package dev.marginalis.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import dev.marginalis.core.AnchorPolicy
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Segment
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.ThreadChooserPopup
import dev.marginalis.plugin.ui.ThreadInlayManager

/**
 * The user's pen: start a margin thread on the caret line — or, with a
 * selection, on that exact span (the human gesture gets precise; agents
 * stay line-based by design). Opens a draft panel; the thread only
 * materializes (gutter icon, store, agent visibility) when the first
 * message is sent. The message is born unseen, so the agent discovers it
 * via comment_list(unread_only=true) at its next turn — outside a live
 * discussion you are leaving a note, not sending a message.
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
        val segment = captureSegment(editor)
        val line = when {
            segment != null -> document.getLineNumber(editor.selectionModel.selectionStart)
            else -> editor.caretModel.logicalPosition.line.coerceIn(0, document.lineCount - 1)
        }

        // Bare ⌃⌥M on a line that already has live threads means "open the
        // conversation here", not "start a duplicate". A selection always
        // drafts — a new span thread next to an old one is the normal way
        // to raise a second point on the same line.
        if (segment == null) {
            val store = MarginalisStore.getInstance(project)
            val existing = store.threads.all().filter { thread ->
                thread.file == relPath &&
                    thread.status !is ThreadStatus.Resolved &&
                    store.markerOf(thread)?.isValid == true &&
                    store.currentLine(thread) == line
            }
            existing.singleOrNull()?.let {
                ThreadInlayManager.open(project, editor, it)
                return
            }
            if (existing.size > 1) {
                ThreadChooserPopup.show(project, editor, existing)
                return
            }
        }

        val anchorText = document.getText(
            TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)),
        )
        ThreadInlayManager.openDraft(project, editor, CommentThread(relPath, line, anchorText, segment = segment))
    }

    /**
     * The selection as a quote selector — captured live, never guessed.
     * Segments stay line-scoped (the context that re-finds them is the
     * line), so a multi-line selection clamps to its first line: the
     * gesture still earns a quoted span instead of silently degrading to
     * a whole-line thread (operator finding). Empty selections — or ones
     * whose first-line portion is blank — stay whole-line threads.
     */
    private fun captureSegment(editor: Editor): Segment? {
        val selection = editor.selectionModel
        if (!selection.hasSelection()) return null
        val document = editor.document
        val start = selection.selectionStart
        val line = document.getLineNumber(start)
        val end = minOf(selection.selectionEnd, document.getLineEndOffset(line))
        val exact = document.getText(TextRange(start, end))
        if (exact.isBlank()) return null
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val prefixFrom = maxOf(lineStart, start - AnchorPolicy.SEGMENT_CONTEXT)
        val suffixTo = minOf(lineEnd, end + AnchorPolicy.SEGMENT_CONTEXT)
        return Segment(
            exact = exact,
            prefix = document.getText(TextRange(prefixFrom, start)),
            suffix = document.getText(TextRange(end, suffixTo)),
        )
    }
}
