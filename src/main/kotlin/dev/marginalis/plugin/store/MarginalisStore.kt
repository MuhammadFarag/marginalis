package dev.marginalis.plugin.store

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** In-memory thread registry. No persistence in M1 (that's M2). */
@Service(Service.Level.PROJECT)
class MarginalisStore {
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

    /** Remove one thread entirely; marker cleanup happens in the store listener. */
    fun remove(id: String): CommentThread? {
        val removed = threads.remove(id)
        removed?.let { notifyChanged(it) }
        return removed
    }

    /** Remove everything (Clear All); marker cleanup happens in the store listener. */
    fun clear(): List<CommentThread> {
        val removed = all()
        threads.clear()
        removed.forEach { notifyChanged(it) }
        return removed
    }

    fun byId(id: String): CommentThread? = threads[id]

    fun all(): List<CommentThread> = threads.values.sortedBy { it.createdAt }

    fun query(file: String? = null, status: ThreadStatus? = null, unreadOnly: Boolean = false): List<CommentThread> =
        all().filter { thread ->
            (file == null || thread.file == file) &&
                (status == null || thread.status == status) &&
                (!unreadOnly || thread.unreadCount() > 0)
        }

    /**
     * UI refresh hook: fired on any thread mutation (new thread, new message,
     * resolve/reopen). Listeners are called on whatever thread mutated — UI
     * listeners must hop to the EDT themselves.
     */
    fun addListener(listener: (CommentThread) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (CommentThread) -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged(thread: CommentThread) {
        for (listener in listeners) listener(thread)
    }

    companion object {
        fun getInstance(project: Project): MarginalisStore = project.service()
    }
}
