package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
