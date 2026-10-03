package dev.marginalis.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Messages
import dev.marginalis.core.People
import dev.marginalis.core.PictureFit
import dev.marginalis.plugin.avatars.scaledTo
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.JComponent
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

object Pictures {
    private val LOG = logger<Pictures>()
    private val DIRECTORY: Path = PathManager.getConfigDir().resolve("marginalis/avatars").normalize()
    private val STAGING: Path = DIRECTORY.resolveSibling("avatars.staging")
    private const val STORED_SIDE = 128
    private val arriving: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun stored(key: String): Path? =
        DIRECTORY.resolve(key).normalize().takeIf { it.startsWith(DIRECTORY) && it != DIRECTORY && it.isRegularFile() }

    fun choose(parent: JComponent, chosen: (String) -> Unit) {
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withTitle("Choose Picture")
            .withExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "bmp")
        val file = FileChooser.chooseFile(descriptor, parent, null, null) ?: return
        val source = file.fileSystem.getNioPath(file) ?: return
        val key = "${UUID.randomUUID()}.png"
        arriving += key
        val application = ApplicationManager.getApplication()
        application.executeOnPooledThread {
            var copied = false
            try {
                copied = copyFitted(source, DIRECTORY.resolve(key))
            } catch (e: Exception) {
                LOG.info("Could not copy picture $source: $e")
            } finally {
                // Settings is a modal dialog: without its modality, this waits until the dialog closes.
                application.invokeLater(
                    {
                        arriving -= key
                        if (copied) {
                            chosen(key)
                        } else {
                            Messages.showErrorDialog(parent, "${file.name} could not be read as a picture.", "Choose Picture")
                        }
                    },
                    ModalityState.stateForComponent(parent),
                )
            }
        }
    }

    fun deleteUnreferenced(rows: List<People.Row>, yours: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (!Files.isDirectory(DIRECTORY)) return@executeOnPooledThread
                val stored = DIRECTORY.listDirectoryEntries().filter { it.isRegularFile() }.map { it.name }
                People.unreferencedPictures(stored, rows, yours)
                    .filterNot { it in arriving }
                    .forEach { Files.deleteIfExists(DIRECTORY.resolve(it)) }
            } catch (e: IOException) {
                LOG.info("Could not clean up unused pictures: $e")
            }
        }
    }

    private fun copyFitted(source: Path, target: Path): Boolean {
        val image = decodedSubsampled(source) ?: return false
        val fit = PictureFit.of(image.width, image.height, STORED_SIDE)
        val square = with(fit.crop) { image.getSubimage(x, y, side, side) }
        Files.createDirectories(DIRECTORY)
        Files.createDirectories(STAGING)
        val partial = Files.createTempFile(STAGING, target.name, ".part")
        try {
            if (!ImageIO.write(square.scaledTo(fit.side), "png", partial.toFile())) return false
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(partial)
        }
        return true
    }

    private fun decodedSubsampled(source: Path): BufferedImage? =
        ImageIO.createImageInputStream(source.toFile())?.use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
            try {
                reader.input = input
                val step = PictureFit.subsampling(reader.getWidth(0), reader.getHeight(0), STORED_SIDE)
                val param = reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
                reader.read(0, param)
            } finally {
                reader.dispose()
            }
        }
}
