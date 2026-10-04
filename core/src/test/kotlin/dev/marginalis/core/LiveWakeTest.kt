package dev.marginalis.core

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveWakeTest {

    private val t0 = Instant.parse("2026-10-01T10:00:00Z")
    private var now = t0
    private val threads = ThreadStore()
    private val handBack = HandBack.over(threads) { now }
    private val hour = Duration.ofHours(1)
    private val user = Author.User("Muhammad")
    private val claude = Author.Agent("Claude", "claude-main")
    private val codex = Author.Agent("Codex", "codex")
    private val a = thread("a", Message(claude, "Rename it?"), Message(user, "Why?"))
    private val b = thread("b", Message(claude, "Inline it?"), Message(user, "Sure"))

    private fun thread(id: String, vararg said: Message): CommentThread =
        CommentThread(file = "a.py", line = 1, anchorText = "x", id = id).also { t ->
            said.forEach(t::addMessage)
            threads.add(t)
        }

    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds).also { now = it }

    private fun submitLive(thread: CommentThread, seconds: Long, agent: Author.Agent = claude): Instant =
        handBack.recordLive(thread.id, agent.receiptKey, submittedAt = at(seconds))

    private fun read(thread: CommentThread, agent: Author.Agent = claude) =
        thread.messages.forEach { it.markSeenBy(agent.receiptKey) }

    @Test
    fun `a live submit wakes only the agent it names, with that thread`() {
        val claudeWait = handBack.await(since = t0, timeout = hour, agent = claude)
        val codexWait = handBack.await(since = t0, timeout = hour, agent = codex)

        val woke = submitLive(a, 5)

        assertEquals(Wake.Live(woke, listOf("a")), claudeWait.getNow(null))
        assertFalse(codexWait.isDone)
        assertEquals(listOf(codex), handBack.waitingAgents)
    }

    @Test
    fun `a live wake is stamped with the submit's time, not the moment it is recorded`() {
        val waited = handBack.await(since = t0, timeout = hour, agent = claude)
        val submittedAt = t0.plusSeconds(5)
        at(9)

        handBack.recordLive("a", claude.receiptKey, submittedAt)

        assertEquals(Wake.Live(submittedAt, listOf("a")), waited.getNow(null))
    }

    @Test
    fun `a live submit never moves the project's hand back marker`() {
        handBack.await(since = t0, timeout = hour, agent = claude)

        submitLive(a, 5)

        assertNull(handBack.lastAt)
    }

    @Test
    fun `a live submit made while the agent works answers its next wait at once`() {
        val made = submitLive(a, 5)
        at(9)

        assertEquals(Wake.Live(made, listOf("a")), handBack.await(since = t0, timeout = hour, agent = claude).getNow(null))
    }

    @Test
    fun `a live submit already delivered does not answer the next wait`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        val delivered = submitLive(a, 5)

        assertFalse(handBack.await(since = delivered, timeout = hour, agent = claude).isDone)
    }

    @Test
    fun `a live submit the agent already saw in a sweep does not answer its wait`() {
        val sweepCursor = submitLive(a, 5)

        assertFalse(handBack.await(since = sweepCursor, timeout = hour, agent = claude).isDone)
    }

    @Test
    fun `a live submit the agent already read does not wake it`() {
        submitLive(a, 5)
        read(a)

        assertFalse(handBack.await(since = t0, timeout = hour, agent = claude).isDone)
    }

    @Test
    fun `a follow-up sent while the agent answers the first live wake reaches its next wait`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        val first = submitLive(a, 5)
        read(a)
        a.addMessage(Message(user, "Also rename the test"))
        val followUp = submitLive(a, 7)
        a.addMessage(Message(claude, "Renamed."))

        assertEquals(Turn.USER_OWES, a.turnFor(claude.receiptKey))
        assertEquals(Wake.Live(followUp, listOf("a")), handBack.await(since = first, timeout = hour, agent = claude).getNow(null))
    }

    @Test
    fun `a forgotten live submit never wakes anyone`() {
        submitLive(a, 5)

        handBack.forgetLive("a")

        assertFalse(handBack.await(since = t0, timeout = hour, agent = claude).isDone)
    }

    @Test
    fun `a pending live submit is not another agent's to collect`() {
        submitLive(a, 5)

        assertFalse(handBack.await(since = t0, timeout = hour, agent = codex).isDone)
    }

    @Test
    fun `without a cursor a pending live submit does not answer at once`() {
        submitLive(a, 5)

        assertFalse(handBack.await(since = null, timeout = hour, agent = claude).isDone)
    }

    @Test
    fun `live submits pending in several threads arrive together, the cursor at the newest`() {
        submitLive(a, 5)
        submitLive(b, 6)
        val newest = submitLive(a, 7)

        assertEquals(Wake.Live(newest, listOf("b", "a")), handBack.await(since = t0, timeout = hour, agent = claude).getNow(null))
    }

    @Test
    fun `a pending hand back wins over pending live submits, and covers them`() {
        at(5)
        handBack.record()
        val live = submitLive(a, 8)

        val woke = handBack.await(since = t0, timeout = hour, agent = claude).getNow(null)

        assertEquals(Wake.HandedBack(live, liveThreadIds = listOf("a")), woke)
        assertFalse(handBack.await(since = (woke as Wake.Delivery).at, timeout = hour, agent = claude).isDone)
    }

    @Test
    fun `a hand back that wins over a live follow-up names it, so the follow-up is not lost`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        val first = submitLive(a, 5)
        read(a)
        a.addMessage(Message(user, "Also rename the test"))
        val followUp = submitLive(a, 7)
        a.addMessage(Message(claude, "Renamed."))
        val handedBack = at(9).let { handBack.record() }

        val woke = handBack.await(since = first, timeout = hour, agent = claude).getNow(null)

        assertTrue(followUp < handedBack)
        assertEquals(Wake.HandedBack(handedBack, liveThreadIds = listOf("a")), woke)
        assertEquals(t0.plusSeconds(9), handBack.liveDeliveredAt(claude.receiptKey, "a"))
    }

    @Test
    fun `a hand back aimed at another agent stays theirs, even once a later live submit is owed to you`() {
        a.resolve(user)
        b.resolve(user)
        val done = thread("d", Message(claude, "Done."))
        thread("c", Message(user, "Codex?", to = Addressee.Agent("codex")))
        handBack.await(since = t0, timeout = hour, agent = codex)
        at(3)
        handBack.record()
        done.addMessage(Message(user, "Claude, one more", to = Addressee.Agent("claude-main")))
        val live = submitLive(done, 5)

        assertEquals(Wake.Live(live, listOf("d")), handBack.await(since = t0, timeout = hour, agent = claude).getNow(null))
    }

    @Test
    fun `a hand back's wake says it is a hand back`() {
        val waited = handBack.await(since = t0, timeout = hour, agent = claude)

        val at = at(5).let { handBack.record() }

        assertEquals(Wake.HandedBack(at), waited.getNow(null))
    }

    @Test
    fun `a hand back recorded on the same tick as a delivered live wake still reaches the agent`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        val delivered = submitLive(a, 5)

        val handedBack = handBack.record()

        assertTrue(handedBack > delivered)
        assertEquals(Wake.HandedBack(handedBack), handBack.await(since = delivered, timeout = hour, agent = claude).getNow(null))
    }

    @Test
    fun `a hand back never stamps earlier than a live wake already delivered, even if the clock steps back`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        val delivered = submitLive(a, 5)
        now = t0.plusSeconds(2)

        assertTrue(handBack.record() > delivered)
    }

    @Test
    fun `a live wake delivered to a waiting agent is remembered until that agent waits again`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        submitLive(a, 5)

        assertEquals(t0.plusSeconds(5), handBack.liveDeliveredAt(claude.receiptKey, "a"))
        handBack.await(since = t0.plusSeconds(5), timeout = hour, agent = claude)
        assertNull(handBack.liveDeliveredAt(claude.receiptKey, "a"))
    }

    @Test
    fun `a live wake collected at once is remembered as delivered, and announced`() {
        var changes = 0
        submitLive(a, 5)
        handBack.addListener { changes++ }
        at(8)

        handBack.await(since = t0, timeout = hour, agent = claude)

        assertEquals(t0.plusSeconds(8), handBack.liveDeliveredAt(claude.receiptKey, "a"))
        assertEquals(1, changes)
    }

    @Test
    fun `a live submit still pending was never delivered`() {
        submitLive(a, 5)

        assertNull(handBack.liveDeliveredAt(claude.receiptKey, "a"))
    }

    @Test
    fun `forgetting a live thread forgets its delivery too`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        submitLive(a, 5)

        handBack.forgetLive("a")

        assertNull(handBack.liveDeliveredAt(claude.receiptKey, "a"))
    }

    @Test
    fun `a hand back delivers no live wake`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        at(5)

        handBack.record()

        assertNotNull(handBack.lastAt)
        assertNull(handBack.liveDeliveredAt(claude.receiptKey, "a"))
    }

    @Test
    fun `wakes name their reason on the wire`() {
        assertEquals("hand_back", Wake.HandedBack(t0).reason)
        assertEquals("live", Wake.Live(t0, listOf("a")).reason)
    }

    @Test
    fun `a timeout wake names reason timeout and carries no handed back time`() {
        assertEquals("timeout", Wake.TimedOut.reason)
        assertFalse((Wake.TimedOut as Wake) is Wake.Delivery)
    }
}
