package dev.marginalis.plugin.ui

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.store.MarginalisStore

/**
 * Tab indicator for files with open margin threads. A glyph, not a color:
 * tab background colors collide with VCS/scope colors and refresh
 * unreliably, so the tab title carries the state instead —
 *
 *   name ●   an open thread where Claude spoke last: needs the human
 *   name ○   open threads, all waiting on Claude
 *
 * Same glyph grammar as the Marginalis tool window. Refresh is driven by
 * FileEditorManagerEx.updateFileName from the store listener.
 */
class MarginalisTabTitleProvider : EditorTabTitleProvider {

    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        val base = project.guessProjectDir() ?: return null
        val rel = VfsUtilCore.getRelativePath(file, base) ?: return null
        val open = MarginalisStore.getInstance(project).threads
            .query(file = rel, status = ThreadStatus.Kind.OPEN)
        if (open.isEmpty()) return null
        val needsUser = open.any { it.awaitsUser() }
        return "${file.name} ${if (needsUser) "●" else "○"}"
    }
}
