package dev.marginalis.plugin.ui

import dev.marginalis.core.Face
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

class ParticipantStackIcon(faces: List<Face>, surface: () -> Color) : Icon {

    private val avatars = faces.map { AvatarIcon(it, surface) }

    override fun getIconWidth(): Int = AvatarGeometries.current().stackWidth(avatars.size)

    override fun getIconHeight(): Int = if (avatars.isEmpty()) 0 else AvatarGeometries.current().size

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val step = AvatarGeometries.current().stackStep
        avatars.withIndex().reversed().forEach { (index, avatar) -> avatar.paintIcon(c, g, x + index * step, y) }
    }
}
