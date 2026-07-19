package dev.marginalis.plugin.store

import dev.marginalis.core.Author
import dev.marginalis.plugin.settings.MarginalisSettings

/**
 * The two local identities. The agent name is a default — posts may carry
 * their own author identity as agents learn to introduce themselves; the
 * user name is the settings-page display name, falling back to the OS
 * account.
 */
object Authors {
    val agent: Author.Agent = Author.Agent("Claude")

    val user: Author.User
        get() {
            val custom = MarginalisSettings.getInstance().state.displayName.trim()
            val name = custom.ifEmpty {
                System.getProperty("user.name", "User").replaceFirstChar { it.uppercaseChar() }
            }
            return Author.User(name)
        }
}
