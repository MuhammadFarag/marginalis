package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RelayedClosedThreadTest {

    private val claude = Author.Agent("Claude", "claude-main")
    private val user = Author.User("Muhammad")

    private fun github(commentId: String, fragment: String = "discussion_r") =
        Relayed(Relayed.Source.GITHUB, commentId, "https://github.com/o/r/pull/7#$fragment$commentId", "Mona", "octocat")

    private fun relay(commentId: String) = Message(claude, "from GitHub", relayed = github(commentId))

    private fun thread(vararg said: Message) =
        CommentThread(file = "a.py", line = 1, anchorText = "x").also { t -> said.forEach(t::addMessage) }

    @Test
    fun `a new GitHub reply reopens a resolved thread — the user closed it before those words existed`() {
        val t = thread(relay("1")).also { it.resolve(user) }

        t.addRelayedOnce(relay("2"))

        assertIs<ThreadStatus.Open>(t.status)
    }

    @Test
    fun `re-relaying what is already there leaves a resolved thread resolved`() {
        val t = thread(relay("1"), relay("2")).also { it.resolve(user) }

        t.addRelayedOnce(relay("2"))

        assertIs<ThreadStatus.Resolved>(t.status)
    }

    @Test
    fun `an orphaned thread stays orphaned when a new reply is relayed into it`() {
        val t = thread(relay("1")).also { it.markOrphaned() }

        t.addRelayedOnce(relay("2"))

        assertEquals(ThreadStatus.Orphaned, t.status)
    }

    @Test
    fun `deleting a thread whose root is relayed remembers that comment, by kind and id`() {
        val store = ThreadStore()
        val relayedRoot = thread(relay("1"), relay("2")).also(store::add)

        store.delete(relayedRoot.id)

        assertTrue(store.isDeletedRelay(github("1").key))
        assertFalse(store.isDeletedRelay(github("2").key))
        assertFalse(store.isDeletedRelay(github("1", fragment = "issuecomment-").key))
    }

    @Test
    fun `deleting a thread the agent started itself remembers nothing`() {
        val store = ThreadStore()
        val own = thread(Message(claude, "Mine"), relay("1")).also(store::add)

        store.delete(own.id)

        assertEquals(emptySet(), store.deletedRelays)
    }

    @Test
    fun `clearing the whole margin is a reset that forgets deleted relays too`() {
        val store = ThreadStore()
        store.delete(thread(relay("1")).also(store::add).id)
        store.add(thread(relay("2")))

        store.clear()

        assertEquals(emptySet(), store.deletedRelays)
    }

    @Test
    fun `deleted relays survive a restart, and older files have none`() {
        val keys = setOf(github("1").key, github("2", fragment = "issuecomment-").key)

        val decoded = ThreadsCodec.decodeDocument(ThreadsCodec.encode(emptyList(), deletedRelays = keys))

        assertEquals(keys, decoded.deletedRelays)
        assertEquals(emptySet(), ThreadsCodec.decodeDocument("""{"version":1,"threads":[]}""").deletedRelays)
    }

    @Test
    fun `a store restores the deleted relays it was saved with`() {
        val store = ThreadStore()

        store.restoreDeletedRelays(setOf(github("1").key))

        assertTrue(store.isDeletedRelay(github("1").key))
    }

    @Test
    fun `relaying into a thread lands a new comment and answers one already there`() {
        val store = ThreadStore()
        val t = thread(relay("1")).also(store::add)
        val fresh = relay("2")

        assertEquals(RelayOutcome.Added(fresh), store.relayInto(t, fresh))
        assertEquals(RelayOutcome.Existing(t.messages[1]), store.relayInto(t, relay("2")))
        assertEquals(2, t.messages.size)
    }

    @Test
    fun `relaying a comment that lives in another thread names that thread and adds nothing`() {
        val store = ThreadStore()
        val home = thread(relay("1"), relay("2")).also(store::add)
        val other = thread(relay("9")).also(store::add)

        assertEquals(RelayOutcome.Elsewhere(home), store.relayInto(other, relay("2")))
        assertEquals(1, other.messages.size)
    }

    @Test
    fun `relaying a comment whose thread the user deleted is refused`() {
        val store = ThreadStore()
        store.delete(thread(relay("1")).also(store::add).id)
        val t = thread(relay("5")).also(store::add)

        assertEquals(RelayOutcome.Deleted, store.relayInto(t, relay("1")))
        assertEquals(1, t.messages.size)
    }

    @Test
    fun `racing relays of one comment into two threads land in exactly one`() {
        val store = ThreadStore()
        val first = thread(relay("1")).also(store::add)
        val second = thread(relay("2")).also(store::add)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
        val start = java.util.concurrent.CountDownLatch(1)
        repeat(32) { i -> pool.submit { start.await(); store.relayInto(if (i % 2 == 0) first else second, relay("7")) } }

        start.countDown()
        pool.shutdown()
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)

        assertEquals(1, (first.messages + second.messages).count { it.relayed?.commentId == "7" })
    }

    @Test
    fun `deleted relays are stored under their wire kind`() {
        val encoded = ThreadsCodec.encode(emptyList(), deletedRelays = setOf(github("1", fragment = "issuecomment-").key))

        assertTrue(encoded.contains("\"kind\":\"conversation_comment\""), encoded)
    }

    @Test
    fun `a thread is a relayed root when its first message is relayed`() {
        assertTrue(thread(relay("1"), Message(claude, "Mine")).isRelayedRoot)
        assertFalse(thread(Message(claude, "Mine"), relay("1")).isRelayedRoot)
        assertFalse(thread().isRelayedRoot)
    }
}
