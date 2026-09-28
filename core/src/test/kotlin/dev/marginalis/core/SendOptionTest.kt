package dev.marginalis.core

import dev.marginalis.core.SendOption.COMMENT_ON_FILE
import dev.marginalis.core.SendOption.COMMENT_ON_PROJECT
import dev.marginalis.core.SendOption.HAND_BACK
import kotlin.test.Test
import kotlin.test.assertEquals

class SendOptionTest {

    private val onLine = CommentThread("src/a.kt", line = 3, anchorText = "x")
    private val onFile = CommentThread("src/a.kt", line = null, anchorText = null)
    private val onProject = CommentThread(file = null, line = null, anchorText = null)

    @Test
    fun `hand back is offered only while someone waits`() {
        assertEquals(emptyList(), SendOption.offered(onLine, isDraft = false, isEditing = false, anyoneWaiting = false))
        assertEquals(listOf(HAND_BACK), SendOption.offered(onLine, isDraft = false, isEditing = false, anyoneWaiting = true))
    }

    @Test
    fun `an edit can still hand back but never retargets`() {
        assertEquals(listOf(HAND_BACK), SendOption.offered(onLine, isDraft = true, isEditing = true, anyoneWaiting = true))
        assertEquals(emptyList(), SendOption.offered(onLine, isDraft = true, isEditing = true, anyoneWaiting = false))
    }

    @Test
    fun `a draft retargets only upward`() {
        assertEquals(
            listOf(HAND_BACK, COMMENT_ON_FILE, COMMENT_ON_PROJECT),
            SendOption.offered(onLine, isDraft = true, isEditing = false, anyoneWaiting = true),
        )
        assertEquals(
            listOf(COMMENT_ON_PROJECT),
            SendOption.offered(onFile, isDraft = true, isEditing = false, anyoneWaiting = false),
        )
        assertEquals(emptyList(), SendOption.offered(onProject, isDraft = true, isEditing = false, anyoneWaiting = false))
    }
}
