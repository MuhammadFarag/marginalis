package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageGroupingTest {

    private val claude = Author.Agent("Claude · builder", "claude-builder")
    private val codex = Author.Agent("Codex")
    private val user = Author.User("Muhammad")

    @Test
    fun `an agent's unaddressed follow-up continues its previous unaddressed message`() {
        assertTrue(Message(claude, "…").continues(Message(claude, "…")))
    }

    @Test
    fun `a follow-up after an addressed message starts afresh — it is not for that addressee`() {
        assertFalse(Message(claude, "…").continues(Message(claude, "…", to = Addressee.Agent("claude-review"))))
    }

    @Test
    fun `an addressed message always shows whom it is for`() {
        assertFalse(Message(claude, "…", to = Addressee.User).continues(Message(claude, "…")))
        assertFalse(Message(claude, "…", to = Addressee.User).continues(Message(claude, "…", to = Addressee.User)))
    }

    @Test
    fun `the user, another agent, or nothing before never continue`() {
        assertFalse(Message(user, "…").continues(Message(user, "…")))
        assertFalse(Message(claude, "…").continues(Message(codex, "…")))
        assertFalse(Message(claude, "…").continues(null))
    }

    @Test
    fun `a message shows its avatar when its byline shows, an agent's continuation does not, and an agreement line never does`() {
        val opener = Message(claude, "…")

        assertTrue(opener.showsAvatar(null))
        assertFalse(Message(claude, "…").showsAvatar(opener))
        assertTrue(Message(codex, "…").showsAvatar(opener))
        assertFalse(Message.agreement(user, claude).showsAvatar(opener))
    }

    @Test
    fun `a two-person thread still shows avatars`() {
        val thread = listOf(Message(user, "…"), Message(claude, "…"), Message(user, "…"), Message(claude, "…"))

        assertTrue(thread.withIndex().all { (i, message) -> message.showsAvatar(thread.getOrNull(i - 1)) })
    }
}
