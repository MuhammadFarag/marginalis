package dev.marginalis.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.text.StringUtil
import dev.marginalis.plugin.store.MarginNote
import javax.swing.Icon

/**
 * M0 gutter presence: an icon on the anchored line, body on hover.
 * Collapsed-state-only per handover §8 — no inlays yet (M1).
 */
class MarginNoteGutterIconRenderer(private val note: MarginNote) : GutterIconRenderer() {

    override fun getIcon(): Icon = AllIcons.General.Balloon

    override fun getTooltipText(): String =
        "<html><b>Claude</b><br/>${StringUtil.escapeXmlEntities(note.body).replace("\n", "<br/>")}</html>"

    override fun equals(other: Any?): Boolean =
        other is MarginNoteGutterIconRenderer && other.note.id == note.id

    override fun hashCode(): Int = note.id.hashCode()
}
