package dev.marginalis.core

import java.time.Instant
import java.util.UUID

/**
 * One margin conversation, held at the width of its subject.
 *
 * The anchor is a ladder, and a thread stands on one rung of it: a line of
 * a file, a whole file, or the project itself. Each rung up drops what the
 * narrower one needed — [line] and [anchorText] go together or not at all,
 * and without a [file] there is no line to have. What remains is always the
 * same conversation.
 *
 * The anchor here is data only: `line` is the last known good position and
 * `anchorText` is the content fingerprint used to re-find it when line
 * numbers go stale (they always do — the code moves underneath). Keeping a
 * *live* anchor attached to an editing surface is an adapter concern.
 *
 * Above the line there is nothing to re-find and nothing that can drift: a
 * file-level thread ("this module needs a README") outlives any rewrite of
 * its file, and a project-level one ("we never settled on error handling")
 * is tied to nothing at all. Either may still carry a [segment] — not as an
 * anchor then, but as provenance: the words the user had selected when the
 * thought started.
 */
class CommentThread(
    /** Project-relative path; null = project-level. */
    val file: String?,
    /** 0-based, last known good; null = no line (the file, or the project). */
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
     * The user's selection: a span anchor within the line, or — once the
     * thread widens past the line it started on — the provenance of the
     * thought, the words that sparked a comment about the whole file or the
     * whole project. Null = no selection was made. Human-created only (the
     * selection gesture); agents read segments, never write them.
     */
    val segment: Segment? = null,
    /** What response this thread asks of its reader; null = ordinary comment. */
    val severity: Severity? = null,
) {
    init {
        require((line == null) == (anchorText == null)) {
            "an anchor is a line and its text together; a thread has both or neither"
        }
        require(line == null || file != null) {
            "a line is a place in a file; a thread without a file has no line to hold"
        }
    }

    /** Nothing but the project: no path, no line, nothing that can go stale. */
    val isProjectLevel: Boolean
        get() = file == null

    /** The file itself is the subject, not a place in it. */
    val isFileLevel: Boolean
        get() = file != null && line == null

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
        // Guarded on the anchor itself, not on the rung: everything above the
        // line has nothing to move, and moving one would leave a line with no
        // file to be in.
        check(this.line != null) {
            val subject = if (isProjectLevel) "the project" else "its file"
            "this thread is about $subject as a whole and has no anchor to move; it comes back with what it is about"
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
