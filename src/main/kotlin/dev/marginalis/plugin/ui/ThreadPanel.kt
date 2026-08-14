package dev.marginalis.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBOptionButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.marginalis.core.Author
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Message
import dev.marginalis.core.Severity
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.settings.MarginalisSettings
import dev.marginalis.plugin.store.Authors
import dev.marginalis.plugin.store.MarginalisStore
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

/**
 * Expanded state of a thread: messages with author attribution, an inline
 * reply field — the user's entire outbound channel, one click and one
 * keystroke away — and a resolve button on the header. Esc closes and
 * returns focus to the editor it lives in.
 */
class ThreadPanel(
    private val project: Project,
    /** The editor hosting this panel; null when it floats in a popup of its own. */
    private val editor: Editor?,
    private val thread: CommentThread,
    private val ensureStored: () -> Unit,
    private val onClose: () -> Unit,
) : JPanel(BorderLayout()) {

    private val messagesBox = Box.createVerticalBox()
    private val statusLabel = JBLabel()

    /**
     * Submit, with the wider destinations hanging off it — the
     * Commit/Commit-and-Push shape. The default action always does what the
     * composer says it will; the dropdown offers the rungs above wherever
     * this draft started, and only while nothing has been sent yet (see
     * [refresh]).
     */
    private val submitAction = object : AbstractAction("Submit") {
        override fun actionPerformed(e: ActionEvent?) = sendReply()
    }
    private val commentOnFileAction = object : AbstractAction("Comment on file instead") {
        override fun actionPerformed(e: ActionEvent?) = submitWiderThan(file = thread.file)
    }
    private val commentOnProjectAction = object : AbstractAction("Comment on project instead") {
        override fun actionPerformed(e: ActionEvent?) = submitWiderThan(file = null)
    }
    private val sendButton = JBOptionButton(submitAction, arrayOf(commentOnFileAction, commentOnProjectAction))
    private val replyRow = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.emptyTop(4)
    }
    private lateinit var composerHolder: JComponent
    private lateinit var composerActions: JComponent
    private lateinit var collapsedReply: JComponent
    private var composerExpanded = false
    private val cancelEditLink = ActionLink("Cancel") {
        editingMessageId = null
        replyArea.text = ""
        setComposerExpanded(false)
        refresh()
    }

    // Markdown-aware composer: the IDE's own Markdown lexer highlights as you
    // type (plain text when the Markdown plugin is absent). Same input, same
    // colors you'll see rendered after submitting.
    private val replyArea = EditorTextField("", project, composerFileType()).apply {
        setOneLineMode(false)
        addSettingsProvider { composerEditor ->
            composerEditor.settings.isUseSoftWraps = true
            // Same right-click the rendered messages got: undiscoverable
            // clipboard actions barely exist.
            composerEditor.installPopupHandler(
                ContextMenuPopupHandler.Simple(
                    DefaultActionGroup(
                        ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_CUT),
                        ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_COPY),
                        ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_PASTE),
                        ActionManager.getInstance().getAction(IdeActions.ACTION_SELECT_ALL),
                    ),
                ),
            )
            composerEditor.contentComponent.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && (e.isMetaDown || e.isControlDown)) {
                        e.consume()
                        sendReply()
                    }
                    if (e.keyCode == KeyEvent.VK_ESCAPE) {
                        e.consume()
                        closeAndRefocus()
                    }
                }
            })
        }
    }
    private var editingMessageId: String? = null

    init {
        // The accent rail: the panel's left edge names its kind at a glance —
        // blocker red, nit gray, brand purple otherwise — and visually ties
        // the unfolded conversation to the margin it came from.
        val accent = when (thread.severity) {
            Severity.BLOCKER -> JBColor(Color(0xDB, 0x58, 0x60), Color(0xC7, 0x54, 0x50))
            Severity.NIT -> JBColor(Color(0xB8, 0xB8, 0xB8), Color(0x5E, 0x61, 0x64))
            null -> JBColor(Color(0x9C, 0x27, 0xB0), Color(0xCE, 0x93, 0xD8))
        }
        border = JBUI.Borders.compound(
            JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 1),
                JBUI.Borders.customLine(accent, 0, 3, 0, 0),
            ),
            JBUI.Borders.empty(8),
        )
        background = UIUtil.getPanelBackground()

        add(buildHeader(), BorderLayout.NORTH)
        add(messagesBox, BorderLayout.CENTER)
        add(buildReplyRow(), BorderLayout.SOUTH)
        // Submit must be a REGISTERED shortcut, not a KeyListener: the IDE's
        // key dispatcher routes ⌘⏎ to editor actions (Split Line on several
        // keymaps) before the component ever sees the event. A component-
        // local shortcut outranks the keymap while the composer has focus.
        object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) = sendReply()
        }.registerCustomShortcutSet(
            CustomShortcutSet(
                KeyboardShortcut(KeyStroke.getKeyStroke("meta ENTER"), null),
                KeyboardShortcut(KeyStroke.getKeyStroke("control ENTER"), null),
            ),
            replyArea,
        )
        // Reading mode needs a focus home for Esc and the walk shortcuts.
        isFocusable = true
        // Draft preservation: whatever is typed survives the panel — saved
        // on every keystroke, restored on reopen, cleared on send. Esc is
        // one key; three paragraphs shouldn't be. A restored draft reopens
        // the composer it was typed in.
        MarginalisStore.getInstance(project).drafts[thread.id]?.let {
            replyArea.text = it
            setComposerExpanded(true)
        }
        replyArea.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                if (editingMessageId != null) return // edits restore the original on cancel, not a draft
                val store = MarginalisStore.getInstance(project)
                val text = replyArea.text
                if (text.isBlank()) store.drafts.remove(thread.id) else store.drafts[thread.id] = text
            }
        })
        // Esc anywhere in the panel (buttons, links) closes it; the composer
        // handles its own Esc above because the editor consumes key events.
        registerKeyboardAction(
            { closeAndRefocus() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
        )
        refresh()
    }

    /** Close is a round trip: the margin folds away, the code gets focus back. */
    private fun closeAndRefocus() {
        onClose()
        editor?.contentComponent?.requestFocusInWindow()
    }

    /**
     * Width follows the editor viewport (clamped to stay readable), height
     * computed. Never set preferredSize directly — an explicit value freezes
     * the height at construction time and the inlay squashes to a single
     * line. Live rather than captured: resizing the window used to leave
     * panels frozen at their open-time width.
     */
    override fun getPreferredSize(): Dimension {
        val computed = super.getPreferredSize()
        return Dimension(panelWidth(), computed.height)
    }

    private fun panelWidth(): Int {
        // Floating in its own window there is no viewport to follow, so the
        // panel picks a readable width and the popup takes its size from it.
        val viewport = editor?.scrollingModel?.visibleArea?.width ?: return JBUI.scale(560)
        return (viewport - JBUI.scale(120)).coerceIn(JBUI.scale(360), JBUI.scale(800))
    }

    private fun buildHeader(): JComponent {
        val header = JPanel(BorderLayout()).apply { isOpaque = false }
        // No title at all anymore: the panel IS Marginalis — branding every
        // panel was the file-name-in-the-title mistake again (same operator
        // instinct, second application). The severity pill and status line
        // carry the header.
        statusLabel.font = JBUI.Fonts.smallFont()
        statusLabel.foreground = UIUtil.getContextHelpForeground()

        val left = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            // The panel-side echo of the gutter glyphs (operator feedback:
            // a word in the status line was too easy to miss): what the
            // thread asks for, then how hard it asks — a pill in the same
            // red as the gutter for blockers, quiet gray for nits, and a
            // quieter one still for the intent, which is not a gate.
            thread.intent?.let { intent ->
                add(Chip(intent.name.lowercase(), INTENT_PILL, INTENT_TEXT))
                add(Box.createHorizontalStrut(JBUI.scale(6)))
            }
            thread.severity?.let { severity ->
                add(Chip(severity.name.lowercase(), severityPill(severity), severityText(severity)))
                add(Box.createHorizontalStrut(JBUI.scale(8)))
            }
            add(statusLabel)
        }

        val right = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(buildHeaderToolbar())
        }

        header.add(left, BorderLayout.WEST)
        header.add(right, BorderLayout.EAST)
        header.border = JBUI.Borders.emptyBottom(6)
        return header
    }

    /** Resolve/reopen from the header link — extracted so the link is declarative. */
    private fun toggleResolved() {
        if (thread.status is ThreadStatus.Open) {
            // Auto-advance: capture the next step BEFORE resolving — the
            // walk only contains open threads, so afterwards this thread
            // has no position in it.
            val next = nextStepIfAutoAdvancing()
            thread.resolve(Authors.user)
            MarginalisStore.getInstance(project).threads.notifyChanged(thread)
            if (next != null) {
                onClose()
                WalkthroughNavigator.navigateTo(project, next)
            }
        } else {
            thread.reopen()
            MarginalisStore.getInstance(project).threads.notifyChanged(thread)
        }
    }

    /**
     * The header's one idiom: step navigation, then the thread's lifecycle
     * verbs, all as toolbar icons (links next to an icon toolbar were a
     * mixed metaphor — operator finding). Resolve previews its outcome:
     * the same green checkmark the gutter will show, flipping to the
     * balloon when the click would reopen.
     */
    private fun buildHeaderToolbar(): JComponent {
        val firstStep = navAction("First Step", AllIcons.Actions.Play_first) { walk, i ->
            walk.firstOrNull().takeIf { i != 0 }
        }
        val previousStep = navAction("Previous Step", AllIcons.Actions.PreviousOccurence) { walk, i ->
            if (i > 0) walk[i - 1] else null
        }
        val nextStep = navAction("Next Step", AllIcons.Actions.NextOccurence) { walk, i ->
            walk.getOrNull(i + 1)
        }
        val lastStep = navAction("Last Step", AllIcons.Actions.Play_last) { walk, i ->
            walk.lastOrNull().takeIf { i != walk.size - 1 }
        }
        // The walk must not require the tool window: the platform's
        // occurrence shortcuts (⌘⌥↑/⌘⌥↓ on the default keymap — user
        // remaps follow along) drive prev/next while focus is anywhere in
        // this panel, matching what the skill has promised all along.
        val actionManager = ActionManager.getInstance()
        previousStep.registerCustomShortcutSet(
            actionManager.getAction(IdeActions.ACTION_PREVIOUS_OCCURENCE).shortcutSet, this,
        )
        nextStep.registerCustomShortcutSet(
            actionManager.getAction(IdeActions.ACTION_NEXT_OCCURENCE).shortcutSet, this,
        )
        val group = DefaultActionGroup(
            firstStep,
            previousStep,
            nextStep,
            lastStep,
            Separator.getInstance(),
            resolveAction(),
            deleteAction(),
            closeAction(),
        )
        val toolbar = ActionManager.getInstance().createActionToolbar("MarginalisThreadPanel", group, true)
        toolbar.targetComponent = this
        toolbar.component.isOpaque = false
        return toolbar.component
    }

    private fun isDraft(): Boolean = MarginalisStore.getInstance(project).threads.byId(thread.id) == null

    private fun resolveAction(): AnAction = object : AnAction() {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabledAndVisible = !isDraft() // nothing to resolve before the first send
            if (thread.status is ThreadStatus.Open) {
                e.presentation.text = "Resolve"
                e.presentation.icon = AllIcons.General.GreenCheckmark
            } else {
                e.presentation.text = "Reopen"
                e.presentation.icon = AllIcons.General.Balloon
            }
        }

        override fun actionPerformed(e: AnActionEvent) = toggleResolved()
    }

    /**
     * The single-thread eraser — until now Clear All was the only one.
     * Deletion is not resolution: no outcome, no log entry, the thread
     * never happened. Irreversible, so it always confirms.
     */
    private fun deleteAction(): AnAction = object : AnAction("Delete Thread", null, AllIcons.Actions.GC) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabledAndVisible = !isDraft() // an unsent draft just closes
        }

        override fun actionPerformed(e: AnActionEvent) {
            val answer = Messages.showYesNoDialog(
                project,
                "Delete this thread (${thread.messages.size} message(s))? " +
                    "Unlike resolving, deletion keeps no record. This cannot be undone.",
                "Delete Margin Thread",
                Messages.getWarningIcon(),
            )
            if (answer != Messages.YES) return
            // The store listener does the rest: marker removed, this panel
            // closed, icons regrouped (the deleted-thread branch).
            MarginalisStore.getInstance(project).threads.remove(thread.id)
            // A panel outlives its thread nowhere: in an editor the store
            // listener closes it, in a popup this does.
            closeAndRefocus()
        }
    }

    private fun closeAction(): AnAction = object : AnAction("Close", null, AllIcons.Actions.Close) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) = closeAndRefocus()
    }

    /** A nav button is enabled exactly when [target] yields a step to go to. */
    private fun navAction(
        name: String,
        icon: Icon,
        target: (walk: List<CommentThread>, index: Int) -> CommentThread?,
    ): AnAction = object : AnAction(name, null, icon) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            val (walk, i) = WalkthroughNavigator.walkFrom(project, thread)
            e.presentation.isEnabled = target(walk, i) != null
        }

        override fun actionPerformed(e: AnActionEvent) {
            val (walk, i) = WalkthroughNavigator.walkFrom(project, thread)
            val destination = target(walk, i) ?: return
            onClose()
            WalkthroughNavigator.navigateTo(project, destination)
        }
    }

    /**
     * The composer gets the full panel width — writing is the panel's main
     * verb, and the old right-hand button stack was stealing measure from
     * it. Actions live in a slim row underneath, right-aligned, the layout
     * every commenting UI has taught hands already. Two-line minimum
     * height while writing: a one-line box invites one-line thoughts. But
     * idle, the composer folds to a single prompt row — an empty two-line
     * box plus an action row was reserving real estate the reader never
     * asked for (operator finding) — and the first click unfolds it.
     */
    private fun buildReplyRow(): JComponent {
        sendButton.font = JBUI.Fonts.smallFont()
        cancelEditLink.font = JBUI.Fonts.smallFont()
        val quoteLink = ActionLink("") { quoteIntoReply() }.apply {
            icon = AllIcons.Actions.MenuPaste
            toolTipText = "Quote code: insert the editor selection (or this thread's anchor) as a code block"
        }
        composerHolder = object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                val computed = super.getPreferredSize()
                return Dimension(computed.width, computed.height.coerceAtLeast(JBUI.scale(52)))
            }
        }.apply {
            isOpaque = false
            add(replyArea, BorderLayout.CENTER)
        }
        composerActions = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(2)
            add(quoteLink)
            add(cancelEditLink)
            add(sendButton)
        }
        collapsedReply = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    isOpaque = false
                    add(ActionLink("Reply…") { focusReply() }.apply { font = JBUI.Fonts.smallFont() })
                    add(
                        JBLabel("⌘⏎ submits").apply {
                            font = JBUI.Fonts.smallFont()
                            foreground = UIUtil.getContextHelpForeground()
                            border = JBUI.Borders.emptyLeft(8)
                        },
                    )
                },
                BorderLayout.WEST,
            )
        }
        setComposerExpanded(false)
        return replyRow
    }

    /**
     * Idle ↔ writing. The inlay tracks the panel's preferred size, so the
     * toggle just swaps rows and revalidates.
     */
    private fun setComposerExpanded(expanded: Boolean) {
        composerExpanded = expanded
        replyRow.removeAll()
        if (expanded) {
            replyRow.add(composerHolder, BorderLayout.CENTER)
            replyRow.add(composerActions, BorderLayout.SOUTH)
        } else {
            replyRow.add(collapsedReply, BorderLayout.CENTER)
        }
        replyRow.revalidate()
        replyRow.repaint()
    }

    /**
     * Drop code into the conversation: the current editor selection when one
     * exists, else what this thread anchors to (its span, or its line) — as
     * a fenced block tagged with the file's extension, so it renders
     * natively highlighted like every other fence.
     */
    private fun quoteIntoReply() {
        val quoted = editor?.selectionModel?.selectedText
            ?: thread.segment?.exact
            ?: thread.anchorText?.trim()
        if (quoted.isNullOrBlank()) return
        val lang = thread.file?.substringAfterLast('.', "") ?: ""
        val fence = "```$lang\n$quoted\n```\n"
        replyArea.text = when {
            replyArea.text.isBlank() -> fence
            replyArea.text.endsWith("\n") -> replyArea.text + fence
            else -> replyArea.text + "\n" + fence
        }
        focusReply()
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
            // The window may have closed mid-edit: if an agent read the
            // original in the meantime, it is record now — don't rewrite it.
            if (!editing.seenByAnyAgent) {
                editing.body = body
                // The one change a Message owns; the thread has to be told,
                // or a sweep by cursor would miss the revision.
                thread.touch()
            }
            replyArea.text = ""
            setComposerExpanded(false)
            MarginalisStore.getInstance(project).threads.notifyChanged(thread)
            return
        }

        ensureStored() // draft threads materialize on first send
        thread.addMessage(Message(Authors.user, body))
        replyArea.text = ""
        setComposerExpanded(false)
        MarginalisStore.getInstance(project).drafts.remove(thread.id)
        MarginalisStore.getInstance(project).threads.notifyChanged(thread)
    }

    /**
     * The dropdown's destinations: land this unsent draft a rung wider than
     * it began — on [file] as a whole, or on the project when that is null.
     * The selection that sparked it rides along as provenance either way —
     * the user pointed at those words even if what they had to say outgrew
     * them — and the draft, which was never stored, simply ends.
     */
    private fun submitWiderThan(file: String?) {
        val body = replyArea.text.trim()
        if (body.isEmpty()) return
        val store = MarginalisStore.getInstance(project)
        val wider = CommentThread(file, line = null, anchorText = null, segment = thread.segment)
        wider.addMessage(Message(Authors.user, body))
        replyArea.text = ""
        store.drafts.remove(thread.id)
        onClose()
        store.threads.add(wider)
        WalkthroughNavigator.navigateTo(project, wider)
    }

    fun focusReply() {
        if (!composerExpanded) setComposerExpanded(true)
        replyArea.requestFocusInWindow()
    }

    /**
     * Focus on open: drafts and restored compositions land in the composer;
     * reading mode keeps the composer folded and focuses the panel itself,
     * so Esc and the walk shortcuts work without a click.
     */
    fun focusDefault() {
        if (isDraft() || replyArea.text.isNotBlank()) focusReply() else requestFocusInWindow()
    }

    /**
     * The step to open after resolving this one, or null when the setting
     * forbids it or the walk has nothing further. Every thread advances
     * along its own walk — a walkthrough step through its walkthrough,
     * an ordinary thread through the open threads in tree order — the
     * same walk the header arrows drive (operator finding: resolve used
     * to advance only in guided walkthroughs, stranding review-by-panel).
     */
    private fun nextStepIfAutoAdvancing(): CommentThread? {
        if (!MarginalisSettings.getInstance().state.walkthroughAutoAdvance) return null
        val (walk, i) = WalkthroughNavigator.walkFrom(project, thread)
        return if (i >= 0) walk.getOrNull(i + 1) else null
    }

    /** Timestamp style is the user's call; "auto" lets the locale decide 12h vs 24h. */
    private fun messageTimeFormatter(): DateTimeFormatter =
        when (MarginalisSettings.getInstance().state.timeFormat) {
            "12" -> DateTimeFormatter.ofPattern("h:mm a")
            "24" -> DateTimeFormatter.ofPattern("HH:mm")
            else -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        }.withZone(ZoneId.systemDefault())

    /**
     * " · step 2/5" — where this thread sits in its walk. Ordered steps use
     * their fixed position over the walkthrough's stable total (agreeing
     * with the tool window's (2/5) prefixes even as steps resolve);
     * unordered threads show their live position in the open-section walk.
     */
    private fun walkPosition(): String {
        val order = thread.order
        if (order != null) {
            val total = WalkthroughNavigator.stableTotal(project, thread) ?: return ""
            return " · step $order/$total"
        }
        val (walk, i) = WalkthroughNavigator.walkFrom(project, thread)
        return if (i >= 0 && walk.size > 1) " · step ${i + 1}/${walk.size}" else ""
    }

    /** Rebuild the message list from the store. Must run on the EDT. */
    fun refresh() {
        statusLabel.text = when {
            isDraft() -> "new comment — unsent"
            thread.status is ThreadStatus.Open -> "open${walkPosition()}"
            thread.status is ThreadStatus.Resolved -> "resolved by ${thread.resolvedBy?.displayName ?: "?"}"
            else -> "orphaned (anchor deleted)"
        }
        // The affordance follows state — and "Submit" over "Send": nothing is
        // transmitted anywhere, the message lands in the local store awaiting
        // the agent's next read. While editing, the composer becomes the
        // editor: Save + Cancel.
        submitAction.putValue(
            Action.NAME,
            when {
                editingMessageId != null -> "Save"
                thread.messages.isEmpty() -> "Submit"
                else -> "Reply"
            },
        )
        // Retargeting is offered only where it is still a choice — a thread
        // being started — and only upward: a reply belongs to the thread it
        // is in, and nothing widens past the project.
        sendButton.options = when {
            !isDraft() -> emptyArray()
            thread.isProjectLevel -> emptyArray()
            thread.isFileLevel -> arrayOf(commentOnProjectAction)
            else -> arrayOf(commentOnFileAction, commentOnProjectAction)
        }
        cancelEditLink.isVisible = editingMessageId != null
        replyArea.setPlaceholder(
            when {
                thread.messages.isNotEmpty() -> "Reply… (⌘⏎ to submit)"
                // The composer names its own subject: a file-level panel
                // opens at the top of the file, where "this line" would lie,
                // and a project-level one is nowhere in particular.
                thread.isProjectLevel -> "Comment on this project… (⌘⏎ to submit)"
                thread.isFileLevel -> "Comment on this file… (⌘⏎ to submit)"
                else -> "Comment on this line… (⌘⏎ to submit)"
            },
        )

        messagesBox.removeAll()
        val timeFormat = messageTimeFormatter()
        // Consecutive agent messages group under one meta line — the second
        // "Claude · 14:02" in a row is noise. User messages always keep
        // theirs: the meta row is where Edit and the seen-check live.
        var previousAuthor: Author? = null
        for (message in thread.messages) {
            val grouped = message.author is Author.Agent && message.author == previousAuthor
            if (messagesBox.componentCount > 0) {
                messagesBox.add(Box.createVerticalStrut(JBUI.scale(if (grouped) 2 else 8)))
            }
            messagesBox.add(messageComponent(message, timeFormat, showMeta = !grouped))
            previousAuthor = message.author
        }
        revalidate()
        repaint()
    }

    /**
     * A stable color per agent identity, so concurrent agents are tellable
     * apart at a glance. The anonymous "Agent" keeps the classic purple;
     * introduced agents hash their receipt identity into a small palette
     * (user blue is deliberately absent from it).
     */
    private fun agentColor(agent: Author.Agent): JBColor {
        if (agent.id == null && agent.displayName == Authors.agent.displayName) return AGENT_PALETTE[0]
        return AGENT_PALETTE[Math.floorMod(agent.receiptKey.hashCode(), AGENT_PALETTE.size)]
    }

    private fun messageComponent(message: Message, timeFormat: DateTimeFormatter, showMeta: Boolean): JComponent {
        val authorColor = when (val author = message.author) {
            is Author.Agent -> agentColor(author)
            else -> JBColor(0x1565C0, 0x90CAF9) // user: blue
        }
        // Each message wears a thin rail in its author's color — enough for
        // the eye to separate turns without reading names, without becoming
        // a chat bubble.
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(authorColor, 0, 2, 0, 0),
                JBUI.Borders.emptyLeft(7),
            )
        }
        val metaRow = JPanel(BorderLayout()).apply { isOpaque = false }
        if (showMeta) {
            val meta = JBLabel("${message.author.displayName} · ${timeFormat.format(message.createdAt)}").apply {
                font = JBUI.Fonts.smallFont().asBold()
                foreground = authorColor
            }
            metaRow.add(meta, BorderLayout.WEST)
        } else {
            // A grouped message's own time is suppressed with its meta line;
            // hover recovers it (operator ask — invisible until wanted).
            panel.toolTipText = "${message.author.displayName} · ${timeFormat.format(message.createdAt)}"
        }

        // The read receipt is the edit window: your message is revisable
        // until the agent reads it, immutable record after — and once read,
        // the receipt itself becomes visible: the promise "the agent will
        // see this" is only trustworthy if you can see it kept.
        if (message.author is Author.User && !message.seenByAnyAgent && editingMessageId == null) {
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
        } else if (message.author is Author.User && message.seenByAnyAgent) {
            metaRow.add(
                JBLabel("✓ seen").apply {
                    font = JBUI.Fonts.smallFont()
                    // Green, matching the resolve checkmark family — the
                    // receipt is good news and may as well feel like it
                    // (operator request, verbatim: "for the dopamine hit").
                    foreground = JBColor(Color(0x2E, 0x7D, 0x32), Color(0xA5, 0xD6, 0xA7))
                    toolTipText = seenByNames(message)
                },
                BorderLayout.EAST,
            )
        }
        // Markdown-lite body: paragraphs as wrapped HTML panes, fenced code
        // as native highlighted editor fragments. Measured at a conservative
        // width so heights only overestimate, never clip.
        val body = MarkdownRenderer.render(project, message.body, panelWidth() - JBUI.scale(64))
        panel.add(metaRow, BorderLayout.NORTH)
        panel.add(body, BorderLayout.CENTER)
        return panel
    }

    /** "Seen by Claude" — receipt keys mapped back to display names where the thread knows them. */
    private fun seenByNames(message: Message): String {
        val agents = thread.messages.map { it.author }.filterIsInstance<Author.Agent>().distinct()
        val names = message.seenBy.sorted().map { key -> agents.find { it.receiptKey == key }?.displayName ?: key }
        return "Seen by ${names.joinToString(", ")}"
    }

    /**
     * A pill naming one of the thread's marks — its intent, its severity —
     * colored like its gutter counterpart. Word + color, never color alone,
     * so the two vocabularies stay readable side by side and in every theme.
     */
    private class Chip(text: String, private val pill: JBColor, textColor: JBColor) : JBLabel(text) {

        init {
            font = JBUI.Fonts.miniFont().asBold()
            foreground = textColor
            border = JBUI.Borders.empty(1, 7)
            isOpaque = false
            maximumSize = preferredSize
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = pill
            g2.fillRoundRect(0, 0, width, height, height, height)
            g2.dispose()
            super.paintComponent(g)
        }
    }

    private companion object {
        // Quieter than either severity: an intent says what kind of answer
        // is wanted, never how urgently.
        val INTENT_PILL = JBColor(Color(0xE1, 0xE9, 0xF4), Color(0x36, 0x3E, 0x4B))
        val INTENT_TEXT = JBColor(Color(0x2A, 0x4A, 0x7A), Color(0xB6, 0xC7, 0xE0))

        fun severityPill(severity: Severity): JBColor = when (severity) {
            Severity.BLOCKER -> JBColor(Color(0xDB, 0x58, 0x60), Color(0xC7, 0x54, 0x50))
            Severity.NIT -> JBColor(Color(0xE8, 0xE8, 0xE8), Color(0x4E, 0x51, 0x57))
        }

        fun severityText(severity: Severity): JBColor = when (severity) {
            Severity.BLOCKER -> JBColor(Color.WHITE, Color(0xF5, 0xE3, 0xE3))
            Severity.NIT -> JBColor(Color(0x59, 0x59, 0x59), Color(0xBD, 0xBD, 0xBD))
        }

        val AGENT_PALETTE = arrayOf(
            JBColor(0x9C27B0, 0xCE93D8), // purple — the anonymous "Agent"
            JBColor(0x00796B, 0x80CBC4), // teal
            JBColor(0xE65100, 0xFFB74D), // orange
            JBColor(0xC2185B, 0xF48FB1), // pink
            JBColor(0x2E7D32, 0xA5D6A7), // green
            JBColor(0x5D4037, 0xBCAAA4), // brown
        )
    }

}
