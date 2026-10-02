package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwaitingSnapshotTest {

    private val user = Author.User("Muhammad")
    private val claude = Author.Agent("Claude", "claude-main")
    private val codex = Author.Agent("Codex", "codex")
    private val store = ThreadStore()

    private fun open(vararg said: Message) =
        CommentThread(file = "a.py", line = 1, anchorText = "x").also { t -> said.forEach(t::addMessage); store.add(t) }

    @Test
    fun `a snapshot answers who was owed when it was taken`() {
        open(Message(user, "Codex?", to = Addressee.Agent("codex")))

        val owed = store.awaitingSnapshot()
        open(Message(user, "Claude?", to = Addressee.Agent("claude-main")))

        assertTrue(owed(codex))
        assertFalse(owed(claude))
        assertTrue(store.awaitingSnapshot()(claude))
    }

    @Test
    fun `an unaddressed thread is owed by every agent`() {
        open(Message(user, "Anyone?"))

        val owed = store.awaitingSnapshot()

        assertTrue(owed(codex) && owed(claude))
    }

    @Test
    fun `a snapshot agrees with asking each agent directly`() {
        open(Message(user, "Codex?", to = Addressee.Agent("codex")))
        open(Message(claude, "Over to you", to = Addressee.Agent("codex")))
        open(Message(codex, "Done."))

        val owed = store.awaitingSnapshot()

        listOf(claude, codex).forEach { assertTrue(owed(it) == store.hasAwaiting(it)) }
    }
}
