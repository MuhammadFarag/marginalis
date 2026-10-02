package dev.marginalis.core

data class ThreadSummary(
    val messages: Int,
    val unread: Int,
    val lastAuthor: Author?,
    val awaiting: Turn?,
) {
    companion object {
        fun of(thread: CommentThread, readerKey: String): ThreadSummary = ThreadSummary(
            messages = thread.messages.size,
            unread = thread.unreadCountFor(readerKey),
            lastAuthor = thread.lastSpoken?.author,
            awaiting = thread.turnFor(readerKey),
        )
    }
}
