package dev.marginalis.plugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.ui.JBUI
import dev.marginalis.plugin.store.CommentThread
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
        val open = editor.getUserData(OPEN_INLAYS) ?: mutableMapOf<String, Pair<Inlay<*>, ThreadPanel>>()
            .also { editor.putUserData(OPEN_INLAYS, it) }

        open.remove(thread.id)?.let { (inlay, _) ->
            Disposer.dispose(inlay)
            return
        }

        // Fit within what the editor can actually show: visible width minus
        // room for the gutter/inlay x-offset, clamped to something readable.
        val visibleWidth = editor.scrollingModel.visibleArea.width
        val panelWidth = (visibleWidth - JBUI.scale(120))
            .coerceIn(JBUI.scale(360), JBUI.scale(800))
        val panel = ThreadPanel(project, thread, panelWidth) { close(editor, thread.id) }
        val line = thread.currentLine().coerceAtMost(editor.document.lineCount - 1)
        val offset = editor.document.getLineEndOffset(line.coerceAtLeast(0))
        val inlay = EditorEmbeddedComponentManager.getInstance().addComponent(
            editor as EditorEx,
            panel,
            EditorEmbeddedComponentManager.Properties(
                EditorEmbeddedComponentManager.ResizePolicy.none(),
                null,
                true, // relatesToPrecedingText
                false, // showAbove = false -> below the anchor line (handover §8)
                0,
                offset,
            ),
        ) ?: return
        open[thread.id] = inlay to panel
        installStoreListener(project, editor)
    }

    private fun close(editor: Editor, threadId: String) {
        val open = editor.getUserData(OPEN_INLAYS) ?: return
        open.remove(threadId)?.let { (inlay, _) -> Disposer.dispose(inlay) }
    }

    /**
     * One store listener per editor: refreshes any open panel when its thread
     * changes (agent replies land while the human is looking at the thread).
     */
    private fun installStoreListener(project: Project, editor: Editor) {
        if (editor.getUserData(LISTENER_INSTALLED) == true) return
        editor.putUserData(LISTENER_INSTALLED, true)
        MarginalisStore.getInstance(project).addListener { thread ->
            ApplicationManager.getApplication().invokeLater {
                if (editor.isDisposed) return@invokeLater
                editor.getUserData(OPEN_INLAYS)?.get(thread.id)?.second?.refresh()
            }
        }
    }
}
