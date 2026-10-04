package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectTabReadingTest {

    private val agent = Author.Agent("Claude", "claude-main")
    private val user = Author.User("Muhammad")

    private fun thread(vararg said: Message) =
        CommentThread(file = null, line = null, anchorText = null).also { t -> said.forEach(t::addMessage) }

    @Test
    fun `with expanded-in-front, an expanded thread in a tab in front counts as read`() {
        assertTrue(ReadWhen.EXPANDED_IN_FRONT.reads(inFront = true, expanded = true))
    }

    @Test
    fun `a collapsed preview never counts as read`() {
        ReadWhen.entries.forEach { assertFalse(it.reads(inFront = true, expanded = false)) }
    }

    @Test
    fun `a tab in the background never marks anything read`() {
        ReadWhen.entries.forEach { assertFalse(it.reads(inFront = false, expanded = true, clickedInto = true)) }
    }

    @Test
    fun `with clicked-into, being expanded in front reads nothing, but clicking into the thread does`() {
        assertFalse(ReadWhen.CLICKED_INTO.reads(inFront = true, expanded = true))
        assertTrue(ReadWhen.CLICKED_INTO.reads(inFront = true, expanded = true, clickedInto = true))
    }

    @Test
    fun `a message unread when the look began carries a new tag`() {
        val old = Message(agent, "Earlier.", readByUser = true)
        val fresh = Message(agent, "Just now.")

        assertEquals(setOf(fresh.id), NewTags().tagsOnSight(thread(old, fresh)))
    }

    @Test
    fun `a message read before the thread is first sighted carries no tag`() {
        val looked = thread(Message(agent, "Just now."))
        looked.markReadByUser()

        assertEquals(emptySet(), NewTags().tagsOnSight(looked))
    }

    @Test
    fun `a message read during this look keeps its tag until the look ends`() {
        val looked = thread(Message(agent, "Just now."))
        val tags = NewTags()
        val atFirstSight = tags.tagsOnSight(looked)

        looked.markReadByUser()

        assertEquals(atFirstSight, tags.tagsOnSight(looked))
    }

    @Test
    fun `after looking away, read messages lose their tag`() {
        val read = Message(agent, "Seen.")
        val stillUnread = Message(agent, "Not yet.")
        val looked = thread(read, stillUnread)
        val tags = NewTags().also { it.tagsOnSight(looked) }
        looked.markReadByUser(listOf(read))

        tags.endLook()

        assertEquals(setOf(stillUnread.id), tags.tagsOnSight(looked))
    }

    @Test
    fun `one thread's tags survive looking at another, and ending one thread's look leaves the others`() {
        val first = thread(Message(agent, "First."))
        val second = thread(Message(agent, "Second."))
        val tags = NewTags()
        val firstTags = tags.tagsOnSight(first)
        val secondTags = tags.tagsOnSight(second)
        first.markReadByUser()
        second.markReadByUser()

        assertEquals(firstTags, tags.tagsOnSight(first))
        tags.endLook(first.id)

        assertEquals(emptySet(), tags.tagsOnSight(first))
        assertEquals(secondTags, tags.tagsOnSight(second))
    }
}
