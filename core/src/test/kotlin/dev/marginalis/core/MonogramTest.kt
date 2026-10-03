package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals

class MonogramTest {

    @Test
    fun `a monogram takes the first letters of the first two words`() {
        assertEquals("MF", Monogram.initials("Muhammad Farag"))
        assertEquals("MF", Monogram.initials("  muhammad   farag  ali "))
    }

    @Test
    fun `a single-word name gives one initial`() {
        assertEquals("C", Monogram.initials("Claude"))
    }

    @Test
    fun `hyphen, underscore and dot separate words`() {
        assertEquals("CB", Monogram.initials("claude-builder"))
        assertEquals("OC", Monogram.initials("octo_cat"))
        assertEquals("JD", Monogram.initials("j.doe"))
    }

    @Test
    fun `a bot suffix is ignored`() {
        assertEquals("D", Monogram.initials("dependabot[bot]"))
        assertEquals("GA", Monogram.initials("github-actions[bot]"))
    }

    @Test
    fun `separators like the middle dot are not words`() {
        assertEquals("CB", Monogram.initials("Claude · builder"))
        assertEquals("CB", Monogram.initials("Claude — builder"))
    }

    @Test
    fun `initials are upper-cased letters or digits, and a name with none falls back to a question mark`() {
        assertEquals("ÉZ", Monogram.initials("élodie zola"))
        assertEquals("2S", Monogram.initials("2pac shakur"))
        assertEquals("A", Monogram.initials("(alice)"))
        assertEquals("?", Monogram.initials("· — ·"))
        assertEquals("?", Monogram.initials(""))
    }
}
