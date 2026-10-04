package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectTabPrefsTest {

    @Test
    fun `the defaults are Live, expand on your move, read when expanded in front, and group relayed conversations`() {
        assertEquals(
            ProjectTabPrefs(
                list = ListWhileInFront.LIVE,
                expandOnYourMove = true,
                readWhen = ReadWhen.EXPANDED_IN_FRONT,
                groupRelayed = true,
            ),
            ProjectTabPrefs(),
        )
    }

    @Test
    fun `an unknown stored list choice falls back to Live`() {
        assertEquals(ListWhileInFront.LIVE, ListWhileInFront.fromStored("FROZEN"))
        assertEquals(ListWhileInFront.LIVE, ListWhileInFront.fromStored(""))
    }

    @Test
    fun `a stored list choice reads back as itself`() {
        ListWhileInFront.entries.forEach { assertEquals(it, ListWhileInFront.fromStored(it.stored)) }
    }

    @Test
    fun `an unknown stored read rule falls back to expanded while in front`() {
        assertEquals(ReadWhen.EXPANDED_IN_FRONT, ReadWhen.fromStored("ON_HOVER"))
        assertEquals(ReadWhen.EXPANDED_IN_FRONT, ReadWhen.fromStored(""))
    }

    @Test
    fun `a stored read rule reads back as itself`() {
        ReadWhen.entries.forEach { assertEquals(it, ReadWhen.fromStored(it.stored)) }
    }
}
