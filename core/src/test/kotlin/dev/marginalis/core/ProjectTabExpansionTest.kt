package dev.marginalis.core

import dev.marginalis.core.ProjectTabLayout.Entry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectTabExpansionTest {

    private val agent = Author.Agent("Claude", "claude-main")
    private val user = Author.User("Muhammad")

    private fun project(intent: Intent? = null) = CommentThread(file = null, line = null, anchorText = null, intent = intent)

    private fun awaitingYou() = project().also { it.addMessage(Message(agent, "Your call?")) }

    private fun awaitingTheAgent() = project().also { it.addMessage(Message(user, "Can you look?")) }

    private fun relay(pr: Int = 41) = Message(
        agent,
        "from GitHub",
        relayed = Relayed(Relayed.Source.GITHUB, "7", "https://github.com/o/r/pull/$pr#issuecomment-7", "Mona", "octocat"),
    )

    private fun relayedThread() = project().also { it.addMessage(relay()) }

    private fun seen(vararg threads: CommentThread, expandOnYourMove: Boolean = true, expansion: ProjectTabExpansion = ProjectTabExpansion()) =
        expansion.also { it.observe(threads.map { thread -> Entry.Single(thread) }, expandOnYourMove) }

    @Test
    fun `a thread awaiting you starts expanded`() {
        val thread = awaitingYou()
        assertTrue(seen(thread).isExpanded(thread.id))
    }

    @Test
    fun `a thread awaiting the agent starts collapsed`() {
        val thread = awaitingTheAgent()
        assertFalse(seen(thread).isExpanded(thread.id))
    }

    @Test
    fun `a read fyi starts collapsed`() {
        val fyi = project(Intent.FYI).also {
            it.addMessage(Message(agent, "Heads up."))
            it.markReadByUser()
        }
        assertFalse(seen(fyi).isExpanded(fyi.id))
    }

    @Test
    fun `a relayed thread starts collapsed even when it is your move`() {
        val thread = relayedThread()
        assertTrue(thread.turn() == Turn.USER_OWES)
        assertFalse(seen(thread).isExpanded(thread.id))
    }

    @Test
    fun `a conversation starts folded`() {
        val group = Entry.Conversation("PR #41", listOf(relayedThread(), relayedThread()))
        val expansion = ProjectTabExpansion().also { it.observe(listOf(group), expandOnYourMove = true) }
        assertFalse(expansion.isExpanded(group.key))
    }

    @Test
    fun `clicking a header toggles it and the choice survives later updates`() {
        val thread = awaitingYou()
        val expansion = seen(thread)

        expansion.toggle(thread.id)
        thread.addMessage(Message(agent, "And another thing."))
        seen(thread, expansion = expansion)

        assertFalse(expansion.isExpanded(thread.id))
        expansion.toggle(thread.id)
        assertTrue(expansion.isExpanded(thread.id))
    }

    @Test
    fun `a collapsed thread that becomes your move expands when the setting is on`() {
        val thread = awaitingTheAgent()
        val expansion = seen(thread)

        thread.addMessage(Message(agent, "Done — have a look."))
        seen(thread, expandOnYourMove = true, expansion = expansion)

        assertTrue(expansion.isExpanded(thread.id))
    }

    @Test
    fun `a thread you collapsed reopens when it becomes your move again and the setting is on`() {
        val thread = awaitingYou()
        val expansion = seen(thread)
        expansion.toggle(thread.id)

        thread.addMessage(Message(user, "Try the other approach."))
        seen(thread, expansion = expansion)
        thread.addMessage(Message(agent, "Done — have a look."))
        seen(thread, expandOnYourMove = true, expansion = expansion)

        assertTrue(expansion.isExpanded(thread.id))
    }

    @Test
    fun `a thread you collapsed stays collapsed when it becomes your move again and the setting is off`() {
        val thread = awaitingYou()
        val expansion = seen(thread, expandOnYourMove = false)
        expansion.toggle(thread.id)

        thread.addMessage(Message(user, "Try the other approach."))
        seen(thread, expandOnYourMove = false, expansion = expansion)
        thread.addMessage(Message(agent, "Done — have a look."))
        seen(thread, expandOnYourMove = false, expansion = expansion)

        assertFalse(expansion.isExpanded(thread.id))
    }

    @Test
    fun `a collapsed thread that becomes your move stays collapsed when the setting is off`() {
        val thread = awaitingTheAgent()
        val expansion = seen(thread, expandOnYourMove = false)

        thread.addMessage(Message(agent, "Done — have a look."))
        seen(thread, expandOnYourMove = false, expansion = expansion)

        assertFalse(expansion.isExpanded(thread.id))
    }

    @Test
    fun `replying, so the turn passes to the agent, leaves an expanded thread expanded`() {
        val thread = awaitingYou()
        val expansion = seen(thread)

        thread.addMessage(Message(user, "On it — but check this first."))
        seen(thread, expansion = expansion)

        assertTrue(expansion.isExpanded(thread.id))
    }

    @Test
    fun `revealing a thread expands it`() {
        val thread = awaitingTheAgent()
        val expansion = seen(thread)

        expansion.reveal(thread.id)

        assertTrue(expansion.isExpanded(thread.id))
    }

    @Test
    fun `a thread holding an unsent reply stays expanded`() {
        val thread = awaitingYou()
        val expansion = ProjectTabExpansion(holdsDraft = { it == thread.id })
        seen(thread, expansion = expansion)

        expansion.toggle(thread.id)

        assertTrue(expansion.isExpanded(thread.id))
    }

    @Test
    fun `revealing a member of a folded conversation expands both the member and the conversation`() {
        val member = relayedThread()
        val group = Entry.Conversation("PR #41", listOf(member, relayedThread()))
        val expansion = ProjectTabExpansion().also { it.observe(listOf(group), expandOnYourMove = true) }

        expansion.reveal(member.id)

        assertTrue(expansion.isExpanded(member.id))
        assertTrue(expansion.isExpanded(group.key))
    }

    @Test
    fun `a dissolved conversation forgets its members`() {
        val member = relayedThread()
        val group = Entry.Conversation("PR #41", listOf(member))
        val expansion = ProjectTabExpansion(holdsDraft = { it == member.id })
        expansion.observe(listOf(group), expandOnYourMove = true)

        expansion.observe(listOf(Entry.Single(member)), expandOnYourMove = true)

        assertFalse(expansion.isExpanded(group.key))
    }

    @Test
    fun `a thread that leaves the list forgets your choice and comes back with its default`() {
        val thread = awaitingYou()
        val expansion = seen(thread)
        expansion.toggle(thread.id)

        seen(expansion = expansion)
        seen(thread, expansion = expansion)

        assertTrue(expansion.isExpanded(thread.id))
    }
}
