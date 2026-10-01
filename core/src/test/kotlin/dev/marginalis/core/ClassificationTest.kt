package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClassificationTest {

    private fun reason(intent: String?, severity: String?, label: String?): String =
        (Classification.parse(intent = intent, severity = severity, label = label) as Parsed.Invalid).reason

    @Test
    fun `the three markings parse together`() {
        assertEquals(
            Classification(Intent.FYI, null, "praise"),
            (Classification.parse(intent = "fyi", severity = null, label = "Praise") as Parsed.Ok).value,
        )
        assertEquals(
            Classification(Intent.GUIDANCE, Severity.BLOCKER, null),
            (Classification.parse(intent = "guidance", severity = "blocker", label = null) as Parsed.Ok).value,
        )
        assertEquals(Classification(null, null, null), (Classification.parse(null, null, null) as Parsed.Ok).value)
    }

    @Test
    fun `an fyi carries no severity`() {
        for (severity in listOf("blocker", "nit")) {
            val reason = reason("fyi", severity, null)
            assertTrue(reason.contains("an fyi asks for nothing, so it carries no severity"))
            assertTrue(reason.contains("finding"))
        }
    }

    @Test
    fun `errors come in reading order — intent, then severity, then how they combine, then the label`() {
        assertTrue(reason("praise", "urgent", "x y z!").contains("invalid intent"))
        assertTrue(reason("fyi", "urgent", "x y z!").contains("invalid severity"))
        assertTrue(reason("fyi", "nit", "x y z!").contains("carries no severity"))
        assertTrue(reason("fyi", null, "x y z!").contains("invalid label"))
        assertTrue(reason("finding", null, "praise").contains("it goes with intent 'fyi' only"))
    }

    @Test
    fun `a thread cannot hold a label off an fyi, or a severity on one`() {
        val labelledFinding = runCatching { CommentThread("a.py", null, null, intent = Intent.FINDING, label = "praise") }
        val gatedFyi = runCatching { CommentThread("a.py", null, null, intent = Intent.FYI, severity = Severity.NIT) }

        assertIs<IllegalArgumentException>(labelledFinding.exceptionOrNull())
        assertIs<IllegalArgumentException>(gatedFyi.exceptionOrNull())
    }
}
