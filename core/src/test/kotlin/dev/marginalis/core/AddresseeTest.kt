package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddresseeTest {

    @Test
    fun `an agent is addressed by its author_id`() {
        assertEquals(Addressee.Agent("claude-builder"), ok("claude-builder"))
        assertEquals("claude-builder", Addressee.Agent("claude-builder").wire)
    }

    @Test
    fun `the user is addressed as user, case-insensitively`() {
        assertEquals(Addressee.User, ok("user"))
        assertEquals(Addressee.User, ok("User"))
        assertEquals("user", Addressee.User.wire)
    }

    @Test
    fun `surrounding whitespace is not part of the addressee`() {
        assertEquals(Addressee.Agent("claude-review"), ok(" claude-review "))
        assertEquals(Addressee.User, ok(" user\n"))
    }

    @Test
    fun `omitted is a broadcast, not a rejection`() {
        assertNull(ok(null))
    }

    @Test
    fun `a blank addressee is taught, never silently broadcast`() {
        for (raw in listOf("", "  ")) {
            val parsed = Addressee.parse(raw)
            assertIs<Parsed.Invalid>(parsed, "'$raw' must be rejected")
            assertTrue(parsed.reason.contains("'user'") && parsed.reason.contains("comment_identities"))
        }
    }

    @Test
    fun `the lenient form reads a blank as unaddressed — persistence tolerance`() {
        assertNull(Addressee.parseLenient(""))
        assertEquals(Addressee.User, Addressee.parseLenient("user"))
    }

    private fun ok(raw: String?): Addressee? = (Addressee.parse(raw) as Parsed.Ok).value
}
