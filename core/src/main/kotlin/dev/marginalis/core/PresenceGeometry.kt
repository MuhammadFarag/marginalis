package dev.marginalis.core

import kotlin.math.ceil
import kotlin.math.max

class PresenceGeometry(val scale: Double = 1.0) {
    val avatar: AvatarGeometry = AvatarGeometry(scale)
    val arcStroke: Double = ARC_STROKE * scale
    private val arcGap: Double = ARC_GAP * scale
    val avatarOffset: Double = max(0.0, arcGap + arcStroke / 2 - avatar.tileOrigin)
    val arcOrigin: Double = avatarOffset + avatar.tileOrigin - arcGap
    val arcSize: Double = avatar.tile + 2 * arcGap
    val dotRadius: Double = DOT * scale / 2
    val dotHalo: Double = DOT_HALO * scale
    private val dotInset: Double = DOT_INSET_SHARE * DOT * scale
    val dotCentreX: Double = avatarOffset + avatar.tileOrigin + avatar.tile - dotInset
    val dotCentreY: Double = avatarOffset + avatar.tileOrigin + dotInset
    val size: Int = ceil(
        maxOf(arcOrigin + arcSize + arcStroke / 2, avatarOffset + avatar.size, dotCentreX + dotRadius + dotHalo),
    ).toInt()

    object Spinner {
        val frames: Int = 12
        val frameDelayMillis: Int = 1000 / frames
        val sweep: Double = 270.0

        fun startAngle(frame: Int): Double = frame * 360.0 / frames
    }

    private companion object {
        const val ARC_STROKE = 1.5
        const val ARC_GAP = 4.0
        const val DOT = 7.0
        const val DOT_HALO = 1.5
        const val DOT_INSET_SHARE = 0.3
    }
}
