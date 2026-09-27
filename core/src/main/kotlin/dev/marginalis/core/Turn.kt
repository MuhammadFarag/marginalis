package dev.marginalis.core

/**
 * Whose move it is across a set of threads — one file's, typically. Only
 * open threads ask anything of anyone; among them, what the user owes
 * outranks what the agent owes, because the user is the one looking at the
 * surface that shows it. Declared once so every surface reads the same
 * rule; how it looks is the adapter's concern.
 */
enum class Turn {
    /** An open thread where the agent spoke last: the user owes a reply. */
    USER,

    /** Every open thread has the user's word last: the agent owes the replies. */
    AGENT;

    companion object {
        /** Null when nothing is open — no one owes anything. */
        fun of(threads: List<CommentThread>): Turn? {
            val open = threads.filter { it.status is ThreadStatus.Open }
            return when {
                open.isEmpty() -> null
                open.any { it.awaitsUser() } -> USER
                else -> AGENT
            }
        }
    }
}
