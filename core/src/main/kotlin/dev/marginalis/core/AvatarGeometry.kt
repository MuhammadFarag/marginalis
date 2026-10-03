package dev.marginalis.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class AvatarGeometry(val scale: Double = 1.0) {
    val tile: Double = TILE * scale
    val radius: Double = tile * CORNER_SHARE
    val ring: Double = RING * scale
    val tileOrigin: Double = ring
    val tileCentre: Double = tileOrigin + tile / 2
    val ringOrigin: Double = tileOrigin - ring / 2
    val ringSize: Double = tile + ring
    val ringRadius: Double = radius + ring / 2
    val framed: Double = tile + 2 * ring
    val badge: Double = tile * BADGE_SHARE
    val badgeCentre: Double = tileOrigin + tile - BADGE_INSET_SHARE * badge
    val sparks: Boolean = badge >= SMALLEST_SPARKING_BADGE
    val haloRadius: Double = badge / 2 + HALO_GAP * scale
    val size: Int = ceil(max(framed, badgeCentre + haloRadius)).toInt()
    val tileCentreShare: Double = tileCentre / size
    val trailingPadding: Double = size - framed
    val stackStep: Int = (framed - STACK_OVERLAP * scale).roundToInt()

    fun stackWidth(faces: Int): Int = if (faces <= 0) 0 else size + (faces - 1) * stackStep

    fun tilePixels(deviceScale: Double): Int = ceil(tile * deviceScale).toInt()

    fun initialsFontSize(initials: String): Double =
        tile * if (initials.length > 1) TWO_INITIALS_SHARE else ONE_INITIAL_SHARE

    private companion object {
        const val TILE = 16.0
        const val RING = 1.0
        const val CORNER_SHARE = 0.25
        const val BADGE_SHARE = 0.45
        const val BADGE_INSET_SHARE = 0.3
        const val SMALLEST_SPARKING_BADGE = 6.0
        const val HALO_GAP = 1.2
        const val STACK_OVERLAP = 4.0
        const val TWO_INITIALS_SHARE = 0.44
        const val ONE_INITIAL_SHARE = 0.58
    }
}
