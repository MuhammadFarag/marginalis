package dev.marginalis.plugin.ui

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Tab indicator for files with open margin threads (glyphs in [FileTurn]).
 * A glyph, not a color: tab background colors collide with VCS/scope colors
 * and refresh unreliably, so the tab title carries the state instead.
 * Refresh is driven by FileEditorManager.updateFilePresentation from the
 * store listener.
 */
class MarginalisTabTitleProvider : EditorTabTitleProvider {

    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? =
        FileTurn.of(project, file)?.let { "${file.name} ${FileTurn.glyph(it)}" }
}
