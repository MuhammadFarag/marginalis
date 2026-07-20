package dev.marginalis.core

import java.time.Instant
import java.util.UUID

/**
 * One margin conversation, anchored to a line of one file.
 *
 * The anchor here is data only: `line` is the last known good position and
 * `anchorText` is the content fingerprint used to re-find it when line
 * numbers go stale (they always do — the code moves underneath). Keeping a
 * *live* anchor attached to an editing surface is an adapter concern.
 */
class CommentThread(
    /** Project-relative path. */
    val file: String,
    /** 0-based, last known good. */
    var line: Int,
    /** Text of the anchor line at creation; how the thread re-finds its place. */
    val anchorText: String,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant = Instant.now(),
    /** Step position in a guided walkthrough ("look here Nth"); null = not part of one. */
    val order: Int? = null,
    /** Walkthrough label (e.g. "A") so several guided sequences can coexist. */
    val walkthrough: String? = null,
) {
    private val messagesLock = Any()
    private val _messages = mutableListOf<Message>()

    @Volatile
    var status: ThreadStatus = ThreadStatus.Open
        private set

    val messages: List<Message>
        get() = synchronized(messagesLock) { _messages.toList() }

    val resolvedBy: Author?
        get() = (status as? ThreadStatus.Resolved)?.by

    fun addMessage(message: Message) {
        synchronized(messagesLock) { _messages.add(message) }
    }

    fun resolve(by: Author) {
        status = ThreadStatus.Resolved(by)
    }

    fun reopen() {
        status = ThreadStatus.Open
    }

    fun markOrphaned() {
        status = ThreadStatus.Orphaned
    }

    /** Rehydration only: restore persisted status without lifecycle semantics. */
    fun restoreStatus(status: ThreadStatus) {
        this.status = status
    }

    fun unreadCount(): Int = messages.count { !it.seenByAgent }

    /** Whose turn: the agent spoke last, so the conversation awaits the user. */
    fun awaitsUser(): Boolean = messages.lastOrNull()?.author is Author.Agent
}
