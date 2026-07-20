package dev.marginalis.core

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One utterance in a thread. Defaults are for newly written messages;
 * explicit id/createdAt/seenBy are supplied when rehydrating from disk.
 */
class Message(
    val author: Author,
    body: String,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant = Instant.now(),
    seenBy: Set<String>? = null,
) {
    /**
     * Revisable only inside the edit window: a user message may change until
     * an agent reads it. The read receipt is the boundary between "still
     * mine" and "conversational record".
     */
    @Volatile
    var body: String = body

    private val _seenBy: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply {
        when {
            seenBy != null -> addAll(seenBy)
            // An agent has read its own words; other agents haven't.
            author is Author.Agent -> add(author.receiptKey)
        }
    }

    /**
     * Per-agent read receipts: the [Author.Agent.receiptKey]s that have
     * listed this message. Agents are only present during their turns, so
     * the user needs "which agents will already know this" — and with
     * several agents sharing a margin, one agent's sweep must not consume
     * another's unread.
     */
    val seenBy: Set<String>
        get() = _seenBy

    fun markSeenBy(agentKey: String) {
        _seenBy.add(agentKey)
    }

    /** Read by at least one agent — the edit-window boundary and the user's "consumed" signal. */
    val seenByAnyAgent: Boolean
        get() = _seenBy.isNotEmpty()

    fun seenBy(agentKey: String): Boolean = agentKey in _seenBy
}
