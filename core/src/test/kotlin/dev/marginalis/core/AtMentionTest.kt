package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtMentionTest {

    @Test
    fun `an @ opening the text or following a space or newline starts a mention`() {
        assertTrue(AtMention.startsAt("@", 0))
        assertTrue(AtMention.startsAt("hey @", 4))
        assertTrue(AtMention.startsAt("line\n@", 5))
    }

    @Test
    fun `an @ inside a word is just a character — addresses and decorators stay typeable`() {
        assertFalse(AtMention.startsAt("me@", 2))
        assertFalse(AtMention.startsAt("(@", 1))
    }

    @Test
    fun `an @ inside a code fence is code`() {
        val text = "see\n```kotlin\n    @"

        assertFalse(AtMention.startsAt(text, text.length - 1))
    }

    @Test
    fun `an @ after a closed fence starts a mention again`() {
        val text = "```\ncode\n```\n@"

        assertTrue(AtMention.startsAt(text, text.length - 1))
    }

    @Test
    fun `only an @ starts a mention`() {
        assertFalse(AtMention.startsAt("a", 0))
        assertFalse(AtMention.startsAt("", 0))
    }
}
