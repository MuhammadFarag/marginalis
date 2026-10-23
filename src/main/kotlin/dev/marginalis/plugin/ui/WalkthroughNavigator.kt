package dev.marginalis.plugin.ui

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Message
import dev.marginalis.core.WalkPosition
import dev.marginalis.core.Walkthrough
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.tab.ProjectTab

object WalkthroughNavigator {

    fun walkFrom(project: Project, thread: CommentThread): WalkPosition =
        Walkthrough.walkFrom(MarginalisStore.getInstance(project).threads.all(), thread)

    fun stableTotal(project: Project, thread: CommentThread): Int? =
        Walkthrough.stableTotal(MarginalisStore.getInstance(project).threads.all(), thread)

    fun navigateTo(project: Project, thread: CommentThread, revealing: Message? = null) {
        if (thread.file == null) return ProjectTab.reveal(project, thread, revealing)
        val place = placeOf(project, thread) ?: return
        place.navigate(true)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        ThreadInlayManager.open(project, editor, thread, revealing)
    }

    fun navigateWithin(editor: Editor, project: Project, thread: CommentThread): Boolean {
        if (editor.isDisposed) return false
        val shown = editor.getUserData(ThreadInlayManager.SHOWN_THREADS)
        if (shown != null) {
            val line = shown.lineOf(thread)?.takeIf { shown.file == thread.file } ?: return false
            recordingHistory(project) { editor.moveTo(line) }
        } else {
            val place = placeOf(project, thread)?.takeIf { editor.shows(it.file) } ?: return false
            recordingHistory(project) { place.navigateIn(editor) }
        }
        editor.contentComponent.requestFocusInWindow()
        ThreadInlayManager.open(project, editor, thread)
        return true
    }

    private fun placeOf(project: Project, thread: CommentThread): OpenFileDescriptor? {
        val path = thread.file ?: return null
        val file = project.guessProjectDir()?.findFileByRelativePath(path) ?: return null
        val line = MarginalisStore.getInstance(project).syncLine(thread) ?: 0
        return OpenFileDescriptor(project, file, line, 0)
    }

    private fun Editor.shows(file: VirtualFile): Boolean = FileDocumentManager.getInstance().getFile(document) == file

    private fun Editor.moveTo(line: Int) {
        caretModel.moveToLogicalPosition(LogicalPosition(line, 0))
        scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    private fun recordingHistory(project: Project, move: () -> Unit) {
        CommandProcessor.getInstance().executeCommand(project, {
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
            move()
        }, null, null)
    }
}
