package dev.marginalis.plugin.ui.tab

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.marginalis.core.ProjectTabLayout
import dev.marginalis.plugin.ui.MarginalisPalette
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class ConversationCard(discussion: String, private val onToggle: () -> Unit) : JPanel(BorderLayout()) {

    private val title = JBLabel("From GitHub · $discussion", AllIcons.Vcs.Vendors.Github, SwingConstants.LEADING)
    private val counts = JBLabel().apply {
        font = JBUI.Fonts.smallFont()
        foreground = UIUtil.getContextHelpForeground()
    }
    private val members = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(0, 8, 0, 8)
    }

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.compound(
            JBUI.Borders.emptyBottom(8),
            MarginalisPalette.githubDashedBorder(),
        )
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                border = JBUI.Borders.empty(6, 8)
                add(title, BorderLayout.CENTER)
                add(counts, BorderLayout.EAST)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) = onToggle()
                })
            },
            BorderLayout.NORTH,
        )
        add(members, BorderLayout.CENTER)
    }

    fun bind(conversation: ProjectTabLayout.Entry.Conversation, expanded: Boolean, dimmed: Boolean, memberCards: List<ThreadCard>) {
        title.foreground = if (dimmed) UIUtil.getContextHelpForeground() else UIUtil.getLabelForeground()
        val comments = if (conversation.count == 1) "1 comment" else "${conversation.count} comments"
        val fresh = conversation.newCount.takeIf { it > 0 }?.let { " · $it new" }.orEmpty()
        val resolved = if (dimmed) " · resolved just now" else ""
        counts.text = "${if (expanded) "▾" else "▸"} $comments$fresh$resolved"
        members.arrange(if (expanded) memberCards else emptyList())
    }
}
