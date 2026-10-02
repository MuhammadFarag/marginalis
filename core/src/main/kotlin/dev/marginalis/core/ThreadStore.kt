package dev.marginalis.core

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class ThreadStore {
    private val threads = ConcurrentHashMap<String, CommentThread>()
    private val listeners = CopyOnWriteArrayList<(CommentThread) -> Unit>()

    fun add(thread: CommentThread) {
        threads[thread.id] = thread
        notifyChanged(thread)
    }

    /** Rehydration only. */
    fun addSilently(thread: CommentThread) {
        threads[thread.id] = thread
    }

    fun byId(id: String): CommentThread? = threads[id]

    fun all(): List<CommentThread> = threads.values.sortedBy { it.createdAt }

    fun query(
        file: String? = null,
        status: ThreadStatus.Kind? = null,
        intent: Intent? = null,
        awaiting: Turn? = null,
        awaitingFor: String? = null,
        unreadFor: String? = null,
        /** Strictly after: handing back the newest [CommentThread.updatedAt] seen returns only what happened since. */
        updatedAfter: Instant? = null,
    ): List<CommentThread> = all().filter { thread ->
        (file == null || thread.file == file) &&
            (status == null || thread.status.kind == status) &&
            (intent == null || thread.intent == intent) &&
            (awaiting == null || thread.turnFor(awaitingFor) == awaiting) &&
            (unreadFor == null || thread.unreadCountFor(unreadFor) > 0) &&
            (updatedAfter == null || thread.updatedAt > updatedAfter)
    }

    fun hasAwaiting(agent: Author.Agent): Boolean =
        query(awaiting = Turn.AGENT_OWES, awaitingFor = agent.receiptKey).isNotEmpty()

    fun awaitingSnapshot(): (Author.Agent) -> Boolean {
        val addressees = query(awaiting = Turn.AGENT_OWES).map { it.messages.lastOrNull()?.to }
        return { agent -> addressees.any { it == null || it == Addressee.Agent(agent.receiptKey) } }
    }

    fun remove(id: String): CommentThread? {
        val removed = threads.remove(id)
        removed?.let { notifyChanged(it) }
        return removed
    }

    fun clear(): List<CommentThread> {
        val removed = all()
        threads.clear()
        removed.forEach { notifyChanged(it) }
        return removed
    }

    /** Listeners run on whatever thread mutated; UI listeners must marshal to their toolkit thread themselves. */
    fun addListener(listener: (CommentThread) -> Unit) {
        listeners.add(listener)
    }

    private fun removeListener(listener: (CommentThread) -> Unit) {
        listeners.remove(listener)
    }

    fun watch(id: String, onChange: (CommentThread?) -> Unit): AutoCloseable {
        val listener: (CommentThread) -> Unit = { changed -> if (changed.id == id) onChange(byId(id)) }
        addListener(listener)
        return AutoCloseable { removeListener(listener) }
    }

    fun notifyChanged(thread: CommentThread) {
        for (listener in listeners) listener(thread)
    }
}
