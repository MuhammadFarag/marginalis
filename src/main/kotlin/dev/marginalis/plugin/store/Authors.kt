package dev.marginalis.plugin.store

import dev.marginalis.core.Author
import dev.marginalis.plugin.settings.MarginalisSettings

/**
 * The two local identities. The agent side is deliberately anonymous —
 * "Agent" is what an agent is called when it doesn't introduce itself;
 * self-identified posts carry their own Author.Agent (see the transport's
 * author_name/author_id params). The user name is the settings-page
 * display name, falling back to the OS account.
 */
object Authors {
    val agent: Author.Agent = Author.Agent("Agent")

    val user: Author.User
        get() {
            val custom = MarginalisSettings.getInstance().state.displayName.trim()
            val name = custom.ifEmpty {
                System.getProperty("user.name", "User").replaceFirstChar { it.uppercaseChar() }
            }
            return Author.User(name)
        }
}
