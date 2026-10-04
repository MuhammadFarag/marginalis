package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadTextTest {

    private val agent = Author.Agent("Claude", "claude-main")

    private fun project(vararg messages: Message) =
        CommentThread(file = null, line = null, anchorText = null).also { thread -> messages.forEach(thread::addMessage) }

    private fun relayed(name: String, login: String, body: String = "Looks fine") = Message(
        agent,
        body,
        relayed = Relayed(Relayed.Source.GITHUB, "7", "https://github.com/o/r/pull/41#issuecomment-7", name, login),
    )

    @Test
    fun `a blank filter matches every thread`() {
        assertTrue(ThreadText.matches(project(Message(agent, "Release notes draft")), "  "))
    }

    @Test
    fun `matches message text regardless of case`() {
        val thread = project(Message(agent, "Plan for the 0.3 release"))

        assertTrue(ThreadText.matches(thread, "RELEASE"))
        assertFalse(ThreadText.matches(thread, "rollback"))
    }

    @Test
    fun `matches the pull request a relayed thread came from`() {
        assertTrue(ThreadText.matches(project(relayed("Mona Lisa", "octocat")), "pr #41"))
    }

    @Test
    fun `matches a speaker's name, including a relayed author's`() {
        val nicknamed = People(listOf(People.Row("octocat", People.Kind.PERSON, nickname = "Octo")))

        assertTrue(ThreadText.matches(project(Message(agent, "Plan")), "claude"))
        assertTrue(ThreadText.matches(project(relayed("Mona Lisa", "octocat")), "mona"))
        assertTrue(ThreadText.matches(project(relayed("Mona Lisa", "octocat")), "octo", nicknamed))
    }

    @Test
    fun `a thread that mentions nothing of the filter is hidden`() {
        assertFalse(ThreadText.matches(project(relayed("Mona Lisa", "octocat", body = "Nice work")), "rollback"))
    }

    @Test
    fun `a conversation shows when any member matches`() {
        val conversation = ProjectTabLayout.Entry.Conversation(
            "PR #41",
            listOf(project(relayed("Mona Lisa", "octocat", body = "Nit: rename")), project(relayed("Hubot", "hubot", body = "Rollback?"))),
        )

        assertTrue(ThreadText.matches(conversation, "rollback"))
        assertFalse(ThreadText.matches(conversation, "deploy"))
    }
}
