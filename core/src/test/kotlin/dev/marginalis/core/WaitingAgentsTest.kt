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
    fun `hand back tooltip names who is waiting`() {
        assertEquals("Claude is waiting — hand back", WaitingAgents.handBackTooltip(listOf("Claude")))
    }

    @Test
    fun `hand back tooltip with nobody waiting promises a later pickup`() {
        assertEquals(
            "No agent is waiting — your hand back will be picked up when one starts",
            WaitingAgents.handBackTooltip(emptyList()),
        )
    }
}
