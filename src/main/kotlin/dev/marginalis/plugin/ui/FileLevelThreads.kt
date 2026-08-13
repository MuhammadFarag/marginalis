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

/**
 * The user's two verbs for threads about a whole file, shared by the surfaces
 * that offer them (the editor banner and the tool window's file node) so both
 * do exactly the same thing.
 */
object FileLevelThreads {

    /** The tool window's registered id — the platform's handle on it. */
    private const val TOOL_WINDOW_ID = "Marginalis"

    /**
     * "Comment on file": open the file and unfold an unsent draft above its
     * first line. Nothing is stored until the first message is sent — same
     * bargain as a line draft (see [ThreadInlayManager.openDraft]).
     */
    fun startDraft(project: Project, file: String) {
        val vFile = project.guessProjectDir()?.findFileByRelativePath(file) ?: return
        OpenFileDescriptor(project, vFile, 0, 0).navigate(true)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        draftIn(project, editor, file)
    }

    /**
     * The same draft, in an editor already at hand — the editor context menu
     * has one, so it skips the navigation.
     */
    fun draftIn(project: Project, editor: Editor, file: String, segment: Segment? = null) {
        ThreadInlayManager.openDraft(
            project,
            editor,
            CommentThread(file, line = null, anchorText = null, segment = segment),
        )
    }

    /**
     * "Open": bring the tool window forward with this file's node selected —
     * where the whole conversation about the file already lives, rather than
     * duplicating it in the banner.
     */
    fun showInToolWindow(project: Project, file: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        toolWindow.activate {
            val panel = toolWindow.contentManager.contents.firstOrNull()?.component as? MarginalisToolWindowPanel
            panel?.selectFile(file)
        }
    }
}
