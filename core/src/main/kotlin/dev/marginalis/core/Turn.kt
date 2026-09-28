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
            val turns = threads.mapNotNull { it.turn() }
            return when {
                turns.isEmpty() -> null
                USER in turns -> USER
                else -> AGENT
            }
        }

        fun parse(raw: String?): Parsed<Turn?> {
            if (raw == null) return Parsed.Ok(null)
            return when (raw.lowercase()) {
                "agent" -> Parsed.Ok(AGENT)
                "user" -> Parsed.Ok(USER)
                else -> Parsed.Invalid(
                    "invalid awaiting '$raw' — use 'agent' (the user spoke last: the agent owes a reply) or " +
                        "'user' (an agent spoke last: the user owes one); omit to list regardless of whose turn it is.",
                )
            }
        }
    }
}
