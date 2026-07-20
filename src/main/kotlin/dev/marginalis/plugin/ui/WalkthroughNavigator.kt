package dev.marginalis.plugin.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import dev.marginalis.core.CommentThread
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.store.MarginalisStore

/**
 * Step-by-step walking for the thread panel's navigation buttons, computed
 * from the store so a panel can walk without the tool window being open.
 * Same semantics as the tool window's walk: a walkthrough step walks its
 * own walkthrough in step order (never bleeding into a neighboring
 * walkthrough); an unordered thread walks every open thread in
 * directory-tree order (directories before files at each level, then by
 * line) — the order the tool window displays.
 */
object WalkthroughNavigator {

    /** The walk containing [thread], and its position in it (-1 = not a member, e.g. resolved). */
    fun walkFrom(project: Project, thread: CommentThread): Pair<List<CommentThread>, Int> {
        val open = MarginalisStore.getInstance(project).threads.all()
            .filter { it.status is ThreadStatus.Open }
        val walk = if (thread.order != null) {
            open.filter { it.order != null && (it.walkthrough ?: "") == (thread.walkthrough ?: "") }
                .sortedWith(compareBy({ it.order }, { it.createdAt }))
        } else {
            open.sortedWith(
                Comparator<CommentThread> { a, b -> pathOrder(a.file, b.file) }
                    .thenComparingInt { it.line },
            )
        }
        return walk to walk.indexOfFirst { it.id == thread.id }
    }

    /**
     * The fixed denominator for a step's (n/total). A label alone can't
     * identify one walkthrough — every unlabeled walkthrough ever run
     * shares "" — so the cohort is same-label ordered threads created
     * at-or-after the earliest still-open step: finished walkthroughs
     * predate that and drop out; steps resolved mid-walk (created
     * together) stay counted. Null when [thread] isn't an ordered step or
     * its walkthrough has no open steps.
     */
    fun stableTotal(project: Project, thread: CommentThread): Int? {
        if (thread.order == null) return null
        val label = thread.walkthrough ?: ""
        val sameLabel = MarginalisStore.getInstance(project).threads.all()
            .filter { it.order != null && (it.walkthrough ?: "") == label }
        val earliestOpen = sameLabel.filter { it.status is ThreadStatus.Open }
            .minOfOrNull { it.createdAt } ?: return null
        return sameLabel.filter { it.createdAt >= earliestOpen }.maxOf { it.order!! }
    }

    /** Open the thread's file at its live line and pop its panel — the double-click behavior. */
    fun navigateTo(project: Project, thread: CommentThread) {
        val base = project.guessProjectDir() ?: return
        val vFile = base.findFileByRelativePath(thread.file) ?: return
        OpenFileDescriptor(project, vFile, MarginalisStore.getInstance(project).currentLine(thread), 0).navigate(true)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        ThreadInlayManager.open(project, editor, thread)
    }

    /**
     * Directory-tree order for project-relative paths: at each level all
     * directories sort before all files, mirroring how the tool window's
     * trie renders — so both walks visit files in the same sequence.
     */
    private fun pathOrder(a: String, b: String): Int {
        val pa = a.split('/')
        val pb = b.split('/')
        for (i in 0 until minOf(pa.size, pb.size)) {
            val aIsDir = i < pa.size - 1
            val bIsDir = i < pb.size - 1
            if (aIsDir != bIsDir) return if (aIsDir) -1 else 1
            val byName = pa[i].compareTo(pb[i])
            if (byName != 0) return byName
        }
        return pa.size - pb.size
    }
}
