package dev.marginalis.plugin.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.wm.ToolWindowManager
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Segment
import dev.marginalis.plugin.ui.toolwindow.MarginalisToolWindowPanel

object FileLevelThreads {

    private const val TOOL_WINDOW_ID = "Marginalis"

    fun startDraft(project: Project, file: String) {
        val vFile = project.guessProjectDir()?.findFileByRelativePath(file) ?: return
        OpenFileDescriptor(project, vFile, 0, 0).navigate(true)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        draftIn(project, editor, file)
    }

    fun draftIn(project: Project, editor: Editor, file: String, segment: Segment? = null) {
        ThreadInlayManager.openDraft(
            project,
            editor,
            CommentThread(file, line = null, anchorText = null, segment = segment),
        )
    }

    fun showInToolWindow(project: Project, file: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        toolWindow.activate {
            val panel = toolWindow.contentManager.contents.firstOrNull()?.component as? MarginalisToolWindowPanel
            panel?.selectFile(file)
        }
    }
}
