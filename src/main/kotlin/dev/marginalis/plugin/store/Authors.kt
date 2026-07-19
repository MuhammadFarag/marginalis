package dev.marginalis.plugin.store

import dev.marginalis.core.Author

/**
 * The two local identities. The agent name is a default — posts may carry
 * their own author identity as agents learn to introduce themselves; the
 * user name derives from the OS account until a settings page exists.
 */
object Authors {
    val agent: Author.Agent = Author.Agent("Claude")

    val user: Author.User by lazy {
        val name = System.getProperty("user.name", "User")
            .replaceFirstChar { it.uppercaseChar() }
        Author.User(name)
    }
}
