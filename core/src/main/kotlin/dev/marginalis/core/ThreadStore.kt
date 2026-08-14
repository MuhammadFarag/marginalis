package dev.marginalis.core

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** In-memory thread registry with change notification. Pure; persistence is a caller concern. */
class ThreadStore {
    private val threads = ConcurrentHashMap<String, CommentThread>()
    private val listeners = CopyOnWriteArrayList<(CommentThread) -> Unit>()

    fun add(thread: CommentThread) {
        threads[thread.id] = thread
        notifyChanged(thread)
    }

    /** Rehydration only: no listener storm while loading persisted threads. */
    fun addSilently(thread: CommentThread) {
        threads[thread.id] = thread
    }

    fun byId(id: String): CommentThread? = threads[id]

    fun all(): List<CommentThread> = threads.values.sortedBy { it.createdAt }

    fun query(
        file: String? = null,
        status: ThreadStatus.Kind? = null,
        /** Non-null: only threads with messages this agent hasn't seen. */
        unreadFor: String? = null,
        /**
         * Non-null: only threads that changed strictly after this instant —
         * the sweep cursor. Strict, so handing back the newest [
         * CommentThread.updatedAt] you saw returns what happened since, not
         * what you already have.
         */
        updatedAfter: Instant? = null,
    ): List<CommentThread> = all().filter { thread ->
        (file == null || thread.file == file) &&
            (status == null || thread.status.kind == status) &&
            (unreadFor == null || thread.unreadCountFor(unreadFor) > 0) &&
            (updatedAfter == null || thread.updatedAt > updatedAfter)
    }

    /** Remove one thread entirely. Any live UI attachments are the caller's to clean up. */
    fun remove(id: String): CommentThread? {
        val removed = threads.remove(id)
        removed?.let { notifyChanged(it) }
        return removed
    }

    /** Remove everything, including resolved threads. */
    fun clear(): List<CommentThread> {
        val removed = all()
        threads.clear()
        removed.forEach { notifyChanged(it) }
        return removed
    }

    /**
     * Change hook, fired on any mutation. Listeners run on whatever thread
     * mutated — UI listeners must marshal to their toolkit thread themselves.
     */
    fun addListener(listener: (CommentThread) -> Unit) {
        listeners.add(listener)
    }

    fun notifyChanged(thread: CommentThread) {
        for (listener in listeners) listener(thread)
    }
}
