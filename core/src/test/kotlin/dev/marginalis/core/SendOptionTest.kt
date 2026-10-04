package dev.marginalis.core

import dev.marginalis.core.SendOption.COMMENT_ON_FILE
import dev.marginalis.core.SendOption.COMMENT_ON_PROJECT
import dev.marginalis.core.SendOption.SEND_ROUND
import kotlin.test.Test
import kotlin.test.assertEquals

class SendOptionTest {

    private val onLine = CommentThread("src/a.kt", line = 3, anchorText = "x")
    private val onFile = CommentThread("src/a.kt", line = null, anchorText = null)
    private val onProject = CommentThread(file = null, line = null, anchorText = null)

    @Test
    fun `send round is offered only while someone listens`() {
        assertEquals(emptyList(), SendOption.offered(onLine, isDraft = false, isEditing = false, anyoneListening = false))
        assertEquals(listOf(SEND_ROUND), SendOption.offered(onLine, isDraft = false, isEditing = false, anyoneListening = true))
    }

    @Test
    fun `an edit can still send the round but never retargets`() {
        assertEquals(listOf(SEND_ROUND), SendOption.offered(onLine, isDraft = true, isEditing = true, anyoneListening = true))
        assertEquals(emptyList(), SendOption.offered(onLine, isDraft = true, isEditing = true, anyoneListening = false))
    }

    @Test
    fun `a draft retargets only upward`() {
        assertEquals(
            listOf(SEND_ROUND, COMMENT_ON_FILE, COMMENT_ON_PROJECT),
            SendOption.offered(onLine, isDraft = true, isEditing = false, anyoneListening = true),
        )
        assertEquals(
            listOf(COMMENT_ON_PROJECT),
            SendOption.offered(onFile, isDraft = true, isEditing = false, anyoneListening = false),
        )
        assertEquals(emptyList(), SendOption.offered(onProject, isDraft = true, isEditing = false, anyoneListening = false))
    }
}
