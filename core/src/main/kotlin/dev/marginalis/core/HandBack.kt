package dev.marginalis.core

import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class HandBack(
    private val hasAwaiting: (Author.Agent) -> Boolean = { true },
    private val clock: () -> Instant = Instant::now,
) {

    private val lock = Any()
    private val waiters = LinkedHashMap<CompletableFuture<Instant?>, Author.Agent>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var lastAt: Instant? = null
        private set

    private var lastWasTargeted = false

    internal val waiting: Int
        get() = synchronized(lock) { waiters.size }

    val waitingAgents: List<Author.Agent>
        get() = synchronized(lock) { waiters.values.distinct() }

    val waitingNames: List<String>
        get() = waitingAgents.map { it.displayName }.distinct()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun restore(at: Instant?) {
        synchronized(lock) {
            if (at != null && lastAt.let { it == null || at > it }) lastAt = at
        }
    }

    fun record(): Instant {
        val at = clock()
        val woken = synchronized(lock) {
            lastAt = at
            val owed = waiters.filterValues(hasAwaiting).keys.toList()
            lastWasTargeted = owed.isNotEmpty()
            if (lastWasTargeted) owed.onEach(waiters::remove) else drainWaiters()
        }
        woken.forEach { it.complete(at) }
        if (woken.isNotEmpty()) announceChange()
        return at
    }

    fun await(since: Instant?, timeout: Duration, agent: Author.Agent = Author.Agent.ANONYMOUS): CompletableFuture<Instant?> {
        val waiter = synchronized(lock) {
            lastAt?.takeIf { since != null && it > since && (!lastWasTargeted || hasAwaiting(agent)) }
                ?.let { return CompletableFuture.completedFuture(it) }
            CompletableFuture<Instant?>().also { waiters[it] = agent }
        }
        announceChange()
        waiter.whenComplete { _, _ ->
            if (synchronized(lock) { waiters.remove(waiter) } != null) announceChange()
        }
        waiter.completeOnTimeout(null, timeout.toMillis(), TimeUnit.MILLISECONDS)
        return waiter
    }

    fun releaseAll() {
        val released = synchronized(lock) { drainWaiters() }
        released.forEach { it.complete(null) }
        if (released.isNotEmpty()) announceChange()
    }

    private fun drainWaiters(): List<CompletableFuture<Instant?>> = waiters.keys.toList().also { waiters.clear() }

    private fun announceChange() = listeners.forEach { it() }
}
