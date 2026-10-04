package dev.marginalis.core

import dev.marginalis.core.Presence.State.LISTENING
import dev.marginalis.core.Presence.State.WORKING
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class PresenceTextTest {

    private val t0 = Instant.parse("2026-10-04T10:00:00Z")
    private val claude = Author.Agent("Claude", "claude-main")

    private fun after(duration: Duration) = t0.plus(duration)

    @Test
    fun `the tooltip reads name, state and elapsed time`() {
        val working = Presence(claude, WORKING, t0, staysListening = true)

        assertEquals("Claude · working · 4 min", PresenceText.tooltip(working, now = after(Duration.ofMinutes(4))))
    }

    @Test
    fun `a listening agent's tooltip says listening`() {
        val listening = Presence(claude, LISTENING, t0, staysListening = true)

        assertEquals("Claude · listening · 12 min", PresenceText.tooltip(listening, now = after(Duration.ofMinutes(12))))
    }

    @Test
    fun `elapsed under a minute reads just now`() {
        assertEquals("just now", PresenceText.elapsed(Duration.ofSeconds(59)))
        assertEquals("just now", PresenceText.elapsed(Duration.ofSeconds(-3)))
    }

    @Test
    fun `elapsed under an hour reads whole minutes`() {
        assertEquals("1 min", PresenceText.elapsed(Duration.ofSeconds(119)))
        assertEquals("59 min", PresenceText.elapsed(Duration.ofMinutes(59).plusSeconds(59)))
    }

    @Test
    fun `elapsed beyond an hour reads hours and minutes`() {
        assertEquals("1 h 5 min", PresenceText.elapsed(Duration.ofMinutes(65)))
        assertEquals("2 h", PresenceText.elapsed(Duration.ofHours(2).plusSeconds(30)))
    }

    @Test
    fun `a one-shot agent's tooltip says so`() {
        val oneShot = Presence(claude, LISTENING, t0, staysListening = false)

        assertEquals("Claude · listening · just now · one-shot", PresenceText.tooltip(oneShot, now = t0))
    }

    @Test
    fun `the tooltip names the agent as the margin does, nickname included`() {
        val builder = Presence(Author.Agent("Claude · builder", "claude-builder"), WORKING, t0, staysListening = true)

        assertEquals("Builder · working · just now", PresenceText.tooltip(builder, now = t0, name = "Builder"))
    }
}
