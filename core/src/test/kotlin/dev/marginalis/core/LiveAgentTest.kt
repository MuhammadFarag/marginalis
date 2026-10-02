package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveAgentTest {

    private val user = Author.User("Muhammad")
    private val builder = Author.Agent("Claude · builder", "claude-builder")
    private val reviewer = Author.Agent("Claude · review", "claude-review")

    @Test
    fun `the composer's addressee is the agent to wake`() {
        val said = listOf(Message(builder, "Done."))

        assertEquals("claude-review", LiveAgent.keyOf(said, to = Addressee.Agent("claude-review")))
    }

    @Test
    fun `a sent reply addressed to an agent wakes that agent`() {
        val said = listOf(Message(builder, "Done."), Message(user, "Review?", to = Addressee.Agent("claude-review")))

        assertEquals("claude-review", LiveAgent.keyOf(said, to = null))
    }

    @Test
    fun `an unaddressed reply wakes the agent whose message it answers`() {
        val said = listOf(Message(reviewer, "Looks off."), Message(builder, "Fixed."), Message(user, "Thanks"))

        assertEquals("claude-builder", LiveAgent.keyOf(said, to = null))
    }

    @Test
    fun `an agent handing over to another agent is still the one the user answers`() {
        val said = listOf(Message(user, "Fix it"), Message(builder, "Over to you.", to = Addressee.Agent("claude-review")))

        assertEquals("claude-builder", LiveAgent.keyOf(said, to = null))
    }

    @Test
    fun `a reply addressed to the user falls back to the agent it answers`() {
        assertEquals("claude-builder", LiveAgent.keyOf(listOf(Message(builder, "Done.")), to = Addressee.User))
    }

    @Test
    fun `no agent to wake where none has spoken, none is addressed and none is waiting`() {
        assertNull(LiveAgent.keyOf(listOf(Message(user, "Hmm")), to = null))
    }

    @Test
    fun `a thread the user started goes to the one agent waiting`() {
        assertEquals("claude-builder", LiveAgent.keyOf(listOf(Message(user, "Hmm")), to = null, waiting = listOf(builder)))
    }

    @Test
    fun `with several agents waiting, nobody is picked until the user addresses one`() {
        val started = listOf(Message(user, "Hmm"))

        assertNull(LiveAgent.keyOf(started, to = null, waiting = listOf(builder, reviewer)))
        assertEquals("claude-review", LiveAgent.keyOf(started, to = Addressee.Agent("claude-review"), waiting = listOf(builder, reviewer)))
    }

    @Test
    fun `an agent that spoke in the thread outranks whoever is waiting`() {
        assertEquals("claude-builder", LiveAgent.keyOf(listOf(Message(builder, "Done.")), to = null, waiting = listOf(reviewer)))
    }
}
