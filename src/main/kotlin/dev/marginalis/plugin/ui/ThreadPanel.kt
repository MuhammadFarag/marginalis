package dev.marginalis.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.marginalis.plugin.store.Author
import dev.marginalis.plugin.store.AuthorKind
import dev.marginalis.plugin.store.CommentThread
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.store.Message
import dev.marginalis.plugin.store.ThreadStatus
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Expanded state of a thread (handover §8): messages with author attribution,
 * an inline reply field — the human's entire outbound channel, one click and
 * one keystroke away — and a resolve button on the header.
 */
class ThreadPanel(
    private val project: Project,
    private val thread: CommentThread,
    private val panelWidth: Int,
    private val ensureStored: () -> Unit,
    private val onClose: () -> Unit,
) : JPanel(BorderLayout()) {

    private val messagesBox = Box.createVerticalBox()
    private val statusLabel = JBLabel()
    private val resolveButton = JButton()
    private val sendButton = JButton()
    private val replyArea = JBTextArea(3, 40)

    init {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(8),
        )
        background = UIUtil.getPanelBackground()

        add(buildHeader(), BorderLayout.NORTH)
        add(messagesBox, BorderLayout.CENTER)
        add(buildReplyRow(), BorderLayout.SOUTH)
        refresh()
    }

    // Width adapted to the editor at open time, height computed. Never set
    // preferredSize directly — an explicit value freezes the height at
    // construction time and the inlay squashes to a single line.
    override fun getPreferredSize(): Dimension {
        val computed = super.getPreferredSize()
        return Dimension(panelWidth, computed.height)
    }

    private fun buildHeader(): JComponent {
        val header = JPanel(BorderLayout()).apply { isOpaque = false }
        val title = JBLabel("Marginalis — ${thread.file}").apply {
            font = JBUI.Fonts.smallFont().asBold()
        }
        statusLabel.font = JBUI.Fonts.smallFont()
        statusLabel.foreground = UIUtil.getContextHelpForeground()

        val left = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(title)
            add(Box.createHorizontalStrut(JBUI.scale(8)))
            add(statusLabel)
        }

        resolveButton.font = JBUI.Fonts.smallFont()
        resolveButton.addActionListener {
            if (thread.status == ThreadStatus.OPEN) thread.resolve(Author.HUMAN) else thread.reopen()
            MarginalisStore.getInstance(project).notifyChanged(thread)
        }
        val closeButton = JButton("Close").apply {
            font = JBUI.Fonts.smallFont()
            addActionListener { onClose() }
        }
        val right = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(resolveButton)
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(closeButton)
        }

        header.add(left, BorderLayout.WEST)
        header.add(right, BorderLayout.EAST)
        header.border = JBUI.Borders.emptyBottom(6)
        return header
    }

    private fun buildReplyRow(): JComponent {
        val row = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(6)
        }
        replyArea.lineWrap = true
        replyArea.wrapStyleWord = true
        replyArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && (e.isMetaDown || e.isControlDown)) {
                    e.consume()
                    sendReply()
                }
            }
        })
        sendButton.font = JBUI.Fonts.smallFont()
        sendButton.addActionListener { sendReply() }
        row.add(JBScrollPane(replyArea), BorderLayout.CENTER)
        row.add(sendButton, BorderLayout.EAST)
        return row
    }

    private fun sendReply() {
        val body = replyArea.text.trim()
        if (body.isEmpty()) return
        ensureStored() // draft threads materialize on first send
        thread.addMessage(Message(Author.HUMAN, body))
        replyArea.text = ""
        MarginalisStore.getInstance(project).notifyChanged(thread)
    }

    fun focusReply() {
        replyArea.requestFocusInWindow()
    }

    /** Rebuild the message list from the store. Must run on the EDT. */
    fun refresh() {
        val isDraft = MarginalisStore.getInstance(project).byId(thread.id) == null
        statusLabel.text = when {
            isDraft -> "new comment — unsent"
            thread.status == ThreadStatus.OPEN -> "open"
            thread.status == ThreadStatus.RESOLVED -> "resolved by ${thread.resolvedBy?.displayName ?: "?"}"
            else -> "orphaned (anchor deleted)"
        }
        resolveButton.text = if (thread.status == ThreadStatus.OPEN) "Resolve" else "Reopen"
        resolveButton.isVisible = !isDraft // nothing to resolve before the first send

        // The affordance follows state — and "Submit" over "Send": nothing is
        // transmitted anywhere, the message lands in the local store awaiting
        // the agent's next read.
        sendButton.text = if (thread.messages.isEmpty()) "Submit" else "Reply"
        replyArea.emptyText.text =
            if (thread.messages.isEmpty()) "Comment on this line… (⌘⏎ to submit)" else "Reply… (⌘⏎ to submit)"

        messagesBox.removeAll()
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        for (message in thread.messages) {
            messagesBox.add(messageComponent(message, timeFormat))
            messagesBox.add(Box.createVerticalStrut(JBUI.scale(6)))
        }
        revalidate()
        repaint()
    }

    private fun messageComponent(message: Message, timeFormat: DateTimeFormatter): JComponent {
        val panel = JPanel(BorderLayout()).apply { isOpaque = false }
        val authorColor = when (message.author.kind) {
            AuthorKind.AGENT -> JBColor(0x9C27B0, 0xCE93D8) // agent: purple
            AuthorKind.HUMAN -> JBColor(0x1565C0, 0x90CAF9) // human: blue
        }
        val meta = JBLabel("${message.author.displayName} · ${timeFormat.format(message.createdAt)}").apply {
            font = JBUI.Fonts.smallFont().asBold()
            foreground = authorColor
        }
        // Deliberately NOT an HTML label: css 'width:px' doesn't reliably match
        // layout pixels (font scaling skews it), which clipped text on the
        // right. A wrapping text area wraps at its *actual* width, always.
        val body = JBTextArea(message.body).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            font = JBUI.Fonts.label()
            border = JBUI.Borders.empty()
            // Measure at a slightly conservative width so the computed
            // preferred height can only overestimate, never clip the bottom.
            setSize(panelWidth - JBUI.scale(64), Int.MAX_VALUE)
        }
        panel.add(meta, BorderLayout.NORTH)
        panel.add(body, BorderLayout.CENTER)
        return panel
    }
}
