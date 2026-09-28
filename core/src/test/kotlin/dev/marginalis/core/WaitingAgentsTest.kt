package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaitingAgentsTest {

    @Test
    fun `nobody waiting says nothing`() {
        assertNull(WaitingAgents.describe(emptyList()))
    }

    @Test
    fun `one agent is waiting`() {
        assertEquals("Claude is waiting", WaitingAgents.describe(listOf("Claude")))
    }

    @Test
    fun `two agents are waiting`() {
        assertEquals("Claude and Codex are waiting", WaitingAgents.describe(listOf("Claude", "Codex")))
    }

    @Test
    fun `several agents are listed with the last one joined by and`() {
        assertEquals(
            "Claude, Codex and Gemini are waiting",
            WaitingAgents.describe(listOf("Claude", "Codex", "Gemini")),
        )
    }

    @Test
    fun `hand back text names who is waiting`() {
        assertEquals("Hand Back — Claude · impl is waiting", WaitingAgents.handBackText(listOf("Claude · impl")))
    }

    @Test
    fun `hand back text names every waiting agent`() {
        assertEquals(
            "Hand Back — Claude · design and Claude · impl are waiting",
            WaitingAgents.handBackText(listOf("Claude · design", "Claude · impl")),
        )
    }

    @Test
    fun `hand back text with nobody waiting promises a later pickup`() {
        assertEquals(
            "Hand Back — no agent is waiting; it will be picked up when one starts",
            WaitingAgents.handBackText(emptyList()),
        )
    }
}
