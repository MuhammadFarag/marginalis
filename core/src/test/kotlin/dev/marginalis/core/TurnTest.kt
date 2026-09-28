package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnTest {

    private val agent = Author.Agent("Claude")
    private val user = Author.User("Muhammad")

    private fun thread(lastWord: Author) =
        CommentThread(file = "a.py", line = 1, anchorText = "x").also { it.addMessage(Message(lastWord, "…")) }

    @Test
    fun `no open threads means no one's turn — concluded and orphaned threads ask nothing`() {
        val resolved = thread(agent).also { it.resolve(user) }
        val orphaned = thread(agent).also { it.markOrphaned() }
        assertNull(Turn.of(emptyList()))
        assertNull(Turn.of(listOf(resolved, orphaned)))
    }

    @Test
    fun `an open thread where the agent spoke last is the user's turn`() {
        assertEquals(Turn.USER, Turn.of(listOf(thread(agent))))
    }

    @Test
    fun `open threads where the user spoke last are the agent's turn`() {
        assertEquals(Turn.AGENT, Turn.of(listOf(thread(user), thread(user))))
    }

    @Test
    fun `what the user owes outranks what the agent owes`() {
        assertEquals(Turn.USER, Turn.of(listOf(thread(user), thread(agent), thread(user))))
    }

    @Test
    fun `a single thread's turn is the other party's once someone has spoken`() {
        assertEquals(Turn.USER, thread(agent).turn())
        assertEquals(Turn.AGENT, thread(user).turn())
    }

    @Test
    fun `a concluded or orphaned thread is nobody's turn`() {
        assertNull(thread(user).also { it.resolve(agent) }.turn())
        assertNull(thread(user).also { it.markOrphaned() }.turn())
    }

    @Test
    fun `the awaiting vocabulary names whose turn it is, case-insensitively`() {
        assertEquals(Turn.AGENT, parsed("agent"))
        assertEquals(Turn.USER, parsed("USER"))
        assertNull(parsed(null))
    }

    @Test
    fun `an unknown awaiting value is taught, never silently ignored`() {
        for (raw in listOf("me", "you", "human", "claude", "")) {
            val parsed = Turn.parse(raw)
            assertIs<Parsed.Invalid>(parsed, "'$raw' must be rejected")
            assertTrue(parsed.reason.contains("'agent'") && parsed.reason.contains("'user'"))
        }
    }

    private fun parsed(raw: String?): Turn? = (Turn.parse(raw) as Parsed.Ok).value
}
