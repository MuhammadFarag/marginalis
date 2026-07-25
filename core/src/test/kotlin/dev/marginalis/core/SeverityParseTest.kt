package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SeverityParseTest {

    @Test
    fun `canonical vocabulary, case-insensitively`() {
        assertEquals(Severity.Parsed.Ok(Severity.BLOCKER), Severity.parse("blocker"))
        assertEquals(Severity.Parsed.Ok(Severity.BLOCKER), Severity.parse("BLOCKER"))
        assertEquals(Severity.Parsed.Ok(Severity.NIT), Severity.parse("nit"))
        assertEquals(Severity.Parsed.Ok(null), Severity.parse(null))
    }

    @Test
    fun `no aliases — legacy words are rejections that steer the agent, not synonyms`() {
        assertIs<Severity.Parsed.Invalid>(Severity.parse("high"))
        assertIs<Severity.Parsed.Invalid>(Severity.parse("medium"))
        assertIs<Severity.Parsed.Invalid>(Severity.parse("low"))
    }

    @Test
    fun `garbage is a teachable rejection, never a silently unmarked thread`() {
        assertIs<Severity.Parsed.Invalid>(Severity.parse("urgent"))
    }

    @Test
    fun `the lenient form swallows the unknown — persistence tolerance`() {
        assertEquals(Severity.BLOCKER, Severity.parseLenient("BLOCKER"))
        assertEquals(Severity.NIT, Severity.parseLenient("nit"))
        assertNull(Severity.parseLenient("urgent"))
        assertNull(Severity.parseLenient(null))
    }
}
