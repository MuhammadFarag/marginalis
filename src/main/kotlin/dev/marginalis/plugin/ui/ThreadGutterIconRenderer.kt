package dev.marginalis.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleListCellRenderer
import dev.marginalis.core.CommentThread
import dev.marginalis.core.ThreadStatus
import javax.swing.Icon

/**
 * Collapsed state of one line's threads: a single gutter icon whether the
 * line hosts one thread or several (segments made same-line threads
 * ordinary). Solo thread: click toggles its panel. Several: click opens a
 * chooser. Status merges pessimistically — any unread shows unread, any
 * orphan shows the warning.
 */
class ThreadGutterIconRenderer(
    private val project: Project,
    /** The line's threads in creation order; never empty. */
    private val threads: List<CommentThread>,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = when {
        threads.all { it.status is ThreadStatus.Resolved } -> AllIcons.General.GreenCheckmark
        threads.any { it.status is ThreadStatus.Orphaned } -> AllIcons.General.Warning
        threads.any { it.unreadCount() > 0 } -> AllIcons.General.BalloonInformation
        else -> AllIcons.General.Balloon
    }

    override fun getTooltipText(): String {
        if (threads.size == 1) {
            val thread = threads.single()
            val first = thread.messages.firstOrNull() ?: return "Marginalis thread"
            val status = thread.status.kind.name.lowercase()
            return "<html><b>${first.author.displayName}</b> · $status · ${thread.messages.size} message(s)<br/>" +
                "${preview(thread)}<br/><i>Click to open thread</i></html>"
        }
        val lines = threads.joinToString("<br/>") { thread ->
            "<b>${thread.messages.firstOrNull()?.author?.displayName ?: "?"}</b> · ${preview(thread)}"
        }
        return "<html><b>${threads.size} Marginalis threads</b><br/>$lines<br/><i>Click to choose</i></html>"
    }

    private fun preview(thread: CommentThread): String {
        val first = thread.messages.firstOrNull() ?: return ""
        return StringUtil.escapeXmlEntities(
            StringUtil.shortenTextWithEllipsis(MarkdownRenderer.previewText(first.body), 120, 0),
        )
    }

    override fun isNavigateAction(): Boolean = true

    override fun getClickAction(): AnAction = object : AnAction("Open Marginalis Thread") {
        override fun actionPerformed(e: AnActionEvent) {
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            threads.singleOrNull()?.let {
                ThreadInlayManager.toggle(project, editor, it)
                return
            }
            chooseThread(editor)
        }
    }

    /**
     * The multiplicity chooser: one row per thread — a segment thread shows
     * its quoted span, a line thread its first words — pick one to open.
     */
    private fun chooseThread(editor: Editor) {
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(threads)
            .setTitle("Threads on This Line")
            .setRenderer(
                SimpleListCellRenderer.create("") { thread ->
                    val who = thread.messages.firstOrNull()?.author?.displayName ?: "?"
                    val what = thread.segment?.exact?.let { "“$it”" }
                        ?: MarkdownRenderer.previewText(thread.messages.firstOrNull()?.body ?: "")
                    "$who · " + StringUtil.shortenTextWithEllipsis(what, 60, 0)
                },
            )
            .setItemChosenCallback { thread -> ThreadInlayManager.open(project, editor, thread) }
            .createPopup()
            .showInBestPositionFor(editor)
    }

    override fun equals(other: Any?): Boolean =
        other is ThreadGutterIconRenderer &&
            other.threads.map { it.id } == threads.map { it.id } &&
            other.threads.map { it.status.kind } == threads.map { it.status.kind } &&
            other.threads.map { it.unreadCount() } == threads.map { it.unreadCount() }

    override fun hashCode(): Int = threads.first().id.hashCode()
}
