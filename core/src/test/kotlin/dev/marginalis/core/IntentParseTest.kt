package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntentParseTest {

    @Test
    fun `the vocabulary is four words, case-insensitively`() {
        assertEquals(Intent.FINDING, ok("finding"))
        assertEquals(Intent.GUIDANCE, ok("GUIDANCE"))
        assertEquals(Intent.QUESTION, ok("Question"))
        assertEquals(Intent.FYI, ok("fyi"))
    }

    @Test
    fun `omitted is the ordinary comment, not a rejection`() {
        assertNull(ok(null))
    }

    @Test
    fun `a near miss is taught, never silently unmarked`() {
        for (raw in listOf("issue", "note", "todo", "decision", "praise", "suggestion", "")) {
            val parsed = Intent.parse(raw)
            assertIs<Parsed.Invalid>(parsed, "'$raw' must be rejected")
            assertTrue(parsed.reason.contains("finding"), "the rejection names the vocabulary")
            assertTrue(parsed.reason.contains("guidance") && parsed.reason.contains("question"))
            assertTrue(parsed.reason.contains("'fyi' (nothing is owed"), "fyi is taught with its meaning")
        }
    }

    @Test
    fun `the lenient form swallows the unknown — persistence tolerance`() {
        assertNull(Intent.parseLenient("epiphany"))
        assertEquals(Intent.FINDING, Intent.parseLenient("finding"))
    }

    private fun ok(raw: String?): Intent? = (Intent.parse(raw) as Parsed.Ok).value
}
