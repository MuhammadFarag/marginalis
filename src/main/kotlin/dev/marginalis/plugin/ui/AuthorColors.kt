package dev.marginalis.plugin.ui

import com.intellij.ui.JBColor
import dev.marginalis.core.Addressee
import dev.marginalis.core.FaceKey

object AuthorColors {
    val USER = JBColor(0x1565C0, 0x90CAF9)

    /** User blue is deliberately absent from the palette. */
    val PALETTE = arrayOf(
        JBColor(0x9C27B0, 0xCE93D8),
        JBColor(0x00796B, 0x80CBC4),
        JBColor(0xE65100, 0xFFB74D),
        JBColor(0xC2185B, 0xF48FB1),
        JBColor(0x2E7D32, 0xA5D6A7),
        JBColor(0x5D4037, 0xBCAAA4),
    )

    fun of(key: FaceKey): JBColor = key.paletteIndex(PALETTE.size)?.let { PALETTE[it] } ?: USER

    fun of(to: Addressee): JBColor = when (to) {
        Addressee.User -> USER
        is Addressee.Agent -> of(FaceKey.Agent(to.key))
    }
}
