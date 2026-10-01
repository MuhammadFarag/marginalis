package dev.marginalis.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

// App-level, not per project: navigation consent and display name follow the person.
@Service
@State(name = "MarginalisSettings", storages = [Storage("marginalis.xml")])
class MarginalisSettings : PersistentStateComponent<MarginalisSettings.State> {

    class State {
        var navigationEnabled: Boolean = true

        var displayName: String = ""

        var timeFormat: String = TimeFormat.AUTO.stored

        var walkthroughAutoAdvance: Boolean = true

        var notifyOnAgentReply: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var timeFormat: TimeFormat
        get() = TimeFormat.fromStored(state.timeFormat)
        set(value) {
            state.timeFormat = value.stored
        }

    companion object {
        fun getInstance(): MarginalisSettings =
            ApplicationManager.getApplication().getService(MarginalisSettings::class.java)
    }
}
