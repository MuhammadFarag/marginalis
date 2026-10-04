package dev.marginalis.plugin.ui

import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import dev.marginalis.core.Face
import dev.marginalis.core.PresenceGeometry
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import javax.swing.Icon

internal object PresenceGeometries {
    @Volatile private var cached = PresenceGeometry()

    fun current(): PresenceGeometry {
        val scale = JBUIScale.scale(1f).toDouble()
        return cached.takeIf { it.scale == scale } ?: PresenceGeometry(scale).also { cached = it }
    }
}

class PresenceIcon private constructor(
    private val face: Face,
    private val oneShot: Boolean,
    private val spinnerFrame: Int?,
    private val surface: () -> Color,
) : Icon {

    private val avatar = AvatarIcon(face, surface)

    override fun getIconWidth(): Int = PresenceGeometries.current().size

    override fun getIconHeight(): Int = PresenceGeometries.current().size

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val geometry = PresenceGeometries.current()
        val g2 = g.create() as Graphics2D
        try {
            g2.translate(x.toDouble(), y.toDouble())
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            paintAvatar(c, g2, geometry)
            spinnerFrame?.let { paintSpinner(g2, geometry, it) }
            when {
                spinnerFrame == null -> paintDot(g2, geometry, LISTENING)
                oneShot -> paintDot(g2, geometry, AuthorColors.of(face.key))
            }
        } finally {
            g2.dispose()
        }
    }

    private fun paintAvatar(c: Component?, g: Graphics2D, geometry: PresenceGeometry) {
        val avatarGraphics = g.create() as Graphics2D
        try {
            avatarGraphics.translate(geometry.avatarOffset, geometry.avatarOffset)
            avatar.paintIcon(c, avatarGraphics, 0, 0)
        } finally {
            avatarGraphics.dispose()
        }
    }

    private fun paintSpinner(g: Graphics2D, geometry: PresenceGeometry, frame: Int) {
        g.color = AuthorColors.of(face.key)
        g.stroke = BasicStroke(geometry.arcStroke.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val start = 90.0 - PresenceGeometry.Spinner.startAngle(frame)
        g.draw(
            with(geometry) {
                Arc2D.Double(arcOrigin, arcOrigin, arcSize, arcSize, start, -PresenceGeometry.Spinner.sweep, Arc2D.OPEN)
            },
        )
    }

    private fun paintDot(g: Graphics2D, geometry: PresenceGeometry, colour: Color) {
        val radius = geometry.dotRadius
        g.color = surface()
        g.fill(circle(geometry, radius + geometry.dotHalo))
        g.color = colour
        if (oneShot) {
            g.stroke = BasicStroke(geometry.arcStroke.toFloat())
            g.draw(circle(geometry, radius - geometry.arcStroke / 2))
        } else {
            g.fill(circle(geometry, radius))
        }
    }

    private fun circle(geometry: PresenceGeometry, radius: Double) =
        Ellipse2D.Double(geometry.dotCentreX - radius, geometry.dotCentreY - radius, 2 * radius, 2 * radius)

    companion object {
        private val LISTENING = JBColor(0x2E9E45, 0x57C46B)

        fun listening(face: Face, oneShot: Boolean, surface: () -> Color): Icon =
            PresenceIcon(face, oneShot, spinnerFrame = null, surface)

        fun working(face: Face, oneShot: Boolean, surface: () -> Color): Icon = AnimatedIcon(
            PresenceGeometry.Spinner.frameDelayMillis,
            *Array(PresenceGeometry.Spinner.frames) { frame -> PresenceIcon(face, oneShot, frame, surface) },
        )
    }
}
