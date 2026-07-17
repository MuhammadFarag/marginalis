package dev.marginalis.plugin.store

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * M0 record: a single margin note. Grows into the Thread/Message model
 * (handover §4) in M1 — deliberately not modelled yet.
 */
class MarginNote(
    val file: String, // project-relative path
    val line: Int, // 0-based, last known good
    val body: String,
) {
    val id: String = UUID.randomUUID().toString()
    val createdAt: Instant = Instant.now()

    /** Live gutter marker; in-memory only, dies with the Document (handover §3.3). */
    @Volatile
    var highlighter: RangeHighlighter? = null
}

/** In-memory note registry. No persistence in M0 (that's M2). */
@Service(Service.Level.PROJECT)
class MarginalisStore {
    private val notes = ConcurrentHashMap<String, MarginNote>()

    fun add(note: MarginNote) {
        notes[note.id] = note
    }

    fun all(): List<MarginNote> = notes.values.sortedBy { it.createdAt }

    companion object {
        fun getInstance(project: Project): MarginalisStore = project.service()
    }
}
