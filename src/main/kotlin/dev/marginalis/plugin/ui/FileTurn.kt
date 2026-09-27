package dev.marginalis.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import dev.marginalis.core.ThreadStatus
import dev.marginalis.core.Turn
import dev.marginalis.plugin.store.MarginalisStore

/**
 * A file's turn signal, as every file-name surface shows it — the editor tab
 * and the Project view — so the two read identically by construction:
 *
 *   ●   an open thread where the agent spoke last: the user owes a reply
 *   ○   open threads, all waiting on the agent
 *
 * The tool window speaks the same grammar with counts and colors.
 */
object FileTurn {

    fun of(project: Project, file: VirtualFile): Turn? {
        val base = project.guessProjectDir() ?: return null
        val path = VfsUtilCore.getRelativePath(file, base) ?: return null
        return Turn.of(MarginalisStore.getInstance(project).threads.query(file = path, status = ThreadStatus.Kind.OPEN))
    }

    fun glyph(turn: Turn): String = when (turn) {
        Turn.USER -> "●"
        Turn.AGENT -> "○"
    }
}
