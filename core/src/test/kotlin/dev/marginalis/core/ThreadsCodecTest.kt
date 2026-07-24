package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ThreadsCodecTest {

    @Test
    fun `full round trip preserves everything`() {
        val agent = Author.Agent("Claude", id = "claude-1")
        val user = Author.User("Muhammad")
        val t = CommentThread("src/a.py", 7, "def f():", order = 2, walkthrough = "A")
        t.addMessage(Message(agent, "**bold** question"))
        t.addMessage(Message(user, "answer"))
        t.resolve(user)

        val decoded = ThreadsCodec.decode(ThreadsCodec.encode(listOf(t))).single()

        assertEquals(t.id, decoded.id)
        assertEquals("src/a.py", decoded.file)
        assertEquals(7, decoded.line)
        assertEquals("def f():", decoded.anchorText)
        assertEquals(2, decoded.order)
        assertEquals("A", decoded.walkthrough)
        assertEquals(t.createdAt, decoded.createdAt)
        val status = decoded.status
        assertIs<ThreadStatus.Resolved>(status)
        assertEquals(user, status.by)

        assertEquals(2, decoded.messages.size)
        val (m1, m2) = decoded.messages
        assertEquals(agent, m1.author)
        assertEquals("**bold** question", m1.body)
        assertEquals(setOf("claude-1"), m1.seenBy)
        assertEquals(user, m2.author)
        assertFalse(m2.seenByAnyAgent)
    }

    @Test
    fun `pre-rename files with kind HUMAN load as User`() {
        val legacy = """
            {"version":1,"threads":[{
              "id":"t1","file":"a.py","line":3,"anchor_text":"x = 1",
              "status":"OPEN","created_at":"2026-07-18T12:00:00Z",
              "messages":[{"id":"m1","author":{"kind":"HUMAN","name":"Muhammad"},
                "body":"hello","created_at":"2026-07-18T12:00:01Z","seen_by_agent":false}]
            }]}
        """.trimIndent()
        val thread = ThreadsCodec.decode(legacy).single()
        val author = thread.messages.single().author
        assertIs<Author.User>(author)
        assertEquals("Muhammad", author.displayName)
    }

    @Test
    fun `pre-multi-agent seen_by_agent bit maps to the anonymous Agent key`() {
        val legacy = """
            {"version":1,"threads":[{
              "id":"t3","file":"a.py","line":3,"anchor_text":"x = 1",
              "status":"OPEN","created_at":"2026-07-18T12:00:00Z",
              "messages":[{"id":"m1","author":{"kind":"USER","name":"Muhammad"},
                "body":"read","created_at":"2026-07-18T12:00:01Z","seen_by_agent":true},
               {"id":"m2","author":{"kind":"USER","name":"Muhammad"},
                "body":"unread","created_at":"2026-07-18T12:00:02Z","seen_by_agent":false}]
            }]}
        """.trimIndent()
        val messages = ThreadsCodec.decode(legacy).single().messages
        assertEquals(setOf("Agent"), messages[0].seenBy)
        assertFalse(messages[1].seenByAnyAgent)
    }

    @Test
    fun `pre-rename files with tour key load as walkthrough`() {
        val legacy = """
            {"version":1,"threads":[{
              "id":"t2","file":"a.py","line":3,"anchor_text":"x = 1","order":1,"tour":"B",
              "status":"OPEN","created_at":"2026-07-18T12:00:00Z","messages":[]
            }]}
        """.trimIndent()
        assertEquals("B", ThreadsCodec.decode(legacy).single().walkthrough)
    }

    @Test
    fun `orphaned and open statuses survive the trip`() {
        val open = CommentThread("a.py", 1, "x")
        val orphaned = CommentThread("b.py", 2, "y").also { it.markOrphaned() }
        val decoded = ThreadsCodec.decode(ThreadsCodec.encode(listOf(open, orphaned)))
        assertIs<ThreadStatus.Open>(decoded[0].status)
        assertIs<ThreadStatus.Orphaned>(decoded[1].status)
    }

    @Test
    fun `segment survives the trip and its absence stays absent`() {
        val spanned = CommentThread("a.py", 1, "prev, curr = 0, 1", segment = Segment("curr", prefix = "prev, ", suffix = " ="))
        val plain = CommentThread("b.py", 2, "y")
        val decoded = ThreadsCodec.decode(ThreadsCodec.encode(listOf(spanned, plain)))
        assertEquals(Segment("curr", prefix = "prev, ", suffix = " ="), decoded[0].segment)
        assertEquals(null, decoded[1].segment)
    }

    @Test
    fun `pre-segment files load as whole-line threads`() {
        val legacy = """
            {"version":1,"threads":[{
              "id":"t4","file":"a.py","line":3,"anchor_text":"x = 1",
              "status":"OPEN","created_at":"2026-07-18T12:00:00Z","messages":[]
            }]}
        """.trimIndent()
        assertEquals(null, ThreadsCodec.decode(legacy).single().segment)
    }

    @Test
    fun `agent identity survives the trip`() {
        val t = CommentThread("a.py", 1, "x")
        t.addMessage(Message(Author.Agent("Some Other Agent", id = "soa-42"), "hi"))
        val decoded = ThreadsCodec.decode(ThreadsCodec.encode(listOf(t))).single()
        val author = decoded.messages.single().author
        assertIs<Author.Agent>(author)
        assertEquals("soa-42", author.id)
    }
}
