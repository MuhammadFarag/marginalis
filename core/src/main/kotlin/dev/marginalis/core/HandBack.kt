package dev.marginalis.core

import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class HandBack(
    private val snapshotAwaiting: () -> (Author.Agent) -> Boolean = { { true } },
    private val hasUnseen: (agentKey: String, threadId: String) -> Boolean = { _, _ -> true },
    private val clock: () -> Instant = Instant::now,
) {

    private val lock = Any()
    private val waiters = LinkedHashMap<CompletableFuture<Wake?>, Author.Agent>()
    private val liveSubmits = LinkedHashMap<LiveKey, Instant>()
    private val liveDelivered = HashMap<LiveKey, Instant>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var lastAt: Instant? = null
        private set

    private var lastTargets: ((Author.Agent) -> Boolean)? = null

    private var newestStamp: Instant? = null

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
            if (at != null && lastAt.let { it == null || at > it }) {
                lastAt = at
                newestStamp = newestStamp?.let { maxOf(it, at) } ?: at
            }
        }
    }

    fun record(): Instant {
        val at: Instant
        val woken = synchronized(lock) {
            at = stamp(clock())
            lastAt = at
            val awaitingNow = snapshotAwaiting()
            val owed = waiters.filterValues(awaitingNow).keys.toList()
            lastTargets = awaitingNow.takeIf { owed.isNotEmpty() }
            if (owed.isNotEmpty()) owed.onEach(waiters::remove) else drainWaiters()
        }
        woken.forEach { it.complete(Wake.HandedBack(at)) }
        if (woken.isNotEmpty()) announceChange()
        return at
    }

    fun recordLive(threadId: String, agentKey: String, submittedAt: Instant): Instant {
        val at: Instant
        val woken = synchronized(lock) {
            at = stamp(submittedAt)
            val key = LiveKey(agentKey, threadId)
            liveSubmits.remove(key)
            liveSubmits[key] = at
            val listening = waiters.filterValues { it.receiptKey == agentKey }.keys.toList().onEach(waiters::remove)
            if (listening.isNotEmpty()) markDelivered(agentKey, listOf(threadId))
            listening
        }
        woken.forEach { it.complete(Wake.Live(at, listOf(threadId))) }
        if (woken.isNotEmpty()) announceChange()
        return at
    }

    fun forgetLive(threadId: String) {
        synchronized(lock) {
            liveSubmits.keys.removeAll { it.threadId == threadId }
            liveDelivered.keys.removeAll { it.threadId == threadId }
        }
    }

    fun liveDeliveredAt(agentKey: String, threadId: String): Instant? =
        synchronized(lock) { liveDelivered[LiveKey(agentKey, threadId)] }

    fun await(since: Instant?, timeout: Duration, agent: Author.Agent = Author.Agent.ANONYMOUS): CompletableFuture<Wake?> {
        val arrival = synchronized(lock) { arrive(since, agent) }
        if (arrival.announce) announceChange()
        val waiter = arrival.waiter
        if (!arrival.parked) return waiter
        waiter.whenComplete { _, _ ->
            if (synchronized(lock) { waiters.remove(waiter) } != null) announceChange()
        }
        waiter.completeOnTimeout(null, timeout.toMillis(), TimeUnit.MILLISECONDS)
        return waiter
    }

    private fun arrive(since: Instant?, agent: Author.Agent): Arrival {
        val deliveriesCleared = liveDelivered.keys.removeAll { it.agentKey == agent.receiptKey }
        val pending = since?.let { pendingWake(it, agent) }
            ?: return Arrival(CompletableFuture<Wake?>().also { waiters[it] = agent }, parked = true, announce = true)
        val delivered = when (pending) {
            is Wake.Live -> pending.threadIds
            is Wake.HandedBack -> pending.liveThreadIds
        }
        markDelivered(agent.receiptKey, delivered)
        return Arrival(CompletableFuture.completedFuture(pending), parked = false, announce = deliveriesCleared || delivered.isNotEmpty())
    }

    private fun markDelivered(agentKey: String, threadIds: List<String>) {
        val deliveredAt = clock()
        threadIds.forEach { liveDelivered[LiveKey(agentKey, it)] = deliveredAt }
    }

    private class Arrival(val waiter: CompletableFuture<Wake?>, val parked: Boolean, val announce: Boolean)

    fun releaseAll() {
        val released = synchronized(lock) { drainWaiters() }
        released.forEach { it.complete(null) }
        if (released.isNotEmpty()) announceChange()
    }

    private fun pendingWake(since: Instant, agent: Author.Agent): Wake? {
        val live = liveSubmits.filter { (key, at) ->
            key.agentKey == agent.receiptKey && at > since && hasUnseen(key.agentKey, key.threadId)
        }
        val newestLive = live.values.maxOrNull()
        val handedBack = lastAt?.takeIf { it > since && lastTargets?.invoke(agent) != false }
        return when {
            handedBack != null -> Wake.HandedBack(maxOf(handedBack, newestLive ?: handedBack), live.keys.map { it.threadId })
            newestLive != null -> Wake.Live(newestLive, live.keys.map { it.threadId })
            else -> null
        }
    }

    private fun stamp(candidate: Instant): Instant =
        (newestStamp?.takeIf { candidate <= it }?.plusNanos(1) ?: candidate).also { newestStamp = it }

    private data class LiveKey(val agentKey: String, val threadId: String)

    private fun drainWaiters(): List<CompletableFuture<Wake?>> = waiters.keys.toList().also { waiters.clear() }

    private fun announceChange() = listeners.forEach { it() }

    companion object {
        fun over(threads: ThreadStore, clock: () -> Instant = Instant::now): HandBack = HandBack(
            snapshotAwaiting = threads::awaitingSnapshot,
            hasUnseen = { agentKey, threadId -> LiveThread.hasUnseen(threads.byId(threadId), agentKey) },
            clock = clock,
        )
    }
}
