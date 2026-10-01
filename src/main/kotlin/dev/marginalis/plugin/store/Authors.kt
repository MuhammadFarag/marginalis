package dev.marginalis.plugin.store

import dev.marginalis.core.Author
import dev.marginalis.plugin.settings.MarginalisSettings

object Authors {
    val agent: Author.Agent = Author.Agent.ANONYMOUS

    val user: Author.User
        get() {
            val custom = MarginalisSettings.getInstance().state.displayName.trim()
            val name = custom.ifEmpty {
                System.getProperty("user.name", "User").replaceFirstChar { it.uppercaseChar() }
            }
            return Author.User(name)
        }
}
