package dev.marginalis.plugin.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleListCellRenderer
import javax.swing.JList
import dev.marginalis.core.CommentThread

/**
 * The multiplicity chooser, shared by the gutter icon and ⌃⌥M: one row per
 * thread on the line — a segment thread shows its quoted span, a line
 * thread its first words — pick one to open.
 */
object ThreadChooserPopup {

    fun show(project: Project, editor: Editor, threads: List<CommentThread>) {
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(threads)
            .setTitle("Threads on This Line")
            .setRenderer(
                object : SimpleListCellRenderer<CommentThread>() {
                    override fun customize(
                        list: JList<out CommentThread>,
                        thread: CommentThread,
                        index: Int,
                        selected: Boolean,
                        hasFocus: Boolean,
                    ) {
                        val who = thread.messages.firstOrNull()?.author?.displayName ?: "?"
                        val what = thread.segment?.exact?.let { "“$it”" }
                            ?: MarkdownRenderer.previewText(thread.messages.firstOrNull()?.body ?: "")
                        text = "$who · " + StringUtil.shortenTextWithEllipsis(what, 60, 0)
                    }
                },
            )
            .setItemChosenCallback { thread -> ThreadInlayManager.open(project, editor, thread) }
            .createPopup()
            .showInBestPositionFor(editor)
    }
}
