package dev.marginalis.core

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class Message(
    val author: Author,
    body: String,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant = Instant.now(),
    seenBy: Set<String>? = null,
    val to: Addressee? = null,
) {
    /** A user message may be revised only until an agent reads it. */
    @Volatile
    var body: String = body

    private val _seenBy: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply {
        when {
            seenBy != null -> addAll(seenBy)
            // An agent has read its own words; other agents haven't.
            author is Author.Agent -> add(author.receiptKey)
        }
    }

    /** [Author.Agent.receiptKey]s. Per agent, so one agent's sweep can't consume another's unread. */
    val seenBy: Set<String>
        get() = _seenBy

    fun markSeenBy(agentKey: String) {
        _seenBy.add(agentKey)
    }

    val seenByAnyAgent: Boolean
        get() = _seenBy.isNotEmpty()

    fun seenBy(agentKey: String): Boolean = agentKey in _seenBy

    fun continues(previous: Message?): Boolean =
        previous != null && author is Author.Agent && author == previous.author && to == null && previous.to == null

    val awaits: Turn
        get() = when (to) {
            Addressee.User -> Turn.USER_OWES
            is Addressee.Agent -> Turn.AGENT_OWES
            null -> if (author is Author.Agent) Turn.USER_OWES else Turn.AGENT_OWES
        }
}
