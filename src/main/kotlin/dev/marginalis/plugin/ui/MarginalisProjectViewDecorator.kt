package dev.marginalis.plugin.ui

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator

/**
 * The turn glyph beside file names in the Project view — the same one the
 * editor tab shows ([FileTurn]), for files that aren't open.
 *
 * Carried in the location string rather than the colored text: rewriting the
 * colored text would override the VCS status color that the file name
 * already wears. Files only — folders stay quiet; the tool window groups by
 * directory for the collapsed-tree case.
 */
class MarginalisProjectViewDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val project = node.project ?: return
        val file = node.virtualFile?.takeUnless { it.isDirectory } ?: return
        val turn = FileTurn.of(project, file) ?: return
        data.locationString = listOfNotNull(data.locationString?.takeIf { it.isNotBlank() }, FileTurn.glyph(turn))
            .joinToString("  ")
    }
}
