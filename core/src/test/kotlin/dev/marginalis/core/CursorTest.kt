package dev.marginalis.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CursorTest {

    @Test
    fun `omitted means no cursor`() {
        assertEquals(Parsed.Ok(null), Cursor.parse("since", null, "pass one"))
    }

    @Test
    fun `an ISO-8601 instant is the cursor`() {
        assertEquals(
            Parsed.Ok(Instant.parse("2026-08-14T09:30:00Z")),
            Cursor.parse("since", "2026-08-14T09:30:00Z", "pass one"),
        )
    }

    @Test
    fun `anything else teaches the format and how to get a cursor`() {
        val invalid = assertIs<Parsed.Invalid>(Cursor.parse("updated_after", "yesterday", "pass back the newest 'updated_at'."))

        assertTrue(invalid.reason.startsWith("'updated_after' must be an ISO-8601 instant (e.g. 2026-08-14T09:30:00Z)"))
        assertTrue(invalid.reason.endsWith("pass back the newest 'updated_at'."))
    }
}
