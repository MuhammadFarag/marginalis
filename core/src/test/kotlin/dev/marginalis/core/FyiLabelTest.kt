package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FyiLabelTest {

    private fun ok(raw: String?, intent: Intent? = Intent.FYI): String? = (FyiLabel.parse(raw, intent) as Parsed.Ok).value

    @Test
    fun `a label names the kind of fyi, lowercased`() {
        assertEquals("praise", ok("praise"))
        assertEquals("heads-up", ok("Heads-Up"))
        assertEquals("copied code", ok("copied code"))
        assertEquals("v2", ok("V2"))
    }

    @Test
    fun `no label is fine, on any intent`() {
        assertNull(ok(null))
        assertNull(ok(null, Intent.FINDING))
        assertNull(ok(null, intent = null))
    }

    @Test
    fun `a malformed label is taught with examples`() {
        val tooLong = "a".repeat(21)
        for (raw in listOf("", " praise", "praise ", "two  spaces", "a--b", "-lead", "trail-", "emoji ✨", "under_score", tooLong)) {
            val parsed = FyiLabel.parse(raw, Intent.FYI)
            assertIs<Parsed.Invalid>(parsed, "'$raw' must be rejected")
            assertTrue(
                listOf("'praise'", "'copied'", "'context'", "'heads-up'").all { it in parsed.reason },
                "the rejection shows examples",
            )
        }
    }

    @Test
    fun `twenty characters is the longest label`() {
        assertEquals("a".repeat(20), ok("a".repeat(20)))
    }

    @Test
    fun `a label belongs to fyi alone`() {
        for (intent in listOf(Intent.FINDING, Intent.GUIDANCE, Intent.QUESTION, null)) {
            val parsed = FyiLabel.parse("praise", intent)
            assertIs<Parsed.Invalid>(parsed)
            assertTrue(parsed.reason.contains("it goes with intent 'fyi' only"))
        }
    }

    @Test
    fun `a label that is already a word of the vocabulary is taught, not taken`() {
        for (raw in listOf("blocker", "NIT", "finding", "guidance", "question", "fyi")) {
            val parsed = FyiLabel.parse(raw, Intent.FYI)
            assertIs<Parsed.Invalid>(parsed, "'$raw' must be rejected")
            assertTrue(parsed.reason.contains("'heads-up'"), "the rejection shows examples")
        }
    }

    @Test
    fun `the rejection names the allowed characters plainly and echoes a long label only in part`() {
        val parsed = FyiLabel.parse("x".repeat(100), Intent.FYI)

        assertIs<Parsed.Invalid>(parsed)
        assertTrue(parsed.reason.contains("a–z and 0–9"))
        assertTrue(parsed.reason.contains("'${"x".repeat(40)}…'"))
        assertTrue(!parsed.reason.contains("x".repeat(41)))
    }
}
