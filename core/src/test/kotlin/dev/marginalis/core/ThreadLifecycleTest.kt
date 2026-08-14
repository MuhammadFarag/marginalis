package dev.marginalis.core

import java.time.Instant
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
    fun `updated_at moves with the conversation, not with reading it`() {
        val t = thread()
        val born = t.updatedAt
        assertEquals(t.createdAt, born)

        Thread.sleep(2)
        t.addMessage(Message(user, "something"))
        val afterMessage = t.updatedAt
        assertTrue(afterMessage > born, "a new message is a change")

        // Reading is not a change: a listing marks messages seen, and if that
        // bumped the cursor every sweep would return everything forever.
        t.messages.forEach { it.markSeenBy("claude") }
        assertEquals(afterMessage, t.updatedAt)

        // Neither is an anchor sliding as the file is edited.
        t.line = 99
        assertEquals(afterMessage, t.updatedAt)

        Thread.sleep(2)
        t.resolve(user)
        val afterResolve = t.updatedAt
        assertTrue(afterResolve > afterMessage, "resolving is a change")

        Thread.sleep(2)
        t.reopen()
        assertTrue(t.updatedAt > afterResolve, "so is reopening")
    }

    @Test
    fun `rescue and in-place message edits both count as changes`() {
        // The clock is only microseconds fine, and these mutations are
        // nanoseconds apart; the sleeps are about the test's ability to see
        // the difference, not about the semantics.
        val t = thread()
        t.addMessage(Message(user, "draft"))
        t.markOrphaned()
        val orphaned = t.updatedAt
        Thread.sleep(2)
        t.rescueTo(7, "def g():")
        assertTrue(t.updatedAt > orphaned, "a rescue is a change")

        val rescued = t.updatedAt
        Thread.sleep(2)
        t.messages.first().body = "revised"
        t.touch()
        assertTrue(t.updatedAt > rescued, "a revised message is a change the thread must report")
    }

    @Test
    fun `rehydration restores the cursor instead of rewriting history`() {
        val t = thread()
        val long_ago = Instant.parse("2026-01-01T00:00:00Z")
        t.addMessage(Message(user, "old news"))
        t.restoreUpdatedAt(long_ago)
        assertEquals(long_ago, t.updatedAt)
        // Status restored the same way — neither is a lifecycle event.
        t.restoreStatus(ThreadStatus.Orphaned)
        assertEquals(long_ago, t.updatedAt)
    }

    @Test
    fun `the sweep cursor returns what moved, and nothing when nothing did`() {
        val store = ThreadStore()
        val quiet = thread()
        val moved = CommentThread("b.py", 1, "x = 1")
        store.add(quiet)
        store.add(moved)
        val cursor = Instant.now()
        Thread.sleep(2)
        moved.addMessage(Message(user, "new"))

        assertEquals(listOf(moved), store.query(updatedAfter = cursor))
        // Handing back the newest value you saw is not a re-read of it.
        assertEquals(emptyList(), store.query(updatedAfter = moved.updatedAt))
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
    fun `store queries filter by intent, independently of severity`() {
        val store = ThreadStore()
        val guidance = CommentThread("a.py", 1, "x", intent = Intent.GUIDANCE)
        val guidanceBlocker = CommentThread("b.py", 1, "x", intent = Intent.GUIDANCE, severity = Severity.BLOCKER)
        val question = CommentThread("c.py", 1, "x", intent = Intent.QUESTION)
        val ordinary = CommentThread("d.py", 1, "x", severity = Severity.BLOCKER)
        listOf(guidance, guidanceBlocker, question, ordinary).forEach(store::add)

        // The motivating query: everything that tells me how to write this.
        assertEquals(listOf(guidance, guidanceBlocker), store.query(intent = Intent.GUIDANCE))
        assertEquals(listOf(question), store.query(intent = Intent.QUESTION))
        // Unfiltered still means unfiltered — an ordinary comment is not an intent.
        assertEquals(4, store.query().size)
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
