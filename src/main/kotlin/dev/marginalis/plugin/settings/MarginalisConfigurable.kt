package dev.marginalis.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import dev.marginalis.core.Intent
import dev.marginalis.core.Mark
import dev.marginalis.core.MarkSubject
import dev.marginalis.core.People
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.MarginalisIcons
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel

class MarginalisConfigurable : Configurable {

    private var panel: com.intellij.openapi.ui.DialogPanel? = null

    private val nicknames = DefaultTableModel(arrayOf<Any>("GitHub login", "Nickname"), 0)

    private val nicknameTable = JBTable(nicknames).apply {
        emptyText.text = "No nicknames: relayed comments show the name GitHub gives"
        setShowGrid(false)
        putClientProperty("terminateEditOnFocusLost", true)
    }

    override fun getDisplayName(): String = "Marginalis"

    override fun createComponent(): JComponent {
        val settings = MarginalisSettings.getInstance()
        val state = settings.state
        val created = panel {
            row {
                checkBox("Allow agent navigation")
                    .comment(
                        "Lets the agent open a file and move the caret when you ask it to " +
                            "(\"show me where that is\"). When off, navigation requests are refused.",
                    )
                    .bindSelected(state::navigationEnabled)
            }
            row("Display name:") {
                textField()
                    .comment("Shown as the author of your comments. Leave blank to use your OS username.")
                    .columns(24)
                    .bindText(state::displayName)
            }
            row("GitHub login:") {
                textField()
                    .comment(
                        "Your own comments relayed from GitHub show as yours, marked \"on GitHub\". " +
                            "Leave blank to show them like anyone else's.",
                    )
                    .columns(24)
                    .bindText(state::githubLogin)
            }
            group("GitHub People") {
                row {
                    cell(
                        ToolbarDecorator.createDecorator(nicknameTable)
                            .setAddAction { addNickname() }
                            .setRemoveAction { removeSelectedNicknames() }
                            .createPanel(),
                    )
                        .align(Align.FILL)
                        .comment("Comments relayed from GitHub show the nickname you give a login instead of its GitHub name.")
                }
            }
            row {
                checkBox("Jump to the next step after resolving")
                    .comment("While walking a guided walkthrough, resolving a step opens the next one.")
                    .bindSelected(state::walkthroughAutoAdvance)
            }
            row {
                checkBox("Notify when the agent posts elsewhere")
                    .comment(
                        "A balloon when an agent message lands in a file you don't have open in front " +
                            "of you. Turn-taking, not presence: one notification per message, nothing pulses.",
                    )
                    .bindSelected(state::notifyOnAgentReply)
            }
            row("Time format:") {
                comboBox(TimeFormat.entries, SimpleListCellRenderer.create("") { it.label })
                    .comment("Message timestamps in thread panels.")
                    .bindItem(
                        { settings.timeFormat },
                        { settings.timeFormat = it ?: TimeFormat.AUTO },
                    )
            }
            group("Intent Glyphs") {
                for ((intent, meaning) in INTENT_LEGEND) {
                    row {
                        cell(JBLabel(meaning, MarginalisIcons.mark(Mark(MarkSubject.LINE, intent)), SwingConstants.LEADING))
                    }
                }
            }
        }
        created.border = JBUI.Borders.empty(8)
        panel = created
        return created
    }

    override fun isModified(): Boolean =
        nicknameTable.isEditing || (panel?.isModified() ?: false) || editedNicknames() != storedNicknames()

    override fun apply() {
        if (nicknameTable.isEditing) nicknameTable.cellEditor.stopCellEditing()
        People.duplicateLogin(editedRows().map { (login, _) -> login })?.let {
            throw ConfigurationException("GitHub login '$it' is listed twice")
        }
        val state = MarginalisSettings.getInstance().state
        val loginBefore = state.githubLogin
        panel?.apply()
        val edited = editedNicknames()
        val nicknamesChanged = edited != storedNicknames()
        if (nicknamesChanged) state.githubNicknames = LinkedHashMap(edited)
        if (nicknamesChanged || state.githubLogin != loginBefore) refreshRelayedThreads()
    }

    override fun reset() {
        panel?.reset()
        if (nicknameTable.isEditing) nicknameTable.cellEditor.cancelCellEditing()
        nicknames.rowCount = 0
        storedNicknames().forEach { (login, nickname) -> nicknames.addRow(arrayOf<Any>(login, nickname)) }
    }

    private fun storedNicknames(): Map<String, String> = MarginalisSettings.getInstance().state.githubNicknames

    private fun editedNicknames(): Map<String, String> = editedRows().toMap()

    private fun editedRows(): List<Pair<String, String>> = (0 until nicknames.rowCount).mapNotNull { row ->
        val login = (nicknames.getValueAt(row, 0) as? String).orEmpty().trim().removePrefix("@")
        val nickname = (nicknames.getValueAt(row, 1) as? String).orEmpty().trim()
        (login to nickname).takeIf { login.isNotEmpty() && nickname.isNotEmpty() }
    }

    private fun addNickname() {
        nicknames.addRow(arrayOf<Any>("", ""))
        val row = nicknames.rowCount - 1
        nicknameTable.selectionModel.setSelectionInterval(row, row)
        nicknameTable.editCellAt(row, 0)
    }

    private fun removeSelectedNicknames() {
        if (nicknameTable.isEditing) nicknameTable.cellEditor.cancelCellEditing()
        nicknameTable.selectedRows.sortedDescending().forEach(nicknames::removeRow)
    }

    private fun refreshRelayedThreads() {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val threads = MarginalisStore.getInstance(project).threads
            threads.all().filter { thread -> thread.messages.any { it.relayed != null } }.forEach(threads::notifyChanged)
        }
    }

    override fun disposeUIResources() {
        panel = null
    }

    private companion object {
        val INTENT_LEGEND = listOf(
            Intent.FINDING to "Finding: something to fix",
            Intent.GUIDANCE to "Guidance: how to write the code around here",
            Intent.QUESTION to "Question: an answer is wanted",
            Intent.FYI to "FYI: praise, context, a heads-up; nothing is owed once you've read it",
            null to "Ordinary: a comment with no stated intent",
        )
    }
}
