package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReferenceTest {

    private val user = Author.User("Muhammad")

    private fun thread(id: String, vararg messageIds: String) =
        CommentThread(file = "a.kt", line = 0, anchorText = "x", id = id).also { t ->
            messageIds.forEach { t.addMessage(Message(user, "said", id = it)) }
        }

    private fun parsed(text: String): Reference = (Reference.parse(text) as Parsed.Ok).value!!

    @Test
    fun `a reference is mg colon and the first eight characters of the id`() {
        assertEquals("mg:3d4770ad", Reference.of("3d4770ad-1b2c-4d5e-8f90-123456789abc").toString())
    }

    @Test
    fun `parsing reads back what was copied, in any case, at any longer length`() {
        assertEquals("mg:3d4770ad", parsed("mg:3d4770ad").toString())
        assertEquals("mg:3d4770ad", parsed("MG:3D4770AD").toString())
        assertEquals("mg:3d4770ad-1b2c", parsed("mg:3d4770ad-1b2c").toString())
    }

    @Test
    fun `no reference given is no reference asked for`() {
        assertEquals(Parsed.Ok(null), Reference.parse(null))
    }

    @Test
    fun `anything else is a teaching refusal that shows the format`() {
        for (bad in listOf("3d4770ad", "mg:3d47", "mg:zzzzzzzz", "mg:", "")) {
            val refusal = assertIs<Parsed.Invalid>(Reference.parse(bad), bad)
            assertTrue("mg:3d4770ad" in refusal.reason, refusal.reason)
        }
    }

    @Test
    fun `a thread prefix resolves to the thread, naming no message`() {
        val target = thread("3d4770ad-0000-0000-0000-000000000000", "aaaaaaaa-0000-0000-0000-000000000000")

        val found = assertIs<Resolution.Found>(parsed("mg:3d4770ad").resolveIn(listOf(thread("bbbbbbbb-0"), target)))

        assertSame(target, found.referent.thread)
        assertNull(found.referent.message)
    }

    @Test
    fun `a message prefix resolves to its thread, naming the message`() {
        val target = thread("3d4770ad-0000", "11111111-0000", "5e6f7a8b-0000")

        val found = assertIs<Resolution.Found>(parsed("mg:5e6f7a8b").resolveIn(listOf(target)))

        assertSame(target, found.referent.thread)
        assertEquals("5e6f7a8b-0000", found.referent.message?.id)
    }

    @Test
    fun `a prefix shared by two ids is ambiguous, listing both and saying how to narrow`() {
        val one = thread("3d4770ad-1111")
        val other = thread("99999999-0000", "3d4770ad-2222")

        val ambiguous = assertIs<Resolution.Ambiguous>(parsed("mg:3d4770ad").resolveIn(listOf(one, other)))

        assertEquals(listOf("3d4770ad-1111", "3d4770ad-2222"), ambiguous.candidates.map { it.message?.id ?: it.thread.id })
        assertTrue("mg:3d4770ad" in ambiguous.reason && "longer" in ambiguous.reason, ambiguous.reason)
        assertTrue(ambiguous.reason.startsWith(ambiguous.summary), ambiguous.summary)
    }

    @Test
    fun `a longer prefix settles what the short one could not`() {
        val one = thread("3d4770ad-1111")
        val other = thread("3d4770ad-2222")

        val found = assertIs<Resolution.Found>(parsed("mg:3d4770ad-2222").resolveIn(listOf(one, other)))

        assertSame(other, found.referent.thread)
    }

    @Test
    fun `a prefix nothing starts with is unknown`() {
        assertIs<Resolution.Unknown>(parsed("mg:3d4770ad").resolveIn(listOf(thread("bbbbbbbb-0", "cccccccc-0"))))
    }

    @Test
    fun `a candidate's own full reference is the unambiguous one`() {
        assertEquals("mg:3d4770ad-1111", Referent(thread("3d4770ad-1111"), null).fullReference.toString())
        val withMessage = thread("99999999-0000", "3d4770ad-2222")
        assertEquals("mg:3d4770ad-2222", Referent(withMessage, withMessage.messages.single()).fullReference.toString())
    }

    @Test
    fun `references in rendered prose become links to themselves`() {
        assertEquals(
            "<p>as I said in <a href=\"mg:3d4770ad\">mg:3d4770ad</a>, and (<a href=\"mg:5e6f7a8b\">mg:5e6f7a8b</a>).</p>",
            Reference.linkify("<p>as I said in mg:3d4770ad, and (mg:5e6f7a8b).</p>"),
        )
    }

    @Test
    fun `references in code or already inside a link stay as they are`() {
        val untouched = listOf(
            "<p>run <code>mg:3d4770ad</code></p>",
            "<pre><code>see mg:3d4770ad\n</code></pre>",
            "<p><a href=\"mg:3d4770ad\">mg:3d4770ad</a></p>",
            "<p><a href=\"https://x\">see mg:3d4770ad</a></p>",
        )
        for (html in untouched) assertEquals(html, Reference.linkify(html))
    }

    @Test
    fun `only a whole token is a reference`() {
        for (html in listOf("<p>img:3d4770ad</p>", "<p>mg:3d4770adzz</p>", "<p>mg:3d47</p>")) {
            assertEquals(html, Reference.linkify(html))
        }
    }

    @Test
    fun `prose after a code span links again`() {
        assertEquals(
            "<p><code>x</code> then <a href=\"mg:3d4770ad\">mg:3d4770ad</a></p>",
            Reference.linkify("<p><code>x</code> then mg:3d4770ad</p>"),
        )
    }
}
