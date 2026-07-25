package dev.marginalis.plugin.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Walkthrough
import dev.marginalis.plugin.store.MarginalisStore

/**
 * Step-by-step walking for the thread panel's navigation buttons, computed
 * from the store so a panel can walk without the tool window being open.
 * The walking rules themselves (membership, order, stable totals) are
 * core's [Walkthrough]; this object binds them to a project's store and
 * owns the one genuinely-editor concern, [navigateTo].
 */
object WalkthroughNavigator {

    /** The walk containing [thread], and its position in it (-1 = not a member, e.g. resolved). */
    fun walkFrom(project: Project, thread: CommentThread): Pair<List<CommentThread>, Int> =
        Walkthrough.walkFrom(MarginalisStore.getInstance(project).threads.all(), thread)

    /** The fixed denominator for a step's (n/total); see [Walkthrough.stableTotal]. */
    fun stableTotal(project: Project, thread: CommentThread): Int? =
        Walkthrough.stableTotal(MarginalisStore.getInstance(project).threads.all(), thread)

    /** Open the thread's file at its live line and pop its panel — the double-click behavior. */
    fun navigateTo(project: Project, thread: CommentThread) {
        val base = project.guessProjectDir() ?: return
        val vFile = base.findFileByRelativePath(thread.file) ?: return
        OpenFileDescriptor(project, vFile, MarginalisStore.getInstance(project).currentLine(thread), 0).navigate(true)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        ThreadInlayManager.open(project, editor, thread)
    }
}
