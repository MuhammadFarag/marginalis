package dev.marginalis.core

import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandBackTest {

    private val t0 = Instant.parse("2026-09-27T10:00:00Z")
    private var now = t0
    private val handBack = HandBack { now }
    private val hour = Duration.ofHours(1)
    private val claude = Author.Agent("Claude", "claude-main")
    private val codex = Author.Agent("Codex")
    private var changes = 0
    private val countChange: () -> Unit = { changes++ }
    private val owed = mutableSetOf<Author.Agent>()
    private val targeted = HandBack(snapshotAwaiting = { owed.toSet()::contains }, clock = { now })

    @Test
    fun `a hand back after the cursor answers at once`() {
        now = t0.plusSeconds(10)
        handBack.record()

        val waited = handBack.await(since = t0, timeout = hour)

        assertEquals(Wake.HandedBack(t0.plusSeconds(10)), waited.getNow(null))
        assertEquals(0, handBack.waiting)
    }

    @Test
    fun `a hand back at or before the cursor was already answered, so the wait holds`() {
        handBack.record()

        val atCursor = handBack.await(since = t0, timeout = hour)
        val afterCursor = handBack.await(since = t0.plusSeconds(1), timeout = hour)

        assertFalse(atCursor.isDone)
        assertFalse(afterCursor.isDone)
        assertEquals(2, handBack.waiting)
    }

    @Test
    fun `waiting again from the hand back that woke you holds until the next one`() {
        val wokenBy = handBack.record()

        val again = handBack.await(since = wokenBy, timeout = hour)

        assertFalse(again.isDone)
        now = t0.plusSeconds(1)
        assertEquals(Wake.HandedBack(handBack.record()), again.getNow(null))
    }

    @Test
    fun `without a cursor only the next hand back counts`() {
        handBack.record()

        assertFalse(handBack.await(since = null, timeout = hour).isDone)
    }

    @Test
    fun `the next hand back wakes every waiter`() {
        val first = handBack.await(since = t0, timeout = hour)
        val second = handBack.await(since = null, timeout = hour)
        now = t0.plusSeconds(60)

        val at = handBack.record()

        assertEquals(t0.plusSeconds(60), at)
        assertEquals(Wake.HandedBack(at), first.getNow(null))
        assertEquals(Wake.HandedBack(at), second.getNow(null))
        assertEquals(0, handBack.waiting)
        assertEquals(at, handBack.lastAt)
    }

    @Test
    fun `a wait with no round ends at its timeout with a timeout wake`() {
        val startedAndDropped = CountDownLatch(2)
        handBack.addListener { startedAndDropped.countDown() }

        val waited = handBack.await(since = t0, timeout = Duration.ofMillis(20))

        assertEquals(Wake.TimedOut, waited.get(5, TimeUnit.SECONDS))
        assertTrue(startedAndDropped.await(5, TimeUnit.SECONDS))
        assertEquals(0, handBack.waiting)
    }

    @Test
    fun `a cancelled wait stops waiting`() {
        val waited = handBack.await(since = t0, timeout = hour)

        waited.cancel(false)

        assertEquals(0, handBack.waiting)
    }

    @Test
    fun `closing ends every open wait with a closing wake`() {
        val first = handBack.await(since = t0, timeout = hour)
        val second = handBack.await(since = t0, timeout = hour)

        handBack.close()

        assertEquals(Wake.Closing, first.getNow(null))
        assertEquals(Wake.Closing, second.getNow(null))
        assertEquals(0, handBack.waiting)
    }

    @Test
    fun `a restored hand back is remembered without waking anyone`() {
        val waited = handBack.await(since = null, timeout = hour)

        handBack.restore(t0.minusSeconds(5))

        assertEquals(t0.minusSeconds(5), handBack.lastAt)
        assertFalse(waited.isDone)
        assertEquals(Wake.HandedBack(t0.minusSeconds(5)), handBack.await(since = t0.minusSeconds(6), timeout = hour).getNow(null))
    }

    @Test
    fun `restoring never rewinds a newer hand back`() {
        handBack.record()

        handBack.restore(t0.minusSeconds(60))
        handBack.restore(null)

        assertEquals(t0, handBack.lastAt)
    }

    @Test
    fun `nobody is waiting until a wait starts, and the start is announced`() {
        handBack.addListener(countChange)
        assertEquals(emptyList<Author.Agent>(), handBack.waitingAgents)

        handBack.await(since = t0, timeout = hour, agent = claude)

        assertEquals(listOf(claude), handBack.waitingAgents)
        assertEquals(1, changes)
    }

    @Test
    fun `a wait answered at once never counts as waiting, but announces the agent at work`() {
        handBack.record()
        handBack.addListener(countChange)

        handBack.await(since = t0.minusSeconds(1), timeout = hour, agent = claude)

        assertEquals(emptyList<Author.Agent>(), handBack.waitingAgents)
        assertEquals(listOf(Presence.State.WORKING), handBack.presence.map { it.state })
        assertEquals(1, changes)
    }

    @Test
    fun `waiting agents are known by identity, once each, in the order they started`() {
        handBack.await(since = t0, timeout = hour, agent = codex)
        handBack.await(since = t0, timeout = hour, agent = claude)
        handBack.await(since = null, timeout = hour, agent = codex)

        assertEquals(listOf(codex, claude), handBack.waitingAgents)
    }

    @Test
    fun `an answered wait drops out, announced once however many woke`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        handBack.await(since = t0, timeout = hour, agent = codex)
        handBack.addListener(countChange)

        handBack.record()

        assertEquals(emptyList<Author.Agent>(), handBack.waitingAgents)
        assertEquals(1, changes)
    }

    @Test
    fun `a timed-out wait drops out and is announced`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        val startedAndDropped = CountDownLatch(2)
        handBack.addListener(countChange)
        handBack.addListener { startedAndDropped.countDown() }

        handBack.await(since = t0, timeout = Duration.ofMillis(20), agent = codex)

        assertTrue(startedAndDropped.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(claude), handBack.waitingAgents)
        assertEquals(2, changes)
    }

    @Test
    fun `a hung-up wait drops out and is announced`() {
        val waited = handBack.await(since = t0, timeout = hour, agent = claude)
        handBack.addListener(countChange)

        waited.cancel(false)

        assertEquals(emptyList<Author.Agent>(), handBack.waitingAgents)
        assertEquals(1, changes)
    }

    @Test
    fun `closed waits drop out and are announced`() {
        handBack.await(since = t0, timeout = hour, agent = claude)
        handBack.addListener(countChange)

        handBack.close()

        assertEquals(emptyList<Author.Agent>(), handBack.waitingAgents)
        assertEquals(1, changes)
    }

    @Test
    fun `nothing is announced when nobody was waiting`() {
        handBack.addListener(countChange)

        handBack.record()
        handBack.close()

        assertEquals(0, changes)
    }

    @Test
    fun `a removed listener hears nothing more`() {
        handBack.addListener(countChange)
        handBack.removeListener(countChange)

        handBack.await(since = t0, timeout = hour, agent = claude)

        assertEquals(0, changes)
    }

    @Test
    fun `an unintroduced waiter is just Agent`() {
        handBack.await(since = t0, timeout = hour)

        assertEquals(listOf(Author.Agent.ANONYMOUS), handBack.waitingAgents)
    }

    @Test
    fun `a hand back wakes only the waiters something awaits`() {
        val claudeWait = targeted.await(since = t0, timeout = hour, agent = claude)
        val codexWait = targeted.await(since = t0, timeout = hour, agent = codex)
        owed += codex
        now = t0.plusSeconds(1)

        val at = targeted.record()

        assertEquals(Wake.HandedBack(at), codexWait.getNow(null))
        assertFalse(claudeWait.isDone)
        assertEquals(listOf(claude), targeted.waitingAgents)
    }

    @Test
    fun `when nothing awaits any waiter, every waiter wakes — the end-the-loop signal`() {
        val claudeWait = targeted.await(since = t0, timeout = hour, agent = claude)
        val codexWait = targeted.await(since = t0, timeout = hour, agent = codex)

        val at = targeted.record()

        assertEquals(Wake.HandedBack(at), claudeWait.getNow(null))
        assertEquals(Wake.HandedBack(at), codexWait.getNow(null))
    }

    @Test
    fun `work owed only to an agent that is not waiting wakes every waiter`() {
        val claudeWait = targeted.await(since = t0, timeout = hour, agent = claude)
        owed += codex
        now = t0.plusSeconds(1)

        val at = targeted.record()

        assertEquals(Wake.HandedBack(at), claudeWait.getNow(null))
        assertEquals(Wake.HandedBack(at), targeted.await(since = t0, timeout = hour, agent = codex).getNow(null))
    }

    @Test
    fun `a hand back that passed you by does not answer your next wait at once`() {
        targeted.await(since = t0, timeout = hour, agent = codex)
        owed += codex
        now = t0.plusSeconds(1)
        targeted.record()

        assertFalse(targeted.await(since = t0, timeout = hour, agent = claude).isDone)
    }

    @Test
    fun `a hand back meant for you answers your late wait at once`() {
        targeted.await(since = t0, timeout = hour, agent = codex)
        owed += setOf(codex, claude)
        now = t0.plusSeconds(1)
        val at = targeted.record()

        assertEquals(Wake.HandedBack(at), targeted.await(since = t0, timeout = hour, agent = claude).getNow(null))
    }

    @Test
    fun `a hand back that woke everyone answers every late wait at once`() {
        targeted.await(since = t0, timeout = hour, agent = codex)
        now = t0.plusSeconds(1)
        val at = targeted.record()

        assertEquals(Wake.HandedBack(at), targeted.await(since = t0, timeout = hour, agent = claude).getNow(null))
    }
}
