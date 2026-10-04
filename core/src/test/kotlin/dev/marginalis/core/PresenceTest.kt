package dev.marginalis.core

import dev.marginalis.core.Presence.State.LISTENING
import dev.marginalis.core.Presence.State.WORKING
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresenceTest {

    private val t0 = Instant.parse("2026-10-04T10:00:00Z")
    private var now = t0
    private val hour = Duration.ofHours(1)
    private val claude = Author.Agent("Claude", "claude-main")
    private val codex = Author.Agent("Codex", "codex")
    private val fable = Author.Agent("Fable", "fable")
    private val owed = mutableSetOf<Author.Agent>()
    private val handBack = HandBack(snapshotAwaiting = { owed.toSet()::contains }, clock = { now })
    private var changes = 0

    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds).also { now = it }

    private fun listening(agent: Author.Agent, since: Instant, stays: Boolean = false) = Presence(agent, LISTENING, since, stays)

    private fun working(agent: Author.Agent, since: Instant, stays: Boolean = false) = Presence(agent, WORKING, since, stays)

    private fun countChanges() = handBack.addListener { changes++ }

    private fun timeOut(agent: Author.Agent, stay: Boolean = false): Wake {
        val settled = CountDownLatch(2)
        handBack.addListener { settled.countDown() }
        val waited = handBack.await(since = null, timeout = Duration.ofMillis(20), agent = agent, stay = stay)
        val wake = waited.get(5, TimeUnit.SECONDS)
        assertTrue(settled.await(5, TimeUnit.SECONDS))
        return wake
    }

    @Test
    fun `an agent that waits is listening, since the moment its wait opened`() {
        at(3)
        handBack.await(since = null, timeout = hour, agent = claude)

        assertEquals(listOf(listening(claude, t0.plusSeconds(3))), handBack.presence)
    }

    @Test
    fun `an agent woken by a round meant for it is working since the round's time`() {
        handBack.await(since = null, timeout = hour, agent = claude)
        owed += claude

        val round = at(5).let { handBack.record() }

        assertEquals(listOf(working(claude, round)), handBack.presence)
    }

    @Test
    fun `an agent woken by a live submit is working since that wake`() {
        handBack.await(since = null, timeout = hour, agent = claude)

        val submitted = handBack.recordLive("a", claude.receiptKey, submittedAt = at(4))

        assertEquals(listOf(working(claude, submitted)), handBack.presence)
    }

    @Test
    fun `an agent answered at once by a pending round on arrival is working, never listening`() {
        handBack.await(since = null, timeout = hour, agent = codex)
        owed += setOf(codex, claude)
        at(2)
        handBack.record()
        at(7)

        val answered = handBack.await(since = t0, timeout = hour, agent = claude)

        assertTrue(answered.isDone)
        assertEquals(listOf(working(codex, t0.plusSeconds(2)), working(claude, t0.plusSeconds(7))), handBack.presence)
    }

    @Test
    fun `an agent working again is listening the moment it opens its next wait`() {
        handBack.await(since = null, timeout = hour, agent = claude)
        owed += claude
        val round = at(5).let { handBack.record() }
        owed.clear()
        at(9)

        handBack.await(since = round, timeout = hour, agent = claude)

        assertEquals(listOf(listening(claude, t0.plusSeconds(9))), handBack.presence)
    }

    @Test
    fun `a hung-up wait leaves its agent absent, announced`() {
        val waited = handBack.await(since = null, timeout = hour, agent = claude, stay = true)
        countChanges()

        waited.cancel(false)

        assertEquals(emptyList(), handBack.presence)
        assertEquals(1, changes)
    }

    @Test
    fun `an agent with two open waits stays listening when one hangs up`() {
        val first = handBack.await(since = null, timeout = hour, agent = claude)
        at(4)
        handBack.await(since = null, timeout = hour, agent = claude)

        first.cancel(false)

        assertEquals(listOf(listening(claude, t0.plusSeconds(4))), handBack.presence)
    }

    @Test
    fun `an opted-in agent stays present after a timeout, working since the timeout`() {
        at(6)

        assertEquals(Wake.TimedOut, timeOut(claude, stay = true))

        assertEquals(listOf(working(claude, t0.plusSeconds(6), stays = true)), handBack.presence)
    }

    @Test
    fun `a one-shot agent drops out after a timeout`() {
        timeOut(claude)

        assertEquals(emptyList(), handBack.presence)
    }

    @Test
    fun `an empty round wakes every waiter as today`() {
        val claudeWait = handBack.await(since = null, timeout = hour, agent = claude, stay = true)
        val codexWait = handBack.await(since = null, timeout = hour, agent = codex)

        val round = at(5).let { handBack.record() }

        assertEquals(Wake.HandedBack(round), claudeWait.getNow(null))
        assertEquals(Wake.HandedBack(round), codexWait.getNow(null))
    }

    @Test
    fun `after an empty round an opted-in agent stays present and listens again as soon as it re-arms`() {
        handBack.await(since = null, timeout = hour, agent = claude, stay = true)
        val round = at(5).let { handBack.record() }

        assertEquals(listOf(working(claude, round, stays = true)), handBack.presence)
        at(8)
        handBack.await(since = round, timeout = hour, agent = claude, stay = true)
        assertEquals(listOf(listening(claude, t0.plusSeconds(8), stays = true)), handBack.presence)
    }

    @Test
    fun `after an empty round a one-shot agent drops out`() {
        handBack.await(since = null, timeout = hour, agent = claude)

        at(5).let { handBack.record() }

        assertEquals(emptyList(), handBack.presence)
    }

    @Test
    fun `after a non-empty round a one-shot agent is working, since it is expected back`() {
        handBack.await(since = null, timeout = hour, agent = claude)
        owed += claude

        val round = at(5).let { handBack.record() }

        assertEquals(listOf(working(claude, round)), handBack.presence)
    }

    @Test
    fun `a one-shot agent that arrives late to an empty round drops out`() {
        handBack.await(since = null, timeout = hour, agent = codex)
        at(5).let { handBack.record() }
        at(7)

        val answered = handBack.await(since = t0, timeout = hour, agent = claude)

        assertTrue(answered.isDone)
        assertEquals(emptyList(), handBack.presence)
    }

    @Test
    fun `an opted-in agent that arrives late to an empty round stays, working since it arrived`() {
        handBack.await(since = null, timeout = hour, agent = codex)
        at(5).let { handBack.record() }
        at(7)

        handBack.await(since = t0, timeout = hour, agent = claude, stay = true)

        assertEquals(listOf(working(claude, t0.plusSeconds(7), stays = true)), handBack.presence)
    }

    @Test
    fun `a one-shot agent that arrives late to a round whose threads it has since answered drops out`() {
        handBack.await(since = null, timeout = hour, agent = codex)
        owed += setOf(codex, claude)
        at(5).let { handBack.record() }
        owed -= claude
        at(7)

        val answered = handBack.await(since = t0, timeout = hour, agent = claude)

        assertTrue(answered.isDone)
        assertEquals(listOf(working(codex, t0.plusSeconds(5))), handBack.presence)
    }

    @Test
    fun `an agent answered at once by one wait stays working when its other open wait hangs up`() {
        owed += claude
        val round = at(2).let { handBack.record() }
        at(3)
        val parked = handBack.await(since = round, timeout = hour, agent = claude)
        at(4)
        handBack.await(since = t0, timeout = hour, agent = claude)

        parked.cancel(false)

        assertEquals(listOf(working(claude, t0.plusSeconds(4))), handBack.presence)
    }

    @Test
    fun `staying is remembered per agent key, so a later wait without stay still counts as opted in`() {
        handBack.await(since = null, timeout = hour, agent = claude, stay = true).cancel(false)
        handBack.await(since = null, timeout = hour, agent = claude)

        assertEquals(listOf(listening(claude, t0, stays = true)), handBack.presence)
    }

    @Test
    fun `agents are listed once each in the order they first arrived`() {
        handBack.await(since = null, timeout = hour, agent = codex)
        handBack.await(since = null, timeout = hour, agent = claude)
        handBack.await(since = null, timeout = hour, agent = codex)

        assertEquals(listOf(codex, claude), handBack.presence.map { it.agent })
    }

    @Test
    fun `stopping a listening agent ends its open wait at once with a stopped wake, and it drops out`() {
        val waited = handBack.await(since = null, timeout = hour, agent = claude, stay = true)
        handBack.await(since = null, timeout = hour, agent = codex)
        countChanges()

        handBack.stop(claude.receiptKey)

        assertEquals(Wake.Stopped, waited.getNow(null))
        assertEquals(listOf(codex), handBack.presence.map { it.agent })
        assertEquals(1, changes)
    }

    @Test
    fun `stopping a working agent drops it at once, and its next wait answers stopped at once`() {
        handBack.await(since = null, timeout = hour, agent = claude, stay = true)
        val round = at(5).let { handBack.record() }

        handBack.stop(claude.receiptKey)

        assertEquals(emptyList(), handBack.presence)
        assertEquals(Wake.Stopped, handBack.await(since = round, timeout = hour, agent = claude, stay = true).getNow(null))
        assertEquals(emptyList(), handBack.presence)
    }

    @Test
    fun `a stop is consumed, and the agent is no longer opted in until it says stay again`() {
        handBack.await(since = null, timeout = hour, agent = claude, stay = true)
        val round = at(5).let { handBack.record() }
        handBack.stop(claude.receiptKey)
        handBack.await(since = round, timeout = hour, agent = claude)
        at(9)

        val again = handBack.await(since = round, timeout = hour, agent = claude)

        assertFalse(again.isDone)
        assertEquals(listOf(listening(claude, t0.plusSeconds(9))), handBack.presence)
    }

    @Test
    fun `stopping all agents stops every listening and working agent`() {
        val claudeWait = handBack.await(since = null, timeout = hour, agent = claude)
        handBack.await(since = null, timeout = hour, agent = codex)
        owed += codex
        val round = at(5).let { handBack.record() }

        handBack.stopAll()

        assertEquals(Wake.Stopped, claudeWait.getNow(null))
        assertEquals(emptyList(), handBack.presence)
        assertEquals(Wake.Stopped, handBack.await(since = round, timeout = hour, agent = codex).getNow(null))
    }

    @Test
    fun `a stop that loses to its wait timing out is still answered on the agent's next wait`() {
        val claudeWait = handBack.await(since = null, timeout = hour, agent = claude, stay = true)
        val codexWait = handBack.await(since = null, timeout = hour, agent = codex, stay = true)
        claudeWait.thenRun { codexWait.complete(Wake.TimedOut) }

        handBack.stopAll()

        assertEquals(Wake.TimedOut, codexWait.getNow(null))
        assertEquals(emptyList(), handBack.presence)
        assertEquals(Wake.Stopped, handBack.await(since = null, timeout = hour, agent = codex, stay = true).getNow(null))
    }

    @Test
    fun `a stop that loses to a hang-up leaves no stop pending`() {
        val claudeWait = handBack.await(since = null, timeout = hour, agent = claude)
        val codexWait = handBack.await(since = null, timeout = hour, agent = codex)
        claudeWait.thenRun { codexWait.cancel(true) }

        handBack.stopAll()

        assertEquals(emptyList(), handBack.presence)
        assertFalse(handBack.await(since = null, timeout = hour, agent = codex).isDone)
    }

    @Test
    fun `a round that loses to a hang-up does not leave its agent working`() {
        val claudeWait = handBack.await(since = null, timeout = hour, agent = claude)
        val codexWait = handBack.await(since = null, timeout = hour, agent = codex, stay = true)
        owed += setOf(claude, codex)
        claudeWait.thenRun { codexWait.cancel(true) }

        val round = at(5).let { handBack.record() }

        assertEquals(listOf(working(claude, round)), handBack.presence)
    }

    @Test
    fun `a round that loses to a timeout leaves a one-shot agent out and an opted-in agent working`() {
        val claudeWait = handBack.await(since = null, timeout = hour, agent = claude)
        val codexWait = handBack.await(since = null, timeout = hour, agent = codex)
        val fableWait = handBack.await(since = null, timeout = hour, agent = fable, stay = true)
        owed += setOf(claude, codex, fable)
        claudeWait.thenRun { codexWait.complete(Wake.TimedOut); fableWait.complete(Wake.TimedOut) }

        val round = at(5).let { handBack.record() }

        assertEquals(listOf(working(claude, round), working(fable, round, stays = true)), handBack.presence)
    }

    @Test
    fun `a live wake that loses to a hang-up does not leave its agent working`() {
        var hangUp = {}
        val racing = HandBack(clock = { hangUp(); now })
        val waited = racing.await(since = null, timeout = hour, agent = claude)
        hangUp = { waited.cancel(true) }

        racing.recordLive("a", claude.receiptKey, submittedAt = at(4))

        assertEquals(emptyList(), racing.presence)
    }

    @Test
    fun `an agent stays working when one of its waits got the wake and another hung up`() {
        val first = handBack.await(since = null, timeout = hour, agent = claude)
        val second = handBack.await(since = null, timeout = hour, agent = claude)
        first.thenRun { second.cancel(true) }

        val submitted = handBack.recordLive("a", claude.receiptKey, submittedAt = at(4))

        assertEquals(listOf(working(claude, submitted)), handBack.presence)
    }

    @Test
    fun `stopping an agent that is not present changes nothing and announces nothing`() {
        handBack.await(since = null, timeout = hour, agent = codex)
        countChanges()

        handBack.stop(claude.receiptKey)
        handBack.await(since = null, timeout = hour, agent = codex)

        assertFalse(handBack.await(since = null, timeout = hour, agent = claude).isDone)
        assertEquals(2, changes)
    }

    @Test
    fun `closing forgets every agent, announced once`() {
        handBack.await(since = null, timeout = hour, agent = claude, stay = true)
        handBack.await(since = null, timeout = hour, agent = codex)
        owed += codex
        at(5).let { handBack.record() }
        handBack.stop(codex.receiptKey)
        countChanges()

        handBack.close()

        assertEquals(emptyList(), handBack.presence)
        assertEquals(1, changes)
    }

    @Test
    fun `a wait that arrives after closing answers closing at once, unannounced`() {
        handBack.close()
        countChanges()

        val late = handBack.await(since = null, timeout = hour, agent = claude, stay = true)

        assertEquals(Wake.Closing, late.getNow(null))
        assertEquals(emptyList(), handBack.presence)
        assertEquals(0, changes)
    }

    @Test
    fun `a submit round is possible only while at least one agent is listening`() {
        assertFalse(handBack.canSubmitRound)

        handBack.await(since = null, timeout = hour, agent = claude)

        assertTrue(handBack.canSubmitRound)
    }

    @Test
    fun `a working agent alone does not make a submit round possible`() {
        handBack.await(since = null, timeout = hour, agent = claude, stay = true)

        at(5).let { handBack.record() }

        assertFalse(handBack.canSubmitRound)
    }

    @Test
    fun `a targeted round leaves a listening agent with nothing owed still listening`() {
        handBack.await(since = null, timeout = hour, agent = claude)
        handBack.await(since = null, timeout = hour, agent = codex)
        owed += codex

        val round = at(5).let { handBack.record() }

        assertEquals(listOf(listening(claude, t0), working(codex, round)), handBack.presence)
    }

    @Test
    fun `a change that alters nothing is not announced`() {
        countChanges()

        handBack.record()
        handBack.recordLive("a", claude.receiptKey, submittedAt = at(3))
        handBack.stopAll()
        handBack.close()

        assertEquals(0, changes)
    }

    @Test
    fun `waking working agents to listening is announced`() {
        handBack.await(since = null, timeout = hour, agent = claude)
        owed += claude
        val round = at(5).let { handBack.record() }
        countChanges()

        handBack.await(since = round, timeout = hour, agent = claude)

        assertEquals(1, changes)
    }
}
