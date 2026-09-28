package dev.marginalis.core

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WaitTimeoutTest {

    @Test
    fun `omitted means the generous default of an hour`() {
        assertEquals(Duration.ofHours(1), parsed(null))
    }

    @Test
    fun `seconds are taken as given up to the cap`() {
        assertEquals(Duration.ofSeconds(90), parsed("90"))
        assertEquals(Duration.ZERO, parsed("0"))
        assertEquals(WaitTimeout.CAP, parsed(WaitTimeout.CAP.seconds.toString()))
    }

    @Test
    fun `anything longer than the cap is clamped to it`() {
        assertEquals(Duration.ofHours(4), WaitTimeout.CAP)
        assertEquals(WaitTimeout.CAP, parsed("86400"))
        assertEquals(WaitTimeout.CAP, parsed("99999999999999999999"))
    }

    @Test
    fun `garbage is taught, never silently defaulted`() {
        for (raw in listOf("", "soon", "-1", "1.5", "1h")) {
            val parsed = WaitTimeout.parse(raw)
            assertIs<Parsed.Invalid>(parsed, "'$raw' must be rejected")
            assertTrue(parsed.reason.contains("seconds"))
        }
    }

    private fun parsed(raw: String?): Duration = (WaitTimeout.parse(raw) as Parsed.Ok).value
}
