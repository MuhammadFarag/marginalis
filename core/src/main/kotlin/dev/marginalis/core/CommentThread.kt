package dev.marginalis.core

import java.time.Instant
import java.util.UUID

/**
 * One margin conversation about one file — anchored to a line of it, or to
 * the file as a whole.
 *
 * The anchor here is data only: `line` is the last known good position and
 * `anchorText` is the content fingerprint used to re-find it when line
 * numbers go stale (they always do — the code moves underneath). Keeping a
 * *live* anchor attached to an editing surface is an adapter concern.
 *
 * Both are null together on a file-level thread: its subject is the file
 * itself ("this module needs a README"), so there is no place in the text
 * to point at, nothing to re-find, and nothing that can drift. Such a
 * thread may still carry a [segment] — not as an anchor then, but as
 * provenance: the words the user had selected when the thought started.
 */
class CommentThread(
    /** Project-relative path — the one anchor every thread has. */
    val file: String,
    /** 0-based, last known good; null = file-level. */
    var line: Int?,
    /**
     * Text of the anchor line; how the thread re-finds its place. Set at
     * creation, rewritten only by orphan rescue (comment_reanchor) — a
     * rescued thread that kept its old fingerprint would re-orphan on the
     * next restart. Null exactly when [line] is: an anchor is the pair.
     */
    var anchorText: String?,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant = Instant.now(),
    /** Step position in a guided walkthrough ("look here Nth"); null = not part of one. */
    val order: Int? = null,
    /** Walkthrough label (e.g. "A") so several guided sequences can coexist. */
    val walkthrough: String? = null,
    /**
     * The user's selection: a span anchor within the line, or — on a
     * file-level thread, which has no line to anchor in — the provenance of
     * the thought, the words that sparked a comment that turned out to be
     * about the whole file. Null = no selection was made. Human-created only
     * (the selection gesture); agents read segments, never write them.
     */
    val segment: Segment? = null,
    /** What response this thread asks of its reader; null = ordinary comment. */
    val severity: Severity? = null,
) {
    init {
        require((line == null) == (anchorText == null)) {
            "an anchor is a line and its text together; a thread has both or neither"
        }
    }

    /** No anchor at all: the file itself is the subject, not a place in it. */
    val isFileLevel: Boolean
        get() = line == null

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

    /**
     * Orphan rescue: move to a verified new anchor and reopen, atomically.
     * Only an orphaned thread may move — a live anchor doesn't. The fresh
     * [anchorText] fingerprint is mandatory: a rescue that kept its old one
     * would re-orphan on the next restart.
     */
    fun rescueTo(line: Int, anchorText: String) {
        check(!isFileLevel) {
            "a file-level thread has no anchor to move; it follows its file, and reopens when the path returns"
        }
        check(status is ThreadStatus.Orphaned) {
            "only an orphaned thread can be re-anchored; this one is ${status.kind.name.lowercase()}"
        }
        this.line = line
        this.anchorText = anchorText
        status = ThreadStatus.Open
    }

    /** Rehydration only: restore persisted status without lifecycle semantics. */
    fun restoreStatus(status: ThreadStatus) {
        this.status = status
    }

    /** Messages no agent has consumed yet — the user-facing "will be seen" count. */
    fun unreadCount(): Int = messages.count { !it.seenByAnyAgent }

    /** Messages a specific agent hasn't seen — that agent's sweep is keyed by this. */
    fun unreadCountFor(agentKey: String): Int = messages.count { !it.seenBy(agentKey) }

    /** Whose turn: the agent spoke last, so the conversation awaits the user. */
    fun awaitsUser(): Boolean = messages.lastOrNull()?.author is Author.Agent
}
