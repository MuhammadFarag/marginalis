package dev.marginalis.plugin.store

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import dev.marginalis.core.CommentThread
import dev.marginalis.core.ThreadStatus
import dev.marginalis.core.ThreadStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Project adapter around the pure [ThreadStore]: adds what only the IDE side
 * knows — the live gutter markers. A RangeMarker tracks edits for free while
 * the document lives, so the marker is authoritative for a thread's line
 * whenever it's valid; the core thread carries the last known good line for
 * everything else (persistence, re-anchoring, display without an editor).
 */
@Service(Service.Level.PROJECT)
class MarginalisStore {

    val threads = ThreadStore()

    /**
     * Unsent composer text per thread, so closing a panel mid-thought (one
     * Esc away) loses nothing: reopen and the words are back. Deliberately
     * in-memory — a draft is a thought in progress, not a record.
     */
    val drafts = ConcurrentHashMap<String, String>()

    private val markers = ConcurrentHashMap<String, RangeHighlighter>()

    fun markerOf(thread: CommentThread): RangeHighlighter? = markers[thread.id]

    fun setMarker(thread: CommentThread, highlighter: RangeHighlighter) {
        markers[thread.id] = highlighter
    }

    fun removeMarker(thread: CommentThread): RangeHighlighter? = markers.remove(thread.id)

    /**
     * Current anchor line (0-based): the live marker when valid, else the
     * last known good. Self-repairs the thread's line — and flips an open
     * thread to orphaned when its anchored range was deleted.
     */
    fun currentLine(thread: CommentThread): Int {
        val marker = markers[thread.id]
        if (marker != null) {
            if (marker.isValid) {
                thread.line = marker.document.getLineNumber(marker.startOffset)
            } else if (thread.status is ThreadStatus.Open) {
                thread.markOrphaned()
            }
        }
        return thread.line
    }

    /** Refresh every thread's line from its marker (before persisting or listing). */
    fun syncLines() {
        threads.all().forEach { currentLine(it) }
    }

    companion object {
        fun getInstance(project: Project): MarginalisStore = project.service()
    }
}
