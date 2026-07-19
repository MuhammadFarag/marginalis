package dev.marginalis.core

import java.time.Instant
import java.util.UUID

/**
 * One utterance in a thread. Defaults are for newly written messages;
 * explicit id/createdAt/seenByAgent are supplied when rehydrating from disk.
 */
class Message(
    val author: Author,
    body: String,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant = Instant.now(),
    seenByAgent: Boolean? = null,
) {
    /**
     * Revisable only inside the edit window: a user message may change until
     * the agent reads it. The read receipt is the boundary between "still
     * mine" and "conversational record".
     */
    @Volatile
    var body: String = body

    /**
     * Read receipt. The agent is only present during its turns, so the user
     * needs to know whether a message has been consumed ("Claude will see
     * this on its next turn" rather than "did anyone see this?"). Agent
     * messages are born seen; user messages become seen when the agent
     * lists them.
     */
    @Volatile
    var seenByAgent: Boolean = seenByAgent ?: (author is Author.Agent)
}
