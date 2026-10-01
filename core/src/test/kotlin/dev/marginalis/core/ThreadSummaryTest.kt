package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadSummaryTest {

    private val user = Author.User("Muhammad")
    private val builder = Author.Agent("Claude · builder", "claude-builder")
    private val reviewer = Author.Agent("Claude · review", "claude-review")

    private fun thread(vararg said: Message) =
        CommentThread(file = "a.kt", line = 0, anchorText = "x").also { t -> said.forEach(t::addMessage) }

    @Test
    fun `counts the messages and the ones the reader has not seen, without seeing them`() {
        val thread = thread(Message(user, "one", seenBy = setOf("claude-builder")), Message(user, "two"), Message(reviewer, "three"))

        val summary = ThreadSummary.of(thread, "claude-builder")

        assertEquals(3, summary.messages)
        assertEquals(2, summary.unread)
        assertEquals(2, thread.unreadCountFor("claude-builder"))
    }

    @Test
    fun `names the last speaker`() {
        assertEquals(reviewer, ThreadSummary.of(thread(Message(user, "q"), Message(reviewer, "a")), "claude-builder").lastAuthor)
    }

    @Test
    fun `whose turn is read for the reader, so a message addressed to another agent is not the reader's debt`() {
        val handedToReviewer = thread(Message(user, "q", to = Addressee.Agent("claude-review")))

        assertNull(ThreadSummary.of(handedToReviewer, "claude-builder").awaiting)
        assertEquals(Turn.AGENT_OWES, ThreadSummary.of(handedToReviewer, "claude-review").awaiting)
        assertEquals(Turn.USER_OWES, ThreadSummary.of(thread(Message(user, "q"), Message(builder, "a")), "claude-review").awaiting)
    }

    @Test
    fun `a closed thread awaits no one`() {
        val resolved = thread(Message(user, "q")).apply { resolve(user) }

        assertNull(ThreadSummary.of(resolved, "claude-builder").awaiting)
    }

    @Test
    fun `an empty thread has no last speaker`() {
        assertNull(ThreadSummary.of(CommentThread(file = null, line = null, anchorText = null), "claude-builder").lastAuthor)
    }
}
