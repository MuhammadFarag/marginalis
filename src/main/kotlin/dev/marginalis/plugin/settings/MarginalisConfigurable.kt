package dev.marginalis.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import dev.marginalis.core.Intent
import dev.marginalis.core.Mark
import dev.marginalis.core.MarkSubject
import dev.marginalis.plugin.ui.MarginalisIcons
import javax.swing.JComponent
import javax.swing.SwingConstants

class MarginalisConfigurable : Configurable {

    private var panel: com.intellij.openapi.ui.DialogPanel? = null

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

    override fun isModified(): Boolean = panel?.isModified() ?: false

    override fun apply() {
        panel?.apply()
    }

    override fun reset() {
        panel?.reset()
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
