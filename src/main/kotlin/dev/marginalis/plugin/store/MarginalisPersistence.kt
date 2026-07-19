package dev.marginalis.plugin.store

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import dev.marginalis.core.CommentThread
import dev.marginalis.core.ThreadsCodec
import java.nio.file.Files
import java.nio.file.Path

/**
 * File I/O for thread durability: `.idea/marginalis.json`, per project —
 * private notes, kept out of version control by the usual `.idea` rules.
 * The format itself lives in the core codec; only markers are never
 * persisted (they die with the Document and are rebuilt by re-anchoring).
 */
object MarginalisPersistence {
    private val log = logger<MarginalisPersistence>()

    private fun storageFile(project: Project): Path? =
        project.guessProjectDir()?.path?.let { Path.of(it, ".idea", "marginalis.json") }

    @Synchronized
    fun save(project: Project, threads: List<CommentThread>) {
        val path = storageFile(project) ?: return
        try {
            Files.createDirectories(path.parent)
            Files.writeString(path, ThreadsCodec.encode(threads))
        } catch (e: Exception) {
            log.warn("Failed to save margin threads to $path", e)
        }
    }

    fun load(project: Project): List<CommentThread> {
        val path = storageFile(project) ?: return emptyList()
        if (!Files.exists(path)) return emptyList()
        return try {
            ThreadsCodec.decode(Files.readString(path))
        } catch (e: Exception) {
            log.warn("Failed to load margin threads from $path — starting empty", e)
            emptyList()
        }
    }
}
