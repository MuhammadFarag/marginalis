package dev.marginalis.core

sealed interface Author {
    val displayName: String

    data class User(override val displayName: String) : Author

    data class Agent(override val displayName: String, val id: String? = null) : Author {
        val receiptKey: String get() = id ?: displayName

        companion object {
            /** Persisted: legacy single-agent receipts map to this key, so renaming it silently mismatches them. */
            const val ANONYMOUS_NAME = "Agent"
            val ANONYMOUS = Agent(ANONYMOUS_NAME)
        }
    }
}
