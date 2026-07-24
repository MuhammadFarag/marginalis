package dev.marginalis.plugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.ui.JBUI
import dev.marginalis.core.CommentThread
import dev.marginalis.plugin.store.MarginalisStore

/**
 * Opens/closes the expanded thread view: a block inlay below the anchor line
 * hosting a real Swing panel (EditorEmbeddedComponentManager). Bookkeeping
 * lives in editor user data, so it dies with the editor.
 */
object ThreadInlayManager {

    private val OPEN_INLAYS = Key.create<MutableMap<String, Pair<Inlay<*>, ThreadPanel>>>("marginalis.open.inlays")
    private val LISTENER_INSTALLED = Key.create<Boolean>("marginalis.store.listener")

    /** Toggle the inlay for [thread] in [editor]. EDT only (gutter clicks arrive there). */
    fun toggle(project: Project, editor: Editor, thread: CommentThread) {
        val open = openInlays(editor)
        open.remove(thread.id)?.let { (inlay, _) ->
            Disposer.dispose(inlay)
            return
        }
        openPanel(project, editor, thread, ensureStored = {})
    }

    /** Open (never close) the panel — used by tool-window navigation. */
    fun open(project: Project, editor: Editor, thread: CommentThread) {
        openPanel(project, editor, thread, ensureStored = {})
    }

    /**
     * Open a panel for a thread that doesn't exist yet (human-initiated,
     * AddCommentAction). Nothing is stored or marked until the first message
     * is sent; closing an unsent draft leaves no trace.
     */
    fun openDraft(project: Project, editor: Editor, thread: CommentThread) {
        openPanel(project, editor, thread) {
            val store = MarginalisStore.getInstance(project)
            if (store.threads.byId(thread.id) == null) {
                MarginalisMarkers.attach(project, thread, editor.document)
                store.threads.add(thread)
            }
        }
    }

    private fun openInlays(editor: Editor): MutableMap<String, Pair<Inlay<*>, ThreadPanel>> =
        editor.getUserData(OPEN_INLAYS) ?: mutableMapOf<String, Pair<Inlay<*>, ThreadPanel>>()
            .also { editor.putUserData(OPEN_INLAYS, it) }

    private fun openPanel(project: Project, editor: Editor, thread: CommentThread, ensureStored: () -> Unit) {
        val open = openInlays(editor)
        if (open.containsKey(thread.id)) return

        // Fit within what the editor can actually show: visible width minus
        // room for the gutter/inlay x-offset, clamped to something readable.
        val visibleWidth = editor.scrollingModel.visibleArea.width
        val panelWidth = (visibleWidth - JBUI.scale(120))
            .coerceIn(JBUI.scale(360), JBUI.scale(800))
        val panel = ThreadPanel(project, thread, panelWidth, ensureStored) { close(editor, thread.id) }
        val line = MarginalisStore.getInstance(project).currentLine(thread)
            .coerceAtMost(editor.document.lineCount - 1)
        val offset = editor.document.getLineEndOffset(line.coerceAtLeast(0))
        val inlay = EditorEmbeddedComponentManager.getInstance().addComponent(
            editor as EditorEx,
            panel,
            EditorEmbeddedComponentManager.Properties(
                EditorEmbeddedComponentManager.ResizePolicy.none(),
                null,
                true, // relatesToPrecedingText
                false, // showAbove = false -> the panel unfolds below the anchor line
                0,
                offset,
            ),
        ) ?: return
        open[thread.id] = inlay to panel
        installStoreListener(project, editor)
        ApplicationManager.getApplication().invokeLater { panel.focusReply() }
    }

    private fun close(editor: Editor, threadId: String) {
        val open = editor.getUserData(OPEN_INLAYS) ?: return
        open.remove(threadId)?.let { (inlay, _) -> Disposer.dispose(inlay) }
    }

    /**
     * Dynamic-unload cleanup: dispose every open panel and clear our user
     * data from every editor. Editor user data outlives the plugin's
     * classloader — anything of ours left behind (panels, inlays, even the
     * stale Key values) pins the old classloader after a hot reload.
     */
    fun disposeAll() {
        for (editor in EditorFactory.getInstance().allEditors) {
            editor.getUserData(OPEN_INLAYS)?.values?.forEach { (inlay, _) -> Disposer.dispose(inlay) }
            editor.putUserData(OPEN_INLAYS, null)
            editor.putUserData(LISTENER_INSTALLED, null)
        }
    }

    /**
     * One store listener per editor: refreshes any open panel when its thread
     * changes (agent replies land while the human is looking at the thread),
     * and closes the panel when its thread is deleted (Clear All, remove) —
     * a panel for a thread that no longer exists is a ghost.
     */
    private fun installStoreListener(project: Project, editor: Editor) {
        if (editor.getUserData(LISTENER_INSTALLED) == true) return
        editor.putUserData(LISTENER_INSTALLED, true)
        val store = MarginalisStore.getInstance(project)
        store.threads.addListener { thread ->
            ApplicationManager.getApplication().invokeLater {
                if (editor.isDisposed) return@invokeLater
                if (store.threads.byId(thread.id) == null) {
                    close(editor, thread.id)
                } else {
                    editor.getUserData(OPEN_INLAYS)?.get(thread.id)?.second?.refresh()
                }
            }
        }
    }
}
