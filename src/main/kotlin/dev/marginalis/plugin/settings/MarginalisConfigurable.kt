package dev.marginalis.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

/** The "Marginalis" page under the IDE settings tree. */
class MarginalisConfigurable : Configurable {

    private var panel: com.intellij.openapi.ui.DialogPanel? = null

    override fun getDisplayName(): String = "Marginalis"

    override fun createComponent(): JComponent {
        val state = MarginalisSettings.getInstance().state
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
            row("Time format:") {
                comboBox(listOf("Auto (system)", "12-hour", "24-hour"))
                    .comment("Message timestamps in thread panels.")
                    .bindItem(
                        {
                            when (state.timeFormat) {
                                "12" -> "12-hour"
                                "24" -> "24-hour"
                                else -> "Auto (system)"
                            }
                        },
                        {
                            state.timeFormat = when (it) {
                                "12-hour" -> "12"
                                "24-hour" -> "24"
                                else -> "AUTO"
                            }
                        },
                    )
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
}
