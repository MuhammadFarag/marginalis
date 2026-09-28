package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParsedTest {

    @Test
    fun `an accepted value is returned as is, even when it is null`() {
        assertEquals("x", Parsed.Ok("x").getOrElse { error("not rejected") })
        assertNull(Parsed.Ok<String?>(null).getOrElse { error("not rejected") })
    }

    @Test
    fun `a rejection hands its reason to the fallback`() {
        val parsed: Parsed<String> = Parsed.Invalid("bad")
        assertEquals("fallback for bad", parsed.getOrElse { "fallback for $it" })
    }
}
