package dev.marginalis.core

enum class Turn(val wireName: String) {
    USER_OWES("user"),
    AGENT_OWES("agent");

    companion object {
        /** What the user owes outranks what the agent owes: the user is the one looking at the surface. */
        fun of(threads: List<CommentThread>): Turn? {
            val turns = threads.mapNotNull { it.turn() }
            return when {
                turns.isEmpty() -> null
                USER_OWES in turns -> USER_OWES
                else -> AGENT_OWES
            }
        }

        fun parse(raw: String?): Parsed<Turn?> {
            if (raw == null) return Parsed.Ok(null)
            val turn = entries.firstOrNull { it.wireName == raw.lowercase() }
                ?: return Parsed.Invalid(
                    "invalid awaiting '$raw' — use 'agent' (the user spoke last: the agent owes a reply) or " +
                        "'user' (an agent spoke last: the user owes one); omit to list regardless of whose turn it is.",
                )
            return Parsed.Ok(turn)
        }
    }
}
