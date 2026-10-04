package dev.marginalis.core

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresenceGeometryTest {

    private val scales = listOf(0.5, 1.0, 1.25, 1.5, 1.75, 2.0, 3.0)

    @Test
    fun `the status dot is 7px with a 1,5px halo, near the tile's top-right corner`() {
        val geometry = PresenceGeometry()
        val tileRight = geometry.avatarOffset + geometry.avatar.tileOrigin + geometry.avatar.tile
        val tileTop = geometry.avatarOffset + geometry.avatar.tileOrigin

        assertEquals(3.5, geometry.dotRadius, EPSILON)
        assertEquals(1.5, geometry.dotHalo, EPSILON)
        assertTrue(geometry.dotCentreX in (tileRight - geometry.dotRadius)..tileRight)
        assertTrue(geometry.dotCentreY in tileTop..(tileTop + geometry.dotRadius))
    }

    @Test
    fun `the status dot sits clear of the agent badge at every scale`() {
        scales.map(::PresenceGeometry).forEach { scaled ->
            val badge = scaled.avatarOffset + scaled.avatar.badgeCentre
            val apart = hypot(badge - scaled.dotCentreX, badge - scaled.dotCentreY)
            assertTrue(apart >= scaled.dotRadius + scaled.dotHalo + scaled.avatar.haloRadius, "at scale ${scaled.scale}")
        }
    }

    @Test
    fun `the spinner is a 1,5px arc 4px outside the tile`() {
        val geometry = PresenceGeometry()

        assertEquals(1.5, geometry.arcStroke, EPSILON)
        assertEquals(geometry.avatarOffset + geometry.avatar.tileOrigin - 4.0, geometry.arcOrigin, EPSILON)
        assertEquals(geometry.avatar.tile + 8.0, geometry.arcSize, EPSILON)
    }

    @Test
    fun `the icon is one size whether listening or working, so the toolbar never shifts`() {
        assertEquals(26, PresenceGeometry().size)
        scales.map(::PresenceGeometry).forEach { scaled ->
            val dotReach = scaled.dotRadius + scaled.dotHalo
            val arcReach = scaled.arcStroke / 2
            val name = "at scale ${scaled.scale}"
            assertTrue(scaled.arcOrigin - arcReach >= 0 && scaled.arcOrigin + scaled.arcSize + arcReach <= scaled.size, name)
            assertTrue(scaled.dotCentreY - dotReach >= 0 && scaled.dotCentreX + dotReach <= scaled.size, name)
            assertTrue(scaled.avatarOffset >= 0 && scaled.avatarOffset + scaled.avatar.size <= scaled.size, name)
        }
    }

    @Test
    fun `the spinner turns once a second, its frames evenly spaced around the avatar`() {
        val spinner = PresenceGeometry.Spinner

        assertEquals(1000.0, (spinner.frames * spinner.frameDelayMillis).toDouble(), 1000.0 / spinner.frames)
        assertEquals(0.0, spinner.startAngle(0), EPSILON)
        assertEquals(360.0 / spinner.frames, spinner.startAngle(1), EPSILON)
        assertEquals(360.0 - 360.0 / spinner.frames, spinner.startAngle(spinner.frames - 1), EPSILON)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
