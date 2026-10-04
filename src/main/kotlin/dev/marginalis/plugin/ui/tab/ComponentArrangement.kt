package dev.marginalis.plugin.ui.tab

import javax.swing.JComponent

internal fun JComponent.arrange(children: List<JComponent>) {
    var changed = false
    children.forEachIndexed { index, child ->
        if (index < componentCount && getComponent(index) === child) return@forEachIndexed
        if (child.parent === this) setComponentZOrder(child, index) else add(child, index)
        changed = true
    }
    while (componentCount > children.size) {
        remove(componentCount - 1)
        changed = true
    }
    if (changed) {
        revalidate()
        repaint()
    }
}
