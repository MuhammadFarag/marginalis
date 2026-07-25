package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AggregateStateTest {

    private val agent = Author.Agent("Claude")
    private val user = Author.User("Muhammad")

    private fun thread(severity: Severity? = null) =
        CommentThread(file = "a.py", line = 1, anchorText = "x", severity = severity)

    private fun unread() = thread().also { it.addMessage(Message(user, "unconsumed")) }

    @Test
    fun `all resolved merges to resolved — and so does the empty set`() {
        val done = thread(Severity.BLOCKER).also { it.resolve(user) }
        assertEquals(AggregateState.RESOLVED, AggregateState.of(listOf(done)))
        assertEquals(AggregateState.RESOLVED, AggregateState.of(emptyList()))
    }

    @Test
    fun `a broken anchor outranks everything still alive`() {
        val orphan = thread().also { it.markOrphaned() }
        assertEquals(
            AggregateState.ORPHANED,
            AggregateState.of(listOf(orphan, thread(Severity.BLOCKER), unread())),
        )
    }

    @Test
    fun `an open blocker outranks the unread signal`() {
        assertEquals(
            AggregateState.OPEN_BLOCKER,
            AggregateState.of(listOf(thread(Severity.BLOCKER), unread())),
        )
    }

    @Test
    fun `a resolved blocker no longer alarms`() {
        val passed = thread(Severity.BLOCKER).also { it.resolve(user) }
        assertEquals(AggregateState.OPEN, AggregateState.of(listOf(passed, thread())))
    }

    @Test
    fun `unread outranks the plain balloon, and a consumed message is plain again`() {
        val consumed = thread().also {
            it.addMessage(Message(user, "note"))
            it.messages.single().markSeenBy(agent.receiptKey)
        }
        assertEquals(AggregateState.UNREAD, AggregateState.of(listOf(unread(), thread())))
        assertEquals(AggregateState.OPEN, AggregateState.of(listOf(consumed, thread())))
    }

    @Test
    fun `a nit deliberately changes nothing`() {
        assertEquals(AggregateState.OPEN, AggregateState.of(listOf(thread(Severity.NIT))))
    }
}
