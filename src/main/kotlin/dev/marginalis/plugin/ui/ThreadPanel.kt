package dev.marginalis.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.marginalis.core.Author
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Message
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.store.Authors
import dev.marginalis.plugin.store.MarginalisStore
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Expanded state of a thread: messages with author attribution, an inline
 * reply field — the user's entire outbound channel, one click and one
 * keystroke away — and a resolve button on the header.
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
    private val cancelEditButton = JButton("Cancel")

    // Markdown-aware composer: the IDE's own Markdown lexer highlights as you
    // type (plain text when the Markdown plugin is absent). Same input, same
    // colors you'll see rendered after submitting.
    private val replyArea = EditorTextField("", project, composerFileType()).apply {
        setOneLineMode(false)
        addSettingsProvider { editor ->
            editor.settings.isUseSoftWraps = true
            editor.contentComponent.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && (e.isMetaDown || e.isControlDown)) {
                        e.consume()
                        sendReply()
                    }
                }
            })
        }
    }
    private var editingMessageId: String? = null

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
        // No file name in the title: the panel renders inside the file it
        // annotates, so naming it would be redundant (operator feedback).
        val title = JBLabel("Marginalis").apply {
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
            if (thread.status is ThreadStatus.Open) thread.resolve(Authors.user) else thread.reopen()
            MarginalisStore.getInstance(project).threads.notifyChanged(thread)
        }
        val closeButton = JButton("Close").apply {
            font = JBUI.Fonts.smallFont()
            addActionListener { onClose() }
        }
        val right = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(buildNavToolbar())
            add(Box.createHorizontalStrut(JBUI.scale(8)))
            add(resolveButton)
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(closeButton)
        }

        header.add(left, BorderLayout.WEST)
        header.add(right, BorderLayout.EAST)
        header.border = JBUI.Borders.emptyBottom(6)
        return header
    }

    /**
     * Stop-by-stop navigation without leaving the panel — during a tour the
     * mouse lives here, not in the tool window. Same walk semantics as the
     * tool window's title buttons (TourNavigator); moving on closes this
     * panel and opens the target's, one stop at a time.
     */
    private fun buildNavToolbar(): JComponent {
        val group = DefaultActionGroup(
            navAction("First Stop", AllIcons.Actions.Play_first) { walk, i ->
                walk.firstOrNull().takeIf { i != 0 }
            },
            navAction("Previous Stop", AllIcons.Actions.PreviousOccurence) { walk, i ->
                if (i > 0) walk[i - 1] else null
            },
            navAction("Next Stop", AllIcons.Actions.NextOccurence) { walk, i ->
                walk.getOrNull(i + 1)
            },
            navAction("Last Stop", AllIcons.Actions.Play_last) { walk, i ->
                walk.lastOrNull().takeIf { i != walk.size - 1 }
            },
        )
        val toolbar = ActionManager.getInstance().createActionToolbar("MarginalisThreadPanel", group, true)
        toolbar.targetComponent = this
        toolbar.component.isOpaque = false
        return toolbar.component
    }

    /** A nav button is enabled exactly when [target] yields a stop to go to. */
    private fun navAction(
        name: String,
        icon: Icon,
        target: (walk: List<CommentThread>, index: Int) -> CommentThread?,
    ): AnAction = object : AnAction(name, null, icon) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            val (walk, i) = TourNavigator.walkFrom(project, thread)
            e.presentation.isEnabled = target(walk, i) != null
        }

        override fun actionPerformed(e: AnActionEvent) {
            val (walk, i) = TourNavigator.walkFrom(project, thread)
            val destination = target(walk, i) ?: return
            onClose()
            TourNavigator.navigateTo(project, destination)
        }
    }

    private fun buildReplyRow(): JComponent {
        val row = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(6)
        }
        sendButton.font = JBUI.Fonts.smallFont()
        sendButton.addActionListener { sendReply() }
        cancelEditButton.font = JBUI.Fonts.smallFont()
        cancelEditButton.addActionListener {
            editingMessageId = null
            replyArea.text = ""
            refresh()
        }
        val buttons = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(sendButton)
            add(cancelEditButton)
        }
        row.add(replyArea, BorderLayout.CENTER)
        row.add(buttons, BorderLayout.EAST)
        return row
    }

    private fun composerFileType(): FileType {
        val markdown = FileTypeManager.getInstance().getFileTypeByExtension("md")
        return if (markdown is UnknownFileType) PlainTextFileType.INSTANCE else markdown
    }

    private fun sendReply() {
        val body = replyArea.text.trim()
        if (body.isEmpty()) return

        val editing = editingMessageId?.let { id -> thread.messages.find { it.id == id } }
        if (editing != null) {
            editingMessageId = null
            // The window may have closed mid-edit: if the agent read the
            // original in the meantime, it is record now — don't rewrite it.
            if (!editing.seenByAgent) {
                editing.body = body
            }
            replyArea.text = ""
            MarginalisStore.getInstance(project).threads.notifyChanged(thread)
            return
        }

        ensureStored() // draft threads materialize on first send
        thread.addMessage(Message(Authors.user, body))
        replyArea.text = ""
        MarginalisStore.getInstance(project).threads.notifyChanged(thread)
    }

    fun focusReply() {
        replyArea.requestFocusInWindow()
    }

    /** Rebuild the message list from the store. Must run on the EDT. */
    fun refresh() {
        val isDraft = MarginalisStore.getInstance(project).threads.byId(thread.id) == null
        statusLabel.text = when {
            isDraft -> "new comment — unsent"
            thread.status is ThreadStatus.Open -> "open"
            thread.status is ThreadStatus.Resolved -> "resolved by ${thread.resolvedBy?.displayName ?: "?"}"
            else -> "orphaned (anchor deleted)"
        }
        resolveButton.text = if (thread.status is ThreadStatus.Open) "Resolve" else "Reopen"
        resolveButton.isVisible = !isDraft // nothing to resolve before the first send

        // The affordance follows state — and "Submit" over "Send": nothing is
        // transmitted anywhere, the message lands in the local store awaiting
        // the agent's next read. While editing, the composer becomes the
        // editor: Save + Cancel.
        sendButton.text = when {
            editingMessageId != null -> "Save"
            thread.messages.isEmpty() -> "Submit"
            else -> "Reply"
        }
        cancelEditButton.isVisible = editingMessageId != null
        replyArea.setPlaceholder(
            if (thread.messages.isEmpty()) "Comment on this line… (⌘⏎ to submit)" else "Reply… (⌘⏎ to submit)",
        )

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
        val authorColor =
            if (message.author is Author.Agent) JBColor(0x9C27B0, 0xCE93D8) // agent: purple
            else JBColor(0x1565C0, 0x90CAF9) // user: blue
        val meta = JBLabel("${message.author.displayName} · ${timeFormat.format(message.createdAt)}").apply {
            font = JBUI.Fonts.smallFont().asBold()
            foreground = authorColor
        }
        val metaRow = JPanel(BorderLayout()).apply { isOpaque = false }
        metaRow.add(meta, BorderLayout.WEST)

        // The read receipt is the edit window: your message is revisable
        // until the agent reads it, immutable record after. Editing happens
        // in the same composer the message was written in.
        if (message.author is Author.User && !message.seenByAgent && editingMessageId == null) {
            val editLink = ActionLink("Edit") {
                editingMessageId = message.id
                replyArea.text = message.body
                refresh()
                focusReply()
            }
            editLink.font = JBUI.Fonts.smallFont()
            metaRow.add(editLink, BorderLayout.EAST)
        } else if (editingMessageId == message.id) {
            metaRow.add(
                JBLabel("editing below ↓").apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                },
                BorderLayout.EAST,
            )
            // The text has MOVED to the composer — don't show it twice.
            panel.add(metaRow, BorderLayout.NORTH)
            return panel
        }
        // Markdown-lite body: paragraphs as wrapped HTML panes, fenced code
        // as native highlighted editor fragments. Measured at a conservative
        // width so heights only overestimate, never clip.
        val body = MarkdownRenderer.render(project, message.body, panelWidth - JBUI.scale(64))
        panel.add(metaRow, BorderLayout.NORTH)
        panel.add(body, BorderLayout.CENTER)
        return panel
    }

}
