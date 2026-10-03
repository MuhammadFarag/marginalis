package dev.marginalis.plugin.avatars

import dev.marginalis.core.PictureFit
import java.awt.RenderingHints
import java.awt.image.BufferedImage

fun BufferedImage.scaledTo(side: Int): BufferedImage =
    PictureFit.halvings(from = width, to = side).fold(this, ::resized)

private fun resized(image: BufferedImage, side: Int): BufferedImage {
    val resized = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
    val g = resized.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(image, 0, 0, side, side, null)
    } finally {
        g.dispose()
    }
    return resized
}
