package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkTest {

    private fun onLine(intent: Intent? = null) = CommentThread(file = "a.py", line = 1, anchorText = "x", intent = intent)
    private fun onFile(intent: Intent? = null) = CommentThread(file = "a.py", line = null, anchorText = null, intent = intent)
    private fun onProject(intent: Intent? = null) = CommentThread(file = null, line = null, anchorText = null, intent = intent)

    @Test
    fun `each width wears its own subject`() {
        assertEquals(Mark(MarkSubject.LINE, null), Mark.of(listOf(onLine())))
        assertEquals(Mark(MarkSubject.FILE, null), Mark.of(listOf(onFile())))
        assertEquals(Mark(MarkSubject.PROJECT, null), Mark.of(listOf(onProject())))
    }

    @Test
    fun `an agreed intent colors the mark`() {
        assertEquals(Mark(MarkSubject.LINE, Intent.FINDING), Mark.of(listOf(onLine(Intent.FINDING), onLine(Intent.FINDING))))
        assertEquals(Mark(MarkSubject.FILE, Intent.GUIDANCE), Mark.of(listOf(onFile(Intent.GUIDANCE))))
        assertEquals(Mark(MarkSubject.PROJECT, Intent.QUESTION), Mark.of(listOf(onProject(Intent.QUESTION))))
    }

    @Test
    fun `disagreeing intents fall back to the ordinary mark`() {
        assertEquals(Mark(MarkSubject.LINE, null), Mark.of(listOf(onLine(Intent.FINDING), onLine(Intent.QUESTION))))
        assertEquals(Mark(MarkSubject.LINE, null), Mark.of(listOf(onLine(Intent.FINDING), onLine())))
    }

    @Test
    fun `the narrowest subject present names a mixed set`() {
        assertEquals(MarkSubject.LINE, Mark.of(listOf(onFile(), onLine())).subject)
        assertEquals(MarkSubject.FILE, Mark.of(listOf(onProject(), onFile())).subject)
    }

    @Test
    fun `every subject and intent pairing is a distinct mark`() {
        val all = Mark.all()
        assertEquals(12, all.size)
        assertEquals(12, all.toSet().size)
    }
}
