package dev.marginalis.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.BadgeIconSupplier
import dev.marginalis.core.AggregateState
import dev.marginalis.core.CommentThread
import javax.swing.Icon

/**
 * Collapsed state of a gutter position's threads: a single icon whether it
 * stands for one thread or several (segments made same-line threads
 * ordinary). Solo thread: click toggles its panel. Several: click opens a
 * chooser. Status merges pessimistically — any unread shows unread, any
 * orphan shows the warning.
 *
 * Two positions use it: a line's own threads, and — beside line 1 — a
 * file's file-level threads, which are collapsed conversations too.
 */
class ThreadGutterIconRenderer(
    private val project: Project,
    /** The threads collapsed into this icon, in creation order; never empty. */
    private val threads: List<CommentThread>,
) : GutterIconRenderer() {

    /**
     * The base glyph names the subject — a page for threads about the whole
     * file (these sit beside line 1, where their panel unfolds), the balloon
     * for a line's conversation — so the two kinds are never confused at a
     * glance.
     */
    private val badges: BadgeIconSupplier
        get() = if (threads.all { it.isFileLevel }) FILE_BADGES else LINE_BADGES

    // A badge dot for unread, not a different balloon: the
    // BalloonInformation swap was too subtle to spot and leaned on color
    // alone. The precedence itself is core's AggregateState.
    override fun getIcon(): Icon = when (AggregateState.of(threads)) {
        AggregateState.RESOLVED -> AllIcons.General.GreenCheckmark
        AggregateState.ORPHANED -> AllIcons.General.Warning
        AggregateState.OPEN_BLOCKER -> badges.errorIcon
        AggregateState.UNREAD -> badges.infoIcon
        AggregateState.OPEN -> badges.originalIcon
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
            ThreadChooserPopup.show(project, editor, threads)
        }
    }

    private companion object {
        val LINE_BADGES = BadgeIconSupplier(AllIcons.General.Balloon)
        val FILE_BADGES = BadgeIconSupplier(AllIcons.FileTypes.Any_type)
    }

    override fun equals(other: Any?): Boolean =
        other is ThreadGutterIconRenderer &&
            other.threads.map { it.id } == threads.map { it.id } &&
            other.threads.map { it.status.kind } == threads.map { it.status.kind } &&
            other.threads.map { it.unreadCount() } == threads.map { it.unreadCount() }

    override fun hashCode(): Int = threads.first().id.hashCode()
}
