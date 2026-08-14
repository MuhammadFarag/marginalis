package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntentParseTest {

    @Test
    fun `the vocabulary is three words, case-insensitively`() {
        assertEquals(Intent.FINDING, ok("finding"))
        assertEquals(Intent.GUIDANCE, ok("GUIDANCE"))
        assertEquals(Intent.QUESTION, ok("Question"))
    }

    @Test
    fun `omitted is the ordinary comment, not a rejection`() {
        assertNull(ok(null))
    }

    @Test
    fun `a near miss is taught, never silently unmarked`() {
        // The words deliberately rejected in review: each would have been a
        // synonym for one of the three, and synonyms rot a closed vocabulary.
        for (raw in listOf("issue", "note", "todo", "decision", "praise", "suggestion", "")) {
            val parsed = Intent.parse(raw)
            assertIs<Intent.Parsed.Invalid>(parsed, "'$raw' must be rejected")
            assertTrue(parsed.reason.contains("finding"), "the rejection names the vocabulary")
            assertTrue(parsed.reason.contains("guidance") && parsed.reason.contains("question"))
        }
    }

    @Test
    fun `the lenient form swallows the unknown — persistence tolerance`() {
        // A file written by a newer vocabulary loads as unmarked rather than
        // failing every thread beside it.
        assertNull(Intent.parseLenient("epiphany"))
        assertEquals(Intent.FINDING, Intent.parseLenient("finding"))
    }

    private fun ok(raw: String?): Intent? = (Intent.parse(raw) as Intent.Parsed.Ok).intent
}
