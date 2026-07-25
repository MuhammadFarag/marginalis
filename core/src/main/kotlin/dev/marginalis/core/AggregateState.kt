package dev.marginalis.core

/**
 * Merged state of a set of co-located threads (one line's threads, one
 * file's open threads): the pessimistic precedence, declared once so the
 * gutter icon, stripe badge, and any future surface can't drift apart.
 * Each surface maps the value to its own visual vocabulary.
 */
enum class AggregateState {
    /** Everything concluded (also the empty set — nothing to shout about). */
    RESOLVED,

    /** Anchor integrity outranks content weight: a broken anchor needs attention before triage means anything. */
    ORPHANED,

    /** Red implies act, so an open blocker outranks the unread signal; nits deliberately change nothing. */
    OPEN_BLOCKER,

    /** Someone wrote something no agent has consumed yet. */
    UNREAD,

    /** Live conversation, nothing shouting. */
    OPEN;

    companion object {
        fun of(threads: List<CommentThread>): AggregateState = when {
            threads.all { it.status is ThreadStatus.Resolved } -> RESOLVED
            threads.any { it.status is ThreadStatus.Orphaned } -> ORPHANED
            threads.any { it.status is ThreadStatus.Open && it.severity == Severity.BLOCKER } -> OPEN_BLOCKER
            threads.any { it.unreadCount() > 0 } -> UNREAD
            else -> OPEN
        }
    }
}
