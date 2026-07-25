package dev.marginalis.core

/**
 * Who wrote a message. A sealed hierarchy rather than an enum so each side
 * carries its own shape: agents will identify themselves per-post (name and
 * a stable id), users get a display name from configuration.
 */
sealed interface Author {
    val displayName: String

    data class User(override val displayName: String) : Author

    data class Agent(override val displayName: String, val id: String? = null) : Author {
        /** Read-receipt identity: the stable id when the agent has one, else its name. */
        val receiptKey: String get() = id ?: displayName

        companion object {
            /**
             * What an agent is called when it doesn't introduce itself —
             * also the receipt key that legacy single-agent files map to.
             * One constant, because a renamed anonymous would silently
             * mismatch old receipts.
             */
            const val ANONYMOUS_NAME = "Agent"
            val ANONYMOUS = Agent(ANONYMOUS_NAME)
        }
    }
}
