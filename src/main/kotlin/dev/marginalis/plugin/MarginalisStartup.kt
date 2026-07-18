package dev.marginalis.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import dev.marginalis.plugin.store.CommentThread
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.store.ThreadStatus
import dev.marginalis.plugin.ui.ThreadGutterIconRenderer

/**
 * Keeps the collapsed state honest as threads change (from either party):
 *
 * - RESOLVED: the gutter icon is removed entirely. Resolution means the
 *   conclusion is consolidated into code (§3.1) — a lingering checkmark
 *   decorates a line that no longer has anything to say, and after edits it
 *   drifts onto unrelated lines. The thread itself stays in the store as a
 *   decision log; only the marker goes.
 * - OPEN with a dead or missing marker (reopen after resolve, or anchor
 *   deleted): re-attach at the last known line.
 * - Otherwise: re-set the renderer so status/unread changes repaint.
 */
class MarginalisStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        MarginalisStore.getInstance(project).addListener { thread ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                syncMarker(project, thread)
                // Targeted tab-title refresh so the ●/○ glyph tracks the
                // thread's state (MarginalisTabTitleProvider).
                project.guessProjectDir()?.findFileByRelativePath(thread.file)?.let { vFile ->
                    FileEditorManagerEx.getInstanceEx(project).updateFileName(vFile)
                }
            }
        }
    }

    private fun syncMarker(project: Project, thread: CommentThread) {
        val highlighter = thread.highlighter
        when {
            thread.status == ThreadStatus.RESOLVED -> {
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
                val line = thread.line.coerceIn(0, document.lineCount - 1)
                val markup = DocumentMarkupModel.forDocument(document, project, true)
                val fresh = markup.addLineHighlighter(line, HighlighterLayer.LAST, null)
                fresh.gutterIconRenderer = ThreadGutterIconRenderer(project, thread)
                thread.highlighter = fresh
            }

            else -> {
                if (highlighter != null && highlighter.isValid) {
                    highlighter.gutterIconRenderer = ThreadGutterIconRenderer(project, thread)
                }
            }
        }
    }
}
