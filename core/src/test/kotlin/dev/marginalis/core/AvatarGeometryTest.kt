package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarGeometryTest {

    private val geometry = AvatarGeometry()

    @Test
    fun `the corner radius is a quarter of the tile`() {
        assertEquals(16.0, geometry.tile)
        assertEquals(4.0, geometry.radius)
        assertEquals(5.0, AvatarGeometry(scale = 1.25).radius)
    }

    @Test
    fun `a picture is drawn at the tile's size in device pixels`() {
        assertEquals(16, geometry.tilePixels(deviceScale = 1.0))
        assertEquals(32, geometry.tilePixels(deviceScale = 2.0))
        assertEquals(30, AvatarGeometry(scale = 1.25).tilePixels(deviceScale = 1.5))
        assertEquals(19, geometry.tilePixels(deviceScale = 1.17))
    }

    @Test
    fun `the ring sits just outside the tile, so the framed tile is the tile plus the ring on each side`() {
        assertEquals(1.0, geometry.ring)
        assertEquals(1.0, geometry.tileOrigin)
        assertEquals(0.5, geometry.ringOrigin)
        assertEquals(17.0, geometry.ringSize)
        assertEquals(4.5, geometry.ringRadius)
        assertEquals(0.0, geometry.ringOrigin - geometry.ring / 2)
        assertEquals(geometry.tile + 2 * geometry.ring, geometry.framed)
        assertEquals(geometry.framed, geometry.ringOrigin + geometry.ringSize + geometry.ring / 2)
    }

    @Test
    fun `the badge is 45 percent of the tile, centred near the bottom-right corner`() {
        assertEquals(7.2, geometry.badge, EPSILON)
        assertEquals(geometry.tileOrigin + geometry.tile - 0.3 * geometry.badge, geometry.badgeCentre, EPSILON)
        assertEquals(14.84, geometry.badgeCentre, EPSILON)
    }

    @Test
    fun `the badge carries its spark only while it is big enough to read`() {
        assertTrue(geometry.sparks)
        assertFalse(AvatarGeometry(scale = 0.5).sparks)
    }

    @Test
    fun `the badge's cut-out halo stays inside the icon's bounds`() {
        assertEquals(4.8, geometry.haloRadius, EPSILON)
        assertEquals(20, geometry.size)
        listOf(0.5, 1.0, 1.25, 1.5, 1.75, 2.0, 3.0).map(::AvatarGeometry).forEach { scaled ->
            assertTrue(scaled.badgeCentre + scaled.haloRadius <= scaled.size, "halo at ${scaled.tile}")
            assertTrue(scaled.framed <= scaled.size, "ring at ${scaled.tile}")
        }
    }

    @Test
    fun `a row lines up on the tile's centre, which sits above the icon's middle to leave room for the badge`() {
        assertEquals(9.0, geometry.tileCentre)
        assertEquals(0.45, geometry.tileCentreShare, EPSILON)
        val scaled = AvatarGeometry(scale = 1.5)
        assertEquals(scaled.tileCentre / scaled.size, scaled.tileCentreShare, EPSILON)
    }

    @Test
    fun `stacked faces overlap their framed tiles by 4px`() {
        assertEquals(14, geometry.stackStep)
        assertEquals(4.0, geometry.framed - geometry.stackStep)
        assertEquals(0, geometry.stackWidth(0))
        assertEquals(20, geometry.stackWidth(1))
        assertEquals(20 + 2 * 14, geometry.stackWidth(3))
        val scaled = AvatarGeometry(scale = 1.5)
        assertEquals(6.0, scaled.framed - scaled.stackStep)
        assertEquals(scaled.size + 2 * scaled.stackStep, scaled.stackWidth(3))
    }

    @Test
    fun `the icon's trailing padding is the space between the ring and the icon's edge`() {
        assertEquals(2.0, geometry.trailingPadding)
        assertEquals(geometry.size.toDouble(), geometry.framed + geometry.trailingPadding)
    }

    @Test
    fun `two initials set smaller than one so both fit the tile`() {
        assertEquals(0.44 * 16, geometry.initialsFontSize("MF"), EPSILON)
        assertEquals(0.58 * 16, geometry.initialsFontSize("C"), EPSILON)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
