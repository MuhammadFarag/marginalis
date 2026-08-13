package dev.marginalis.plugin.ui

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.ui.JBColor
import dev.marginalis.core.AnchorPolicy
import dev.marginalis.core.CommentThread
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.store.MarginalisStore
import java.awt.Color

/**
 * The one place markers are made — creation (agent add, human draft),
 * rehydration, and reopen all attach through here, so the segment ladder
 * and the gutter-icon rules cannot drift apart between call sites.
 *
 * A thread with a resolvable segment gets a tinted EXACT_RANGE highlighter
 * on its span; a segment that no longer matches degrades to a plain line
 * highlighter (the ladder's middle rung — visible in the gutter, no tint).
 * All methods run on the EDT.
 */
object MarginalisMarkers {

    /**
     * Soft span tint, sitting just above the caret-row layer so search
     * results and diagnostics still paint over it.
     */
    private val SPAN_TINT = TextAttributes().apply {
        backgroundColor = JBColor(Color(0xF3, 0xE4, 0xF6), Color(0x43, 0x32, 0x49))
    }

    /**
     * Attach the right highlighter for [thread] to [document], replacing any
     * existing marker. Whole-line threads attach at `thread.line` exactly as
     * ever; segment threads run the quote ladder near the hint and update
     * `thread.line` to where the span actually landed. File-level threads
     * mark nothing: no line is the subject, so no line wears a badge.
     */
    fun attach(project: Project, thread: CommentThread, document: Document) {
        val hinted = thread.line ?: return
        val store = MarginalisStore.getInstance(project)
        store.removeMarker(thread)?.let { old ->
            if (old.isValid) {
                DocumentMarkupModel.forDocument(old.document, project, false)?.removeHighlighter(old)
            }
        }
        val markup = DocumentMarkupModel.forDocument(document, project, true)
        var line = hinted.coerceIn(0, document.lineCount - 1)
        thread.line = line

        val segment = thread.segment
        val span = segment?.let {
            AnchorPolicy.findAnchor(
                lineCount = document.lineCount,
                lineTextAt = { candidate -> lineText(document, candidate) },
                nearLine = line,
                anchorText = thread.anchorText ?: "",
                segment = it,
            ) as? AnchorPolicy.Anchor.Span
        }
        val highlighter = if (span != null) {
            line = span.line
            thread.line = line
            val lineStart = document.getLineStartOffset(span.line)
            markup.addRangeHighlighter(
                lineStart + span.start,
                lineStart + span.endExclusive,
                HighlighterLayer.CARET_ROW + 1,
                SPAN_TINT,
                HighlighterTargetArea.EXACT_RANGE,
            )
        } else {
            markup.addLineHighlighter(line, HighlighterLayer.LAST, null)
        }
        highlighter.gutterIconRenderer = ThreadGutterIconRenderer(project, listOf(thread))
        store.setMarker(thread, highlighter)
    }

    /**
     * Re-assign gutter icons for one file: threads sharing a line get a
     * single combined icon on the earliest thread's marker (click opens a
     * chooser), the rest carry only their span tint. Solo threads keep
     * their own icon. Runs after every thread change — cheap, N is small.
     */
    fun refreshIcons(project: Project, file: String) {
        refreshFileGlyph(project, file)
        val store = MarginalisStore.getInstance(project)
        val markers = store.threads.all()
            .filter { it.file == file && it.status !is ThreadStatus.Resolved }
            .mapNotNull { thread -> store.markerOf(thread)?.takeIf { it.isValid }?.let { thread to it } }
        for (group in markers.groupBy { (_, m) -> m.document.getLineNumber(m.startOffset) }.values) {
            val threads = group.sortedBy { (t, _) -> t.createdAt }
            threads.forEachIndexed { i, (_, marker) ->
                marker.gutterIconRenderer =
                    if (i == 0) ThreadGutterIconRenderer(project, threads.map { it.first }) else null
            }
        }
    }

    /**
     * The one glyph a file's open file-level threads get: a page icon in the
     * gutter beside line 1, where the panel unfolds. It is display only —
     * kept out of the thread→marker registry precisely so nothing reads a
     * line off it and nothing orphans when line 1 changes. A file-level
     * thread's fate is its file's, and only its file's.
     *
     * Rebuilt from scratch on every change to this file's threads; the
     * document is loaded only when there is actually a glyph to show.
     */
    private fun refreshFileGlyph(project: Project, file: String) {
        val store = MarginalisStore.getInstance(project)
        store.removeFileGlyph(file)?.let { old ->
            if (old.isValid) {
                DocumentMarkupModel.forDocument(old.document, project, false)?.removeHighlighter(old)
            }
        }
        val threads = store.threads.all()
            .filter { it.file == file && it.isFileLevel && it.status is ThreadStatus.Open }
        if (threads.isEmpty()) return
        val vFile = project.guessProjectDir()?.findFileByRelativePath(file) ?: return
        val document = FileDocumentManager.getInstance().getDocument(vFile) ?: return
        val glyph = DocumentMarkupModel.forDocument(document, project, true)
            .addLineHighlighter(0, HighlighterLayer.LAST, null)
        glyph.gutterIconRenderer = ThreadGutterIconRenderer(project, threads)
        store.setFileGlyph(file, glyph)
    }

    private fun lineText(document: Document, line: Int): String =
        document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
}
