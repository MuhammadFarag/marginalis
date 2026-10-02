package dev.marginalis.core

import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RelayedThreadTest {

    private val claude = Author.Agent("Claude", "claude-main")
    private val reviewer = Author.Agent("Claude · review", "claude-review")
    private val user = Author.User("Muhammad")

    private fun github(commentId: String, login: String = "octocat", fragment: String = "discussion_r") =
        Relayed(Relayed.Source.GITHUB, commentId, "https://github.com/o/r/pull/7#$fragment$commentId", "Mona", login)

    private fun relay(commentId: String, by: Author.Agent = claude, at: Instant = Instant.now()) =
        Message(by, "from GitHub", createdAt = at, relayed = github(commentId))

    private fun thread(vararg said: Message, intent: Intent? = null) =
        CommentThread(file = "a.py", line = 1, anchorText = "x", intent = intent).also { t -> said.forEach(t::addMessage) }

    @Test
    fun `a thread holding only relayed messages is the user's move, with nothing to agree with`() {
        val t = thread(relay("1"), relay("2"))

        assertEquals(Turn.USER_OWES, t.turn())
        assertNull(t.lastSpoken)
        assertNull(t.agreeable())
    }

    @Test
    fun `relayed messages never take the turn from whoever spoke last`() {
        assertEquals(Turn.AGENT_OWES, thread(relay("1"), Message(user, "Fix it?"), relay("2")).turn())
        assertEquals(Turn.USER_OWES, thread(Message(user, "?"), Message(claude, "Done."), relay("2")).turn())
    }

    @Test
    fun `whom the last spoken word addresses still decides which agent owes`() {
        val t = thread(Message(user, "Codex?", to = Addressee.Agent("codex")), relay("1"))

        assertEquals(Turn.AGENT_OWES, t.turnFor("codex"))
        assertNull(t.turnFor("claude-main"))
    }

    @Test
    fun `the awaiting snapshot reads the last spoken word, not the relay after it`() {
        val store = ThreadStore()
        store.add(thread(Message(user, "Codex?", to = Addressee.Agent("codex")), relay("1")))

        val owed = store.awaitingSnapshot()

        assertTrue(owed(Author.Agent("Codex", "codex")))
        assertFalse(owed(claude))
    }

    @Test
    fun `only an agent's own word can be agreed with, never a relayed one`() {
        val proposal = Message(claude, "Rename it?")
        assertSame(proposal, thread(relay("1"), proposal, relay("2")).agreeable())
        assertNull(thread(Message(user, "?"), relay("1")).agreeable())
    }

    @Test
    fun `a relayed message neither continues nor is continued — each shows its own author`() {
        assertFalse(relay("2").continues(relay("1")))
        assertFalse(Message(claude, "…").continues(relay("1")))
        assertFalse(relay("1").continues(Message(claude, "…")))
    }

    @Test
    fun `a re-relay where the user spoke last leaves the agent owing`() {
        val t = thread(Message(claude, "Done?"), Message(user, "Not yet — see GitHub."))
        t.markReadByUser()

        t.addMessage(relay("1"))

        assertEquals(Turn.AGENT_OWES, t.turn())
    }

    @Test
    fun `a re-relay on a read fyi is the user's move until read, and Agree still answers only the agent's word`() {
        val fyi = Message(claude, "Lovely.")
        val t = thread(fyi, intent = Intent.FYI).also { it.markReadByUser() }
        val relayed = relay("1")

        t.addMessage(relayed)
        assertEquals(Turn.USER_OWES, t.turn())
        assertSame(fyi, t.agreeable())

        t.markReadByUser()
        assertTrue(relayed.readByUser)
        assertNull(t.turn())
    }

    @Test
    fun `a thread of only relayed messages stays the user's move once read, still with nothing to agree with`() {
        val t = thread(relay("1"))

        t.markReadByUser()

        assertEquals(Turn.USER_OWES, t.turn())
        assertNull(t.agreeable())
    }

    @Test
    fun `unread relays owe the user nothing to agree with when no agent word awaits them`() {
        val t = thread(Message(claude, "Codex?", to = Addressee.Agent("codex")), relay("1"))

        assertEquals(Turn.AGENT_OWES, t.turn())
        assertNull(t.agreeable())
    }

    @Test
    fun `a relay never notifies the user, while an agent's own word does`() {
        assertFalse(relay("1").notifiesUser)
        assertTrue(Message(claude, "Done.").notifiesUser)
        assertFalse(Message(user, "Thanks").notifiesUser)
    }

    @Test
    fun `a relay speaks as its GitHub author, everything else as its author`() {
        assertEquals("Mona", relay("1").speaker(People.NONE))
        assertEquals("Claude", Message(claude, "…").speaker(People.NONE))
    }

    @Test
    fun `a thread finds the message relaying a GitHub comment by kind and id`() {
        val second = relay("2")
        val t = thread(relay("1"), second)

        assertSame(second, t.relayedMessage(github("2").key))
        assertNull(t.relayedMessage(github("2", fragment = "issuecomment-").key))
        assertNull(t.relayedMessage(github("3").key))
    }

    @Test
    fun `relaying the same comment twice into a thread adds it once and answers the first`() {
        val first = relay("1")
        val t = thread(first)

        val again = t.addRelayedOnce(relay("1"))
        val fresh = relay("2")

        assertSame(first, again)
        assertSame(fresh, t.addRelayedOnce(fresh))
        assertEquals(2, t.messages.size)
    }

    @Test
    fun `racing relays of one comment land once`() {
        val t = thread()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(32) { pool.submit { start.await(); t.addRelayedOnce(relay("1")) } }

        start.countDown()
        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        assertEquals(1, t.messages.size)
    }

    @Test
    fun `the store finds the thread already relaying a GitHub comment, root or reply`() {
        val store = ThreadStore()
        val relaying = thread(relay("1"), relay("2"))
        store.add(thread(Message(claude, "unrelated")))
        store.add(relaying)

        assertSame(relaying, store.relaying(github("1").key))
        assertSame(relaying, store.relaying(github("2").key))
        assertNull(store.relaying(github("3").key))
    }

    @Test
    fun `the agent to wake answers a relay by its own last word, not by the relay`() {
        val said = listOf(Message(reviewer, "Looks off."), Message(user, "Fix?", to = Addressee.Agent("claude-review")), relay("1"))

        assertEquals("claude-review", LiveAgent.keyOf(said, to = null))
    }

    @Test
    fun `the agent to wake is the last that spoke itself, before any that only relayed`() {
        val said = listOf(Message(reviewer, "Looks off."), relay("1"), Message(user, "Thanks"))

        assertEquals("claude-review", LiveAgent.keyOf(said, to = null))
        assertEquals("claude-main", LiveAgent.keyOf(listOf(relay("1"), Message(user, "Thanks")), to = null))
    }

    @Test
    fun `relaying does not end an agent's working`() {
        val delivered = Instant.parse("2026-10-01T12:00:00Z")
        val said = listOf(Message(user, "Go"), relay("1", at = delivered.plusSeconds(5)))

        assertTrue(LiveThread.isWorking(said, "claude-main", delivered))
    }

    @Test
    fun `a summary's last author is the last one who spoke, not the relayer`() {
        val summary = ThreadSummary.of(thread(Message(user, "?"), relay("1")), "claude-main")

        assertEquals(user, summary.lastAuthor)
        assertNull(ThreadSummary.of(thread(relay("1")), "claude-main").lastAuthor)
    }

    @Test
    fun `relays do not count as messages an agent wrote, though the relayer is still known by name`() {
        val identities = Identities.of(listOf(thread(Message(claude, "Mine"), relay("1"), relay("2"))), user, emptyList())

        val agent = identities.filterIsInstance<Identity.Agent>().single()
        assertEquals(1, agent.messagesWritten)
        assertEquals("Claude", agent.name)
    }
}
