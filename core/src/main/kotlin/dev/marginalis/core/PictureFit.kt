package dev.marginalis.core

import kotlin.math.max
import kotlin.math.min

class PictureFit private constructor(val crop: Square, val side: Int) {

    data class Square(val x: Int, val y: Int, val side: Int)

    companion object {
        private const val DECODED_PIXEL_BUDGET = 4096L * 4096L

        fun of(width: Int, height: Int, largest: Int): PictureFit {
            val cropSide = min(width, height)
            return PictureFit(Square((width - cropSide) / 2, (height - cropSide) / 2, cropSide), min(cropSide, largest))
        }

        fun subsampling(width: Int, height: Int, largest: Int): Int {
            var step = max(1, min(width, height) / (largest * 2))
            while (decodedPixels(width, height, step) > DECODED_PIXEL_BUDGET) step++
            return step
        }

        fun halvings(from: Int, to: Int): List<Int> =
            generateSequence(from) { side -> if (side == to) null else max(side / 2, to) }.drop(1).toList()

        private fun decodedPixels(width: Int, height: Int, step: Int): Long =
            Math.ceilDiv(width, step).toLong() * Math.ceilDiv(height, step)
    }
}
