package dev.marginalis.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import dev.marginalis.plugin.store.CommentThread
import dev.marginalis.plugin.ui.ThreadInlayManager

/**
 * The human's pen: start a margin thread on the caret line. Opens a draft
 * panel; the thread only materializes (gutter icon, store, agent visibility)
 * when the first message is sent. The message is born unseen, so the agent
 * discovers it via comment_list(unread_only=true) at its next turn — the
 * "leaving a note" half of handover §3.1.
 */
class AddCommentAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project
        e.presentation.isEnabledAndVisible = editor != null && project != null &&
            FileDocumentManager.getInstance().getFile(editor.document) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val vFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val base = project.guessProjectDir() ?: return
        val relPath = VfsUtilCore.getRelativePath(vFile, base) ?: return

        val document = editor.document
        val line = editor.caretModel.logicalPosition.line.coerceIn(0, document.lineCount - 1)
        val anchorText = document.getText(
            TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)),
        )
        ThreadInlayManager.openDraft(project, editor, CommentThread(relPath, line, anchorText))
    }
}
