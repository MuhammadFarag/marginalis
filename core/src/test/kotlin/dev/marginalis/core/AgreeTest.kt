package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AgreeTest {

    private val builder = Author.Agent("Claude · builder", "claude-builder")
    private val user = Author.User("Muhammad")

    private fun thread(vararg said: Message) =
        CommentThread(file = "a.py", line = 1, anchorText = "x").also { t -> said.forEach(t::addMessage) }

    @Test
    fun `the agent's latest word is the one to agree with`() {
        val proposal = Message(builder, "Rename it?")
        val t = thread(Message(user, "Thoughts?"), proposal)

        assertSame(proposal, t.agreeable())
    }

    @Test
    fun `nothing to agree with when the user spoke last, nobody spoke, or the thread is closed`() {
        assertNull(thread(Message(builder, "…"), Message(user, "…")).agreeable())
        assertNull(thread().agreeable())
        assertNull(thread(Message(builder, "…")).also { it.resolve(user) }.agreeable())
    }

    @Test
    fun `nothing to agree with when the agent handed the thread to another agent`() {
        val handedOver = thread(Message(user, "…"), Message(builder, "Over to you.", to = Addressee.Agent("claude-review")))

        assertNull(handedOver.agreeable())
    }

    @Test
    fun `an agent's word addressed to the user can be agreed with`() {
        val toUser = Message(builder, "Ready?", to = Addressee.User)

        assertSame(toUser, thread(toUser).agreeable())
    }

    @Test
    fun `a read fyi offers nothing to agree with — it is nobody's turn`() {
        val noted = CommentThread(file = "a.py", line = 1, anchorText = "x", intent = Intent.FYI)
            .also { it.addMessage(Message(builder, "Lovely.")); it.markReadByUser() }

        assertNull(noted.agreeable())
    }

    @Test
    fun `an agreement is a short user reply addressed to the agent it agrees with`() {
        val agreement = Message.agreement(by = user, with = builder)

        assertEquals(user, agreement.author)
        assertEquals("Agreed.", agreement.body)
        assertTrue(agreement.agrees)
        assertEquals(Addressee.Agent("claude-builder"), agreement.to)
    }

    @Test
    fun `agreeing passes the turn to the agent agreed with`() {
        val proposal = Message(builder, "Rename it?")
        val t = thread(proposal)

        t.addMessage(Message.agreement(by = user, with = builder))

        assertEquals(Turn.AGENT_OWES, t.turnFor("claude-builder"))
        assertNull(t.turnFor("claude-review"))
    }

    @Test
    fun `ordinary messages don't agree`() {
        assertFalse(Message(user, "Agreed.").agrees)
    }
}
