package dev.marginalis.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import dev.marginalis.plugin.ui.FileLevelThreads

/**
 * The always-available way in: a thread about the file you are looking at,
 * whatever the caret happens to be on. It has to live here, in the editor
 * where the reading happens — the tool window's file node and the gutter
 * glyph both need a thread to exist before they can offer anything, which
 * left a file with no threads at all with no way to start one (#15).
 *
 * A selection comes along as provenance: the words that sparked the thought
 * are worth keeping even when the comment turns out to be about the file.
 */
class CommentOnFileAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null && e.project != null &&
            FileDocumentManager.getInstance().getFile(editor.document) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val vFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val base = project.guessProjectDir() ?: return
        val relPath = VfsUtilCore.getRelativePath(vFile, base) ?: return
        FileLevelThreads.draftIn(project, editor, relPath, AddCommentAction.captureSegment(editor))
    }
}
