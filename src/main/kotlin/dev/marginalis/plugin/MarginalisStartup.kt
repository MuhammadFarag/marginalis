package dev.marginalis.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.AppExecutorUtil
import dev.marginalis.core.AnchorPolicy
import dev.marginalis.core.CommentThread
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.store.MarginalisPersistence
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.ThreadGutterIconRenderer

/**
 * Project wiring:
 * 1. Rehydrate persisted threads and re-anchor OPEN ones by content — the
 *    file may have changed while the IDE was closed, so the persisted line
 *    is only a hint; no match within the search window means ORPHANED,
 *    never a guessed anchor.
 * 2. Keep collapsed state honest on every change: markers dropped on
 *    resolve/delete, re-attached on reopen, renderers refreshed, tab-title
 *    glyphs updated.
 * 3. Persist on every change (small file, background thread).
 */
class MarginalisStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        val store = MarginalisStore.getInstance(project)

        store.threads.addListener { thread ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                syncMarker(project, thread)
                // Refresh this file's tab so the turn glyph tracks the change.
                // Deliberately the base-class API: FileEditorManagerEx's
                // variant is 2026.1+ and broke the 2025.2 floor in CI.
                project.guessProjectDir()?.findFileByRelativePath(thread.file)?.let { vFile ->
                    FileEditorManager.getInstance(project).updateFilePresentation(vFile)
                }
            }
            AppExecutorUtil.getAppExecutorService().execute {
                if (!project.isDisposed) {
                    MarginalisPersistence.save(project, store.threads.all())
                }
            }
        }

        val persisted = MarginalisPersistence.load(project)
        if (persisted.isNotEmpty()) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                for (thread in persisted) {
                    if (thread.status is ThreadStatus.Open) reanchor(project, thread)
                    store.threads.addSilently(thread)
                }
                // One notification refreshes every UI surface after bulk load.
                persisted.lastOrNull()?.let { store.threads.notifyChanged(it) }
            }
        }
    }

    /** Re-anchor a rehydrated OPEN thread by content; orphan on no match. EDT. */
    private fun reanchor(project: Project, thread: CommentThread) {
        val base = project.guessProjectDir()
        val vFile = base?.findFileByRelativePath(thread.file)
        val document = vFile?.let { FileDocumentManager.getInstance().getDocument(it) }
        if (document == null) {
            thread.markOrphaned()
            return
        }
        val found = AnchorPolicy.findAnchorLine(
            lineCount = document.lineCount,
            lineTextAt = { lineText(document, it) },
            nearLine = thread.line,
            anchorText = thread.anchorText,
        )
        if (found == null) {
            thread.markOrphaned()
            return
        }
        thread.line = found
        attachMarker(project, thread, document)
    }

    private fun lineText(document: Document, line: Int): String =
        document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))

    private fun attachMarker(project: Project, thread: CommentThread, document: Document) {
        val markup = DocumentMarkupModel.forDocument(document, project, true)
        val highlighter = markup.addLineHighlighter(thread.line, HighlighterLayer.LAST, null)
        highlighter.gutterIconRenderer = ThreadGutterIconRenderer(project, thread)
        MarginalisStore.getInstance(project).setMarker(thread, highlighter)
    }

    /**
     * One rule set for the collapsed state: deleted and resolved threads
     * carry no marker (a resolved thread's outcome is in the code — nothing
     * left to mark, and after edits a stale checkmark drifts onto unrelated
     * lines); open threads always have a live one; everything else just
     * refreshes its icon.
     */
    private fun syncMarker(project: Project, thread: CommentThread) {
        val store = MarginalisStore.getInstance(project)
        val marker = store.markerOf(thread)
        when {
            store.threads.byId(thread.id) == null || thread.status is ThreadStatus.Resolved -> {
                if (marker != null) {
                    if (marker.isValid) {
                        DocumentMarkupModel.forDocument(marker.document, project, false)
                            ?.removeHighlighter(marker)
                    }
                    store.removeMarker(thread)
                }
            }

            thread.status is ThreadStatus.Open && (marker == null || !marker.isValid) -> {
                val base = project.guessProjectDir() ?: return
                val vFile = base.findFileByRelativePath(thread.file) ?: return
                val document = FileDocumentManager.getInstance().getDocument(vFile) ?: return
                thread.line = thread.line.coerceIn(0, document.lineCount - 1)
                attachMarker(project, thread, document)
            }

            else -> {
                if (marker != null && marker.isValid) {
                    marker.gutterIconRenderer = ThreadGutterIconRenderer(project, thread)
                }
            }
        }
    }
}
