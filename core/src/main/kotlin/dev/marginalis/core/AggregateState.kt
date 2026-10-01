package dev.marginalis.core

enum class AggregateState {
    /** Also the empty set. */
    RESOLVED,

    /** Anchor integrity outranks content weight: a broken anchor needs attention before triage means anything. */
    ORPHANED,

    /** An open blocker outranks the unread signal; nits deliberately change nothing. */
    OPEN_BLOCKER,

    UNREAD,

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
