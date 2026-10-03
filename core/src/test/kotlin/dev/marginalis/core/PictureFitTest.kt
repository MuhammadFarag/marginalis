package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PictureFitTest {

    @Test
    fun `a landscape image is cropped to its centred square`() {
        val fit = PictureFit.of(width = 300, height = 100, largest = 128)

        assertEquals(PictureFit.Square(x = 100, y = 0, side = 100), fit.crop)
    }

    @Test
    fun `a portrait image is cropped to its centred square`() {
        assertEquals(PictureFit.Square(x = 0, y = 25, side = 80), PictureFit.of(width = 80, height = 131, largest = 128).crop)
        assertEquals(PictureFit.Square(x = 0, y = 0, side = 64), PictureFit.of(width = 64, height = 64, largest = 128).crop)
    }

    @Test
    fun `a large picture is scaled down to the stored size, and a small one is never scaled up`() {
        assertEquals(128, PictureFit.of(width = 4032, height = 3024, largest = 128).side)
        assertEquals(128, PictureFit.of(width = 128, height = 200, largest = 128).side)
        assertEquals(40, PictureFit.of(width = 40, height = 90, largest = 128).side)
    }

    @Test
    fun `a picture that is already small is decoded whole`() {
        assertEquals(1, PictureFit.subsampling(width = 256, height = 256, largest = 128))
        assertEquals(1, PictureFit.subsampling(width = 40, height = 90, largest = 128))
    }

    @Test
    fun `a big photo is decoded at a fraction of its size that still leaves twice the stored side`() {
        assertEquals(11, PictureFit.subsampling(width = 4032, height = 3024, largest = 128))
        assertEquals(2, PictureFit.subsampling(width = 600, height = 512, largest = 128))
    }

    @Test
    fun `a long thin picture is subsampled until it fits the decoding budget`() {
        val step = PictureFit.subsampling(width = 1_000_000, height = 300, largest = 128)

        assertEquals(5, step)
    }

    @Test
    fun `a picture shrinks by halves, landing exactly on the target side`() {
        assertEquals(listOf(64, 32, 16), PictureFit.halvings(from = 128, to = 16))
        assertEquals(listOf(64, 40), PictureFit.halvings(from = 128, to = 40))
        assertEquals(listOf(32), PictureFit.halvings(from = 64, to = 32))
        assertEquals(listOf(128), PictureFit.halvings(from = 200, to = 128))
    }

    @Test
    fun `a picture already at the target side is left alone, and a smaller one is scaled up in one step`() {
        assertEquals(emptyList<Int>(), PictureFit.halvings(from = 32, to = 32))
        assertEquals(listOf(32), PictureFit.halvings(from = 20, to = 32))
    }
}
