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
import dev.marginalis.plugin.store.CommentThread
import dev.marginalis.plugin.store.MarginalisPersistence
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.store.ThreadStatus
import dev.marginalis.plugin.ui.ThreadGutterIconRenderer
import kotlin.math.abs

/**
 * Project wiring:
 * 1. Rehydrate persisted threads (M2) and re-anchor OPEN ones by content —
 *    the file may have changed while the IDE was closed, so the persisted
 *    line is only a hint; no match within the search window means ORPHANED,
 *    never a guessed anchor (handover §7).
 * 2. Keep collapsed state honest on every change: markers dropped on
 *    resolve/delete, re-attached on reopen, renderers refreshed, tab-title
 *    glyphs updated.
 * 3. Persist on every change (small file, background thread).
 */
class MarginalisStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        val store = MarginalisStore.getInstance(project)

        store.addListener { thread ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                syncMarker(project, thread)
                // Targeted tab-title refresh so the ●/○ glyph tracks the
                // thread's state (MarginalisTabTitleProvider). Deliberately the
                // base-class API: FileEditorManagerEx.updateFileName is 2026.1+
                // and broke the 2025.2 floor in CI.
                project.guessProjectDir()?.findFileByRelativePath(thread.file)?.let { vFile ->
                    FileEditorManager.getInstance(project).updateFilePresentation(vFile)
                }
            }
            AppExecutorUtil.getAppExecutorService().execute {
                if (!project.isDisposed) MarginalisPersistence.save(project, store.all())
            }
        }

        val persisted = MarginalisPersistence.load(project)
        if (persisted.isNotEmpty()) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                for (thread in persisted) {
                    if (thread.status == ThreadStatus.OPEN) reanchor(project, thread)
                    store.addSilently(thread)
                }
                // One notification refreshes every UI surface after bulk load.
                persisted.lastOrNull()?.let { store.notifyChanged(it) }
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
        val found = findAnchorLine(document, thread.line, thread.anchorText)
        if (found == null) {
            thread.markOrphaned()
            return
        }
        thread.line = found
        attachMarker(project, thread, document)
    }

    private fun findAnchorLine(document: Document, nearLine: Int, anchorText: String, window: Int = 20): Int? {
        val expected = anchorText.trim()
        val candidates = ((nearLine - window)..(nearLine + window))
            .filter { it in 0 until document.lineCount }
            .sortedBy { abs(it - nearLine) }
        return candidates.firstOrNull { line ->
            val actual = document.getText(
                TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)),
            ).trim()
            if (expected.isEmpty()) actual.isEmpty() else actual == expected || actual.contains(expected)
        }
    }

    private fun attachMarker(project: Project, thread: CommentThread, document: Document) {
        val markup = DocumentMarkupModel.forDocument(document, project, true)
        val highlighter = markup.addLineHighlighter(thread.line, HighlighterLayer.LAST, null)
        highlighter.gutterIconRenderer = ThreadGutterIconRenderer(project, thread)
        thread.highlighter = highlighter
    }

    private fun syncMarker(project: Project, thread: CommentThread) {
        val store = MarginalisStore.getInstance(project)
        val highlighter = thread.highlighter
        when {
            // Deleted (Clear All): never resurrect a marker for it.
            store.byId(thread.id) == null || thread.status == ThreadStatus.RESOLVED -> {
                if (highlighter != null) {
                    if (highlighter.isValid) {
                        DocumentMarkupModel.forDocument(highlighter.document, project, false)
                            ?.removeHighlighter(highlighter)
                    }
                    thread.highlighter = null
                }
            }

            thread.status == ThreadStatus.OPEN && (highlighter == null || !highlighter.isValid) -> {
                val base = project.guessProjectDir() ?: return
                val vFile = base.findFileByRelativePath(thread.file) ?: return
                val document = FileDocumentManager.getInstance().getDocument(vFile) ?: return
                thread.line = thread.line.coerceIn(0, document.lineCount - 1)
                attachMarker(project, thread, document)
            }

            else -> {
                if (highlighter != null && highlighter.isValid) {
                    highlighter.gutterIconRenderer = ThreadGutterIconRenderer(project, thread)
                }
            }
        }
    }
}
