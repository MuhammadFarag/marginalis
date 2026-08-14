package dev.marginalis.core

import java.time.Instant
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
    fun `severity survives the trip and its absence stays absent`() {
        val blocker = CommentThread("a.py", 1, "x", severity = Severity.BLOCKER)
        val nit = CommentThread("b.py", 2, "y", severity = Severity.NIT)
        val plain = CommentThread("c.py", 3, "z")
        val decoded = ThreadsCodec.decode(ThreadsCodec.encode(listOf(blocker, nit, plain)))
        assertEquals(Severity.BLOCKER, decoded[0].severity)
        assertEquals(Severity.NIT, decoded[1].severity)
        assertEquals(null, decoded[2].severity)
    }

    @Test
    fun `unknown severity values load as unmarked, not as failure`() {
        val legacy = """
            {"version":1,"threads":[{
              "id":"t5","file":"a.py","line":3,"anchor_text":"x = 1","severity":"CRITICAL",
              "status":"OPEN","created_at":"2026-07-18T12:00:00Z","messages":[]
            }]}
        """.trimIndent()
        assertEquals(null, ThreadsCodec.decode(legacy).single().severity)
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
    fun `a file-level thread writes no anchor and reads back without one`() {
        val fileLevel = CommentThread("src/a.py", line = null, anchorText = null, severity = Severity.NIT, order = 1)
        fileLevel.addMessage(Message(Author.Agent("Claude", id = "claude-1"), "this module needs a README"))

        val encoded = ThreadsCodec.encode(listOf(fileLevel))
        assertFalse(encoded.contains("\"line\""))
        assertFalse(encoded.contains("\"anchor_text\""))

        val decoded = ThreadsCodec.decode(encoded).single()
        assertTrue(decoded.isFileLevel)
        assertEquals(null, decoded.line)
        assertEquals(null, decoded.anchorText)
        assertEquals(Severity.NIT, decoded.severity)
        assertEquals(1, decoded.order)
        assertEquals("this module needs a README", decoded.messages.single().body)
    }

    @Test
    fun `a file-level thread's provenance segment survives the trip without an anchor`() {
        val sparkedBy = Segment("curr", prefix = "prev, ", suffix = " =")
        val fileLevel = CommentThread("src/a.py", line = null, anchorText = null, segment = sparkedBy)

        val encoded = ThreadsCodec.encode(listOf(fileLevel))
        assertFalse(encoded.contains("\"line\""))
        assertTrue(encoded.contains("\"segment\""))

        val decoded = ThreadsCodec.decode(encoded).single()
        assertTrue(decoded.isFileLevel)
        assertEquals(sparkedBy, decoded.segment)
    }

    @Test
    fun `a project-level thread writes no file at all and reads back without one`() {
        val aboutTheProject = CommentThread(
            file = null, line = null, anchorText = null,
            order = 1, walkthrough = "A", severity = Severity.BLOCKER,
        )
        aboutTheProject.addMessage(Message(Author.User("Muhammad"), "we never settled on error handling"))

        val encoded = ThreadsCodec.encode(listOf(aboutTheProject))
        assertFalse(encoded.contains("\"file\""))
        assertFalse(encoded.contains("\"line\""))

        val decoded = ThreadsCodec.decode(encoded).single()
        assertTrue(decoded.isProjectLevel)
        assertFalse(decoded.isFileLevel)
        assertEquals(null, decoded.file)
        assertEquals(null, decoded.line)
        assertEquals(1, decoded.order)
        assertEquals("A", decoded.walkthrough)
        assertEquals(Severity.BLOCKER, decoded.severity)
    }

    @Test
    fun `a persisted line with no anchor text is still a line thread, not a file-level one`() {
        val legacy = """
            {"version":1,"threads":[{
              "id":"t6","file":"a.py","line":3,
              "status":"OPEN","created_at":"2026-07-18T12:00:00Z","messages":[]
            }]}
        """.trimIndent()
        val thread = ThreadsCodec.decode(legacy).single()
        assertFalse(thread.isFileLevel)
        assertEquals(3, thread.line)
        assertEquals("", thread.anchorText)
    }

    @Test
    fun `the cursor survives the trip untouched by rehydration`() {
        val t = CommentThread("a.py", 1, "x")
        t.addMessage(Message(Author.User("Muhammad"), "hello"))
        val moved = t.updatedAt

        val decoded = ThreadsCodec.decode(ThreadsCodec.encode(listOf(t))).single()
        assertEquals(moved, decoded.updatedAt)
        assertTrue(decoded.updatedAt >= decoded.createdAt)
    }

    @Test
    fun `pre-cursor files derive updated_at from what they do remember`() {
        val legacy = """
            {"version":1,"threads":[{
              "id":"t7","file":"a.py","line":3,"anchor_text":"x = 1",
              "status":"OPEN","created_at":"2026-07-18T12:00:00Z",
              "messages":[{"id":"m1","author":{"kind":"USER","name":"Muhammad"},
                "body":"hi","created_at":"2026-07-18T12:00:05Z","seen_by":[]}]
            },{
              "id":"t8","file":"a.py","line":9,"anchor_text":"y = 2",
              "status":"OPEN","created_at":"2026-07-18T12:00:00Z","messages":[]
            }]}
        """.trimIndent()
        val (withMessage, silent) = ThreadsCodec.decode(legacy)
        // Newest message is the best evidence of when it last moved…
        assertEquals(Instant.parse("2026-07-18T12:00:05Z"), withMessage.updatedAt)
        // …and with nothing said, its birth is all there is.
        assertEquals(Instant.parse("2026-07-18T12:00:00Z"), silent.updatedAt)
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
