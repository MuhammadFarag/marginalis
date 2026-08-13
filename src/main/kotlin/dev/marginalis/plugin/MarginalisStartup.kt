package dev.marginalis.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import dev.marginalis.core.AnchorPolicy
import dev.marginalis.core.CommentThread
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.store.MarginalisPersistence
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.MarginalisMarkers

/**
 * Project wiring:
 * 1. Rehydrate persisted threads, each by the rule its anchor implies (see
 *    [rehydrate]) — the files may have changed while the IDE was closed, so
 *    a persisted line is only a hint; no match within the search window
 *    means ORPHANED, never a guessed anchor.
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
                    rehydrate(project, thread)
                    store.threads.addSilently(thread)
                }
                // Attach assigns solo icons; group shared lines per file.
                persisted.map { it.file }.distinct().forEach { MarginalisMarkers.refreshIcons(project, it) }
                // One notification refreshes every UI surface after bulk load.
                persisted.lastOrNull()?.let { store.threads.notifyChanged(it) }
            }
        }
    }

    /**
     * Put a persisted thread back where it belongs, by the rule its anchor
     * implies. A file-level thread's only anchor is the path: it orphans
     * when the file is gone and comes back by itself when the path exists
     * again — nothing was lost, so nothing needs rescuing. A line thread
     * re-anchors by content while it is open; an orphaned one stays
     * orphaned, because moving a line anchor is the agent's call
     * (comment_reanchor), not a guess made at startup. EDT.
     */
    private fun rehydrate(project: Project, thread: CommentThread) {
        val vFile = project.guessProjectDir()?.findFileByRelativePath(thread.file)
        if (thread.isFileLevel) {
            when {
                vFile == null -> thread.markOrphaned()
                thread.status is ThreadStatus.Orphaned -> thread.reopen()
            }
            return
        }
        if (thread.status !is ThreadStatus.Open) return
        reanchor(project, thread, vFile)
    }

    /**
     * Re-anchor a rehydrated OPEN line thread by content; orphan on no match.
     * The ladder lives in AnchorPolicy: a segment that re-finds its quote
     * spans it again, a reworded span degrades to its line, and only a
     * vanished line orphans. EDT.
     */
    private fun reanchor(project: Project, thread: CommentThread, vFile: VirtualFile?) {
        val document = vFile?.let { FileDocumentManager.getInstance().getDocument(it) }
        if (document == null) {
            thread.markOrphaned()
            return
        }
        val found = AnchorPolicy.findAnchor(
            lineCount = document.lineCount,
            lineTextAt = { lineText(document, it) },
            nearLine = thread.line ?: 0,
            anchorText = thread.anchorText ?: "",
            segment = thread.segment,
        )
        if (found == null) {
            thread.markOrphaned()
            return
        }
        thread.line = found.line
        MarginalisMarkers.attach(project, thread, document)
    }

    private fun lineText(document: Document, line: Int): String =
        document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))

    /**
     * One rule set for the collapsed state: deleted and resolved threads
     * carry no marker (a resolved thread's outcome is in the code — nothing
     * left to mark, and after edits a stale checkmark drifts onto unrelated
     * lines); open threads always have a live one. Every outcome ends in an
     * icon refresh for the file — with several threads on one line the
     * combined icon's owner may just have changed.
     */
    private fun syncMarker(project: Project, thread: CommentThread) {
        val store = MarginalisStore.getInstance(project)
        val marker = store.markerOf(thread)
        when {
            store.threads.byId(thread.id) == null || thread.status is ThreadStatus.Resolved -> {
                if (store.threads.byId(thread.id) == null) store.drafts.remove(thread.id)
                if (marker != null) {
                    if (marker.isValid) {
                        DocumentMarkupModel.forDocument(marker.document, project, false)
                            ?.removeHighlighter(marker)
                    }
                    store.removeMarker(thread)
                }
            }

            // File-level threads are deliberately markerless — nothing to sync.
            thread.isFileLevel -> {}

            thread.status is ThreadStatus.Open && (marker == null || !marker.isValid) -> {
                val base = project.guessProjectDir() ?: return
                val vFile = base.findFileByRelativePath(thread.file) ?: return
                val document = FileDocumentManager.getInstance().getDocument(vFile) ?: return
                MarginalisMarkers.attach(project, thread, document)
            }
        }
        MarginalisMarkers.refreshIcons(project, thread.file)
    }
}
