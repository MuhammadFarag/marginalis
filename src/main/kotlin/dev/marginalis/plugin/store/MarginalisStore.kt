package dev.marginalis.plugin.store

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import dev.marginalis.core.Addressee
import dev.marginalis.core.FileTurns
import dev.marginalis.core.CommentThread
import dev.marginalis.core.HandBack
import dev.marginalis.core.ThreadStatus
import dev.marginalis.core.ThreadStore
import dev.marginalis.core.ThreadsCodec
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class MarginalisStore(private val project: Project) : Disposable {

    val threads = ThreadStore()

    val fileTurns = FileTurns()

    val handBack = HandBack(hasAwaiting = threads::hasAwaiting)

    fun recordHandBack() {
        handBack.record()
        AppExecutorUtil.getAppExecutorService().execute {
            if (!project.isDisposed) MarginalisPersistence.save(project, snapshot())
        }
    }

    fun snapshot() = ThreadsCodec.Document(threads.all(), handBack.lastAt)

    override fun dispose() {
        handBack.releaseAll()
    }

    // Deliberately not persisted: a draft is a thought in progress, not a record.
    val drafts = ConcurrentHashMap<String, Draft>()

    data class Draft(val text: String, val to: Addressee?)

    private val markers = ConcurrentHashMap<String, RangeHighlighter>()

    // Deliberately apart from markers: a file glyph is a place to click, never
    // an anchor — nothing reads a line off it, and its threads follow the file.
    private val fileGlyphs = ConcurrentHashMap<String, RangeHighlighter>()

    fun fileGlyphOf(file: String): RangeHighlighter? = fileGlyphs[file]

    fun setFileGlyph(file: String, highlighter: RangeHighlighter) {
        fileGlyphs[file] = highlighter
    }

    fun removeFileGlyph(file: String): RangeHighlighter? = fileGlyphs.remove(file)

    fun clearFileGlyphs(): List<RangeHighlighter> {
        val all = fileGlyphs.values.toList()
        fileGlyphs.clear()
        return all
    }

    fun markerOf(thread: CommentThread): RangeHighlighter? = markers[thread.id]

    fun setMarker(thread: CommentThread, highlighter: RangeHighlighter) {
        markers[thread.id] = highlighter
    }

    fun removeMarker(thread: CommentThread): RangeHighlighter? = markers.remove(thread.id)

    fun syncLine(thread: CommentThread): Int? {
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

    fun syncLines() {
        threads.all().forEach { syncLine(it) }
    }

    companion object {
        fun getInstance(project: Project): MarginalisStore = project.service()
    }
}
