package dev.marginalis.plugin.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.border.Border

internal object MarginalisPalette {
    val ACCENT = JBColor(Color(0x35, 0x74, 0xF0), Color(0x35, 0x74, 0xF0))

    private val GITHUB_BLOCK = JBColor(Color(0xB0, 0xB7, 0xC3), Color(0x5A, 0x60, 0x6B))

    fun githubDashedBorder(): Border =
        BorderFactory.createDashedBorder(GITHUB_BLOCK, JBUI.scale(4).toFloat(), JBUI.scale(3).toFloat())
}
