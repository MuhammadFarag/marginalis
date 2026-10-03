package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ParticipantsTest {

    private val user = Author.User("Muhammad")
    private val claude = Author.Agent("Claude", "claude-main")
    private val codex = Author.Agent("Codex", "codex")
    private val builder = Author.Agent("Claude · builder", "claude-builder")
    private val faces = Faces(People.NONE, You("Muhammad Farag", "mfarag", picture = null))

    private fun thread(vararg said: Message): CommentThread =
        CommentThread(file = "a.py", line = 1, anchorText = "x").also { t -> said.forEach(t::addMessage) }

    private fun relayed(login: String) =
        Relayed(Relayed.Source.GITHUB, login, "https://github.com/o/r/pull/1#discussion_r$login", login, login)

    private fun keysOf(participants: Participants) = participants.faces.map { it.key }

    @Test
    fun `the stack lists participants in the order they first spoke, opener first, each once`() {
        val participants = faces.participants(
            thread(Message(codex, "…"), Message(user, "…"), Message(codex, "…"), Message(claude, "…"), Message(user, "…")),
        )

        assertEquals(listOf(FaceKey.Agent("codex"), FaceKey.User, FaceKey.Agent("claude-main")), keysOf(participants))
        assertEquals(0, participants.more)
    }

    @Test
    fun `the stack shows three faces and counts the rest as more`() {
        val crowded = thread(
            Message(claude, "…"), Message(user, "…"), Message(codex, "…"), Message(builder, "…"),
            Message(claude, "…", relayed = relayed("1")),
        )

        assertEquals(listOf(FaceKey.Agent("claude-main"), FaceKey.User, FaceKey.Agent("codex")), keysOf(faces.participants(crowded)))
        assertEquals(2, faces.participants(crowded).more)
        assertEquals(1, faces.participants(crowded, max = 4).more)
        assertEquals(Participants(emptyList(), 0), faces.participants(thread()))
    }

    @Test
    fun `the user is in the stack, and a relayed person counts under their login regardless of case`() {
        val participants = faces.participants(
            thread(
                Message(claude, "…", relayed = Relayed(Relayed.Source.GITHUB, "1", "https://github.com/o/r/pull/1#discussion_r1", "Mona", "OctoCat")),
                Message(claude, "…", relayed = Relayed(Relayed.Source.GITHUB, "2", "https://github.com/o/r/pull/1#discussion_r2", "Mona", "octocat")),
                Message(user, "…"),
            ),
        )

        assertEquals(listOf(FaceKey.GitHub("octocat"), FaceKey.User), keysOf(participants))
    }
}
