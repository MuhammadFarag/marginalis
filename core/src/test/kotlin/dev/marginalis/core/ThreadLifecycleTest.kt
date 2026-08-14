package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadLifecycleTest {

    private val agent = Author.Agent("Claude")
    private val user = Author.User("Muhammad")

    private fun thread() = CommentThread(file = "a.py", line = 3, anchorText = "def f():")

    @Test
    fun `threads are born open with no resolver`() {
        val t = thread()
        assertIs<ThreadStatus.Open>(t.status)
        assertNull(t.resolvedBy)
    }

    @Test
    fun `resolving carries the resolver inside the status`() {
        val t = thread()
        t.resolve(user)
        val status = t.status
        assertIs<ThreadStatus.Resolved>(status)
        assertEquals(user, status.by)
        assertEquals(user, t.resolvedBy)
    }

    @Test
    fun `reopening discards the resolver — no open thread with a resolver is representable`() {
        val t = thread()
        t.resolve(agent)
        t.reopen()
        assertIs<ThreadStatus.Open>(t.status)
        assertNull(t.resolvedBy)
    }

    @Test
    fun `orphan rescue moves the anchor and reopens, atomically`() {
        val t = thread()
        t.markOrphaned()
        t.rescueTo(7, "def g():")
        assertIs<ThreadStatus.Open>(t.status)
        assertEquals(7, t.line)
        assertEquals("def g():", t.anchorText)
    }

    @Test
    fun `a file-level thread has no anchor at all`() {
        val t = CommentThread("a.py", line = null, anchorText = null)
        assertTrue(t.isFileLevel)
        assertNull(t.line)
        assertNull(t.anchorText)
        assertIs<ThreadStatus.Open>(t.status)
    }

    @Test
    fun `half an anchor is not representable`() {
        assertFailsWith<IllegalArgumentException> { CommentThread("a.py", line = 3, anchorText = null) }
        assertFailsWith<IllegalArgumentException> { CommentThread("a.py", line = null, anchorText = "def f():") }
    }

    @Test
    fun `a project-level thread has neither file nor line`() {
        val t = CommentThread(file = null, line = null, anchorText = null)
        assertTrue(t.isProjectLevel)
        assertFalse(t.isFileLevel)
        assertNull(t.file)
        assertNull(t.line)
        assertIs<ThreadStatus.Open>(t.status)
    }

    @Test
    fun `a line without a file is not representable`() {
        assertFailsWith<IllegalArgumentException> {
            CommentThread(file = null, line = 3, anchorText = "def f():")
        }
    }

    @Test
    fun `a project-level thread is never re-anchored — there is nothing to lose`() {
        val t = CommentThread(file = null, line = null, anchorText = null)
        t.markOrphaned()
        assertFailsWith<IllegalStateException> { t.rescueTo(7, "def g():") }
    }

    @Test
    fun `a file-level thread may carry a segment — provenance, not an anchor`() {
        val sparkedBy = Segment("curr", prefix = "prev, ", suffix = " =")
        val t = CommentThread("a.py", line = null, anchorText = null, segment = sparkedBy)
        // The same words survive the widest widening too.
        assertEquals(sparkedBy, CommentThread(null, null, null, segment = sparkedBy).segment)
        // The words that started the thought are kept; the thought is still
        // about the whole file, with nothing to re-find.
        assertEquals(sparkedBy, t.segment)
        assertTrue(t.isFileLevel)
        assertNull(t.line)
    }

    @Test
    fun `a file-level thread is never re-anchored — it follows its file`() {
        val t = CommentThread("a.py", line = null, anchorText = null)
        t.markOrphaned()
        assertFailsWith<IllegalStateException> { t.rescueTo(7, "def g():") }
        assertTrue(t.isFileLevel)
        // Its rescue is the file coming back, which reopens it as it stands.
        t.reopen()
        assertIs<ThreadStatus.Open>(t.status)
    }

    @Test
    fun `live anchors don't move — rescue demands an orphan`() {
        val t = thread()
        assertFailsWith<IllegalStateException> { t.rescueTo(7, "elsewhere") }
        t.resolve(user)
        assertFailsWith<IllegalStateException> { t.rescueTo(7, "elsewhere") }
        assertEquals(3, t.line)
        assertEquals("def f():", t.anchorText)
    }

    @Test
    fun `agent messages are born seen by their author, user messages born unread`() {
        val t = thread()
        t.addMessage(Message(agent, "proposal"))
        t.addMessage(Message(user, "reply"))
        assertEquals(1, t.unreadCount())
        assertTrue(t.messages[0].seenByAnyAgent)
        assertFalse(t.messages[1].seenByAnyAgent)
    }

    @Test
    fun `read receipts are per agent — one agent's sweep leaves another's unread alone`() {
        val t = thread()
        t.addMessage(Message(user, "for everyone"))
        t.addMessage(Message(agent, "agent one speaking"))
        val one = agent.receiptKey
        val two = "agent-two"

        // Agent one reads everything; agent two hasn't looked yet.
        t.messages.forEach { it.markSeenBy(one) }
        assertEquals(0, t.unreadCountFor(one))
        assertEquals(2, t.unreadCountFor(two))
        // The human-facing count: someone consumed it all.
        assertEquals(0, t.unreadCount())

        t.messages.forEach { it.markSeenBy(two) }
        assertEquals(0, t.unreadCountFor(two))
    }

    @Test
    fun `turn state follows the last author`() {
        val t = thread()
        t.addMessage(Message(agent, "question"))
        assertTrue(t.awaitsUser())
        t.addMessage(Message(user, "answer"))
        assertFalse(t.awaitsUser())
    }

    @Test
    fun `empty thread awaits nobody`() {
        assertFalse(thread().awaitsUser())
    }

    @Test
    fun `message body is revisable — the read receipt is the boundary the UI enforces`() {
        val m = Message(user, "draft")
        m.body = "final"
        assertEquals("final", m.body)
        // Core stores state; refusing edits after any-agent-read is the
        // adapters' contract, verified here only as data.
        m.markSeenBy("someone")
        assertTrue(m.seenByAnyAgent)
    }

    @Test
    fun `store queries filter by file, status kind, and unread`() {
        val store = ThreadStore()
        val a = thread().also { it.addMessage(Message(user, "hi")) }
        val b = CommentThread("b.py", 1, "x = 1").also {
            it.addMessage(Message(agent, "note"))
            it.resolve(agent)
        }
        store.add(a)
        store.add(b)

        assertEquals(listOf(a), store.query(file = "a.py"))
        assertEquals(listOf(a), store.query(status = ThreadStatus.Kind.OPEN))
        assertEquals(listOf(b), store.query(status = ThreadStatus.Kind.RESOLVED))
        // From the authoring agent's view only its own words are seen; a
        // DIFFERENT agent would also find b unread — receipts are per agent.
        assertEquals(listOf(a), store.query(unreadFor = agent.receiptKey))
    }

    @Test
    fun `store change notifications fire for add, remove, and clear`() {
        val store = ThreadStore()
        val seen = mutableListOf<String>()
        store.addListener { seen.add(it.id) }
        val t = thread()
        store.add(t)
        store.remove(t.id)
        store.add(thread())
        store.clear()
        assertEquals(4, seen.size)
    }
}
