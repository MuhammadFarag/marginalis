package dev.marginalis.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * App-level settings: these govern the user, not one project — navigation
 * consent follows the person, and so does their name.
 *
 * `navigationEnabled` is the hard off-switch behind the agent-side etiquette
 * rule (navigate only on explicit user request). Etiquette governs the
 * agent; the setting governs trust — when off, the transport answers 403 so
 * the agent can tell the user why nothing happened.
 */
@Service
@State(name = "MarginalisSettings", storages = [Storage("marginalis.xml")])
class MarginalisSettings : PersistentStateComponent<MarginalisSettings.State> {

    class State {
        var navigationEnabled: Boolean = true

        /** Blank means "derive from the OS username" (see Authors.user). */
        var displayName: String = ""

        /** Message timestamps: "AUTO" (locale decides), "12", or "24". */
        var timeFormat: String = "AUTO"
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): MarginalisSettings =
            ApplicationManager.getApplication().getService(MarginalisSettings::class.java)
    }
}
