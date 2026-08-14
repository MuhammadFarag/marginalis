package dev.marginalis.plugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import dev.marginalis.core.CommentThread
import dev.marginalis.plugin.store.MarginalisStore

/**
 * Where a thread about the project unfolds. Every other thread has a place
 * in the code to open beside — a line, or the top of its file; this one has
 * none, so it gets a window of its own rather than borrowing some innocent
 * file's margin and pretending to be about it.
 *
 * The panel inside is the same one the editor hosts, so the conversation,
 * the composer and the step buttons all behave identically.
 */
object ProjectThreadPopup {

    /** Read and reply: an existing thread, opened from the tool window or a walk. */
    fun open(project: Project, thread: CommentThread) = show(project, thread) {}

    /**
     * A thread being started: nothing is stored until the first message is
     * sent, so an abandoned draft leaves no trace — the same bargain the
     * line and file gestures make.
     */
    fun openDraft(project: Project, thread: CommentThread) = show(project, thread) {
        val store = MarginalisStore.getInstance(project)
        if (store.threads.byId(thread.id) == null) store.threads.add(thread)
    }

    private fun show(project: Project, thread: CommentThread, ensureStored: () -> Unit) {
        var popup: JBPopup? = null
        val panel = ThreadPanel(project, editor = null, thread = thread, ensureStored = ensureStored) {
            popup?.cancel()
        }
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel)
            .setTitle(project.name)
            .setRequestFocus(true)
            .setMovable(true)
            .setResizable(true)
            .setCancelOnClickOutside(false)
            .createPopup()
        popup.showCenteredInCurrentWindow(project)
        ApplicationManager.getApplication().invokeLater { panel.focusDefault() }
    }
}
