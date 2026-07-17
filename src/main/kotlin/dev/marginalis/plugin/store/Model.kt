package dev.marginalis.plugin.store

import com.intellij.openapi.editor.markup.RangeHighlighter
import java.time.Instant
import java.util.UUID

/**
 * The M1 data model, handover §4. A struct rather than a boolean for `kind` —
 * a second agent is plausible later.
 */
enum class AuthorKind { HUMAN, AGENT }

data class Author(val kind: AuthorKind, val displayName: String) {
    companion object {
        val AGENT = Author(AuthorKind.AGENT, "Claude")

        /** The local human; single-user tool, so the OS username is authoritative enough. */
        val HUMAN: Author by lazy {
            val name = System.getProperty("user.name", "Human")
                .replaceFirstChar { it.uppercaseChar() }
            Author(AuthorKind.HUMAN, name)
        }
    }
}

class Message(
    val author: Author,
    val body: String,
) {
    val id: String = UUID.randomUUID().toString()
    val createdAt: Instant = Instant.now()

    /**
     * Read receipt (handover §3.1): exists because presence is asymmetric.
     * Agent-authored messages are born seen; human messages become seen when
     * the agent reads them via comment_list.
     */
    @Volatile
    var seenByAgent: Boolean = author.kind == AuthorKind.AGENT
}

enum class ThreadStatus { OPEN, RESOLVED, ORPHANED }

/**
 * One margin conversation, anchored to a line.
 *
 * The RangeHighlighter doubles as the live anchor (its RangeMarker side) and
 * the gutter presence. `line` is the last known good 0-based line, refreshed
 * from the marker whenever it is valid; the marker dying (deleted range) is
 * what orphans a thread (handover §3.3–3.4).
 */
class CommentThread(
    val file: String, // project-relative path
    @Volatile var line: Int, // 0-based, last known good
    val anchorText: String, // text of the anchor line at creation; M2 grows this into a fingerprint
) {
    val id: String = UUID.randomUUID().toString()
    val createdAt: Instant = Instant.now()

    private val messagesLock = Any()
    private val _messages = mutableListOf<Message>()

    @Volatile
    var status: ThreadStatus = ThreadStatus.OPEN
        private set

    @Volatile
    var resolvedBy: Author? = null
        private set

    /** In-memory only; dies with the Document. Persistence is M2. */
    @Volatile
    var highlighter: RangeHighlighter? = null

    val messages: List<Message>
        get() = synchronized(messagesLock) { _messages.toList() }

    fun addMessage(message: Message) {
        synchronized(messagesLock) { _messages.add(message) }
    }

    fun resolve(by: Author) {
        status = ThreadStatus.RESOLVED
        resolvedBy = by
    }

    fun reopen() {
        status = ThreadStatus.OPEN
        resolvedBy = null
    }

    fun markOrphaned() {
        status = ThreadStatus.ORPHANED
    }

    /**
     * Current anchor line (0-based): the live marker when valid, else the last
     * known good. Also self-repairs `line` and orphan status as a side effect,
     * so callers always observe a consistent view.
     */
    fun currentLine(): Int {
        val h = highlighter
        if (h != null) {
            if (h.isValid) {
                val docLine = h.document.getLineNumber(h.startOffset)
                line = docLine
            } else if (status == ThreadStatus.OPEN) {
                markOrphaned()
            }
        }
        return line
    }

    fun unreadCount(): Int = messages.count { !it.seenByAgent }
}
