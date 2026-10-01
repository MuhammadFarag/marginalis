package dev.marginalis.plugin.ui

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import dev.marginalis.core.CodeLink
import java.nio.file.InvalidPathException

object CodeLinkNavigator {

    fun navigateTo(project: Project, link: CodeLink, onUnresolved: (problem: String) -> Unit) {
        val file = project.guessProjectDir()?.let { findOrRefresh(it, link.path) }
            ?: return onUnresolved("$link names no file in this project — code link paths are relative to the project root.")
        if (file.isDirectory) return onUnresolved("$link names a directory — a code link opens a file.")
        val lines = link.lines ?: return OpenFileDescriptor(project, file).navigate(true)
        val document = FileDocumentManager.getInstance().getDocument(file)
            ?: return onUnresolved("${link.path} has no text the editor can open at a line.")
        val lineCount = textLineCount(document)
        if (lines.last > lineCount) {
            return onUnresolved("$link reaches past the end of ${link.path}, which has $lineCount lines.")
        }
        val firstLine = lines.first - 1
        val lastLine = lines.last - 1
        val editor = FileEditorManager.getInstance(project)
            .openTextEditor(OpenFileDescriptor(project, file, firstLine, 0), true) ?: return
        if (lastLine > firstLine) {
            editor.selectionModel.setSelection(document.getLineStartOffset(firstLine), document.getLineEndOffset(lastLine))
        }
    }

    private fun findOrRefresh(base: VirtualFile, path: String): VirtualFile? {
        base.findFileByRelativePath(path)?.let { return it }
        val root = base.toNioPath().normalize()
        val onDisk = try {
            root.resolve(path).normalize()
        } catch (_: InvalidPathException) {
            return null
        }
        if (!onDisk.startsWith(root)) return null
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(onDisk)
    }

    private fun textLineCount(document: Document): Int =
        document.lineCount - if (document.immutableCharSequence.endsWith('\n')) 1 else 0
}
