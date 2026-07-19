package dev.marginalis.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import dev.marginalis.core.CommentThread
import dev.marginalis.core.ThreadStatus
import javax.swing.Icon

/**
 * Collapsed state of a thread: one gutter icon on the anchor line, distinct
 * per status, click to expand the inlay.
 */
class ThreadGutterIconRenderer(
    private val project: Project,
    private val thread: CommentThread,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = when {
        thread.status is ThreadStatus.Resolved -> AllIcons.General.GreenCheckmark
        thread.status is ThreadStatus.Orphaned -> AllIcons.General.Warning
        thread.unreadCount() > 0 -> AllIcons.General.BalloonInformation
        else -> AllIcons.General.Balloon
    }

    override fun getTooltipText(): String {
        val first = thread.messages.firstOrNull() ?: return "Marginalis thread"
        val preview = StringUtil.escapeXmlEntities(
            StringUtil.shortenTextWithEllipsis(MarkdownRenderer.previewText(first.body), 120, 0),
        )
        val status = thread.status.kind.name.lowercase()
        return "<html><b>${first.author.displayName}</b> · $status · ${thread.messages.size} message(s)<br/>" +
            "$preview<br/><i>Click to open thread</i></html>"
    }

    override fun isNavigateAction(): Boolean = true

    override fun getClickAction(): AnAction = object : AnAction("Open Marginalis Thread") {
        override fun actionPerformed(e: AnActionEvent) {
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            ThreadInlayManager.toggle(project, editor, thread)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ThreadGutterIconRenderer &&
            other.thread.id == thread.id &&
            other.thread.status.kind == thread.status.kind &&
            other.thread.unreadCount() == thread.unreadCount()

    override fun hashCode(): Int = thread.id.hashCode()
}
