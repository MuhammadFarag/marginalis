package dev.marginalis.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentitiesTest {

    private val t0 = Instant.parse("2026-09-28T10:00:00Z")
    private val user = Author.User("Muhammad")
    private val claude = Author.Agent("Claude · builder", "claude-builder")
    private val codex = Author.Agent("Codex")

    private fun thread(vararg said: Message) =
        CommentThread(file = "a.kt", line = 0, anchorText = "x").also { t -> said.forEach(t::addMessage) }

    private fun said(author: Author, minute: Long = 0, seenBy: Set<String>? = null) =
        Message(author, "…", createdAt = t0.plusSeconds(minute * 60), seenBy = seenBy)

    private fun agents(identities: List<Identity>) = identities.filterIsInstance<Identity.Agent>()

    @Test
    fun `an empty margin still knows the user`() {
        assertEquals(listOf<Identity>(Identity.User("Muhammad", 0)), Identities.of(emptyList(), user, emptyList()))
    }

    @Test
    fun `the user counts every message they wrote, whatever name it was written under`() {
        val threads = listOf(thread(said(user), said(Author.User("Old name"))), thread(said(user)))

        assertEquals(Identity.User("Muhammad", 3), Identities.of(threads, user, emptyList()).first())
    }

    @Test
    fun `an agent that wrote is known by its receipt key, with what it wrote and what it hasn't seen`() {
        val threads = listOf(
            thread(said(user), said(claude, 1), said(user, 2)),
            thread(said(user, seenBy = setOf("claude-builder"))),
        )

        assertEquals(
            listOf(Identity.Agent(id = "claude-builder", name = "Claude · builder", messagesWritten = 1, unread = 2, waiting = false)),
            agents(Identities.of(threads, user, emptyList())),
        )
    }

    @Test
    fun `an agent that never gave an id is known by its name, which is its receipt key`() {
        val identities = Identities.of(listOf(thread(said(codex))), user, emptyList())

        assertEquals(listOf(Identity.Agent("Codex", "Codex", 1, 0, false)), agents(identities))
    }

    @Test
    fun `an identity known only from read receipts has no name and has written nothing`() {
        val threads = listOf(thread(said(user, seenBy = setOf("reader")), said(user, 1)))

        assertEquals(listOf(Identity.Agent("reader", null, 0, 1, false)), agents(Identities.of(threads, user, emptyList())))
    }

    @Test
    fun `an agent is named as it last wrote`() {
        val renamed = Author.Agent("Claude · impl", "claude-builder")
        val threads = listOf(thread(said(renamed, 5)), thread(said(claude, 1)))

        assertEquals("Claude · impl", agents(Identities.of(threads, user, emptyList())).single().name)
    }

    @Test
    fun `unread depth spans concluded threads too — the whole history is unread to a fresh id`() {
        val resolved = thread(said(user), said(claude, 1)).also { it.resolve(user) }
        val orphaned = thread(said(user, 2)).also { it.markOrphaned() }

        val fresh = agents(Identities.of(listOf(resolved, orphaned), user, listOf(Author.Agent("New", "new"))))
            .single { it.id == "new" }

        assertEquals(3, fresh.unread)
    }

    @Test
    fun `a waiting agent says so, and a waiter the history never saw is still known`() {
        val threads = listOf(thread(said(claude)))

        val identities = agents(Identities.of(threads, user, listOf(claude, Author.Agent("Stranger", "stranger"))))

        assertEquals(
            listOf(
                Identity.Agent("claude-builder", "Claude · builder", 1, 0, waiting = true),
                Identity.Agent("stranger", "Stranger", 0, 1, waiting = true),
            ),
            identities,
        )
    }

    @Test
    fun `the user comes first, then agents by how much they wrote, ties by id`() {
        val threads = listOf(
            thread(said(codex), said(codex, 1), said(Author.Agent("Zed", "zed"), 2), said(Author.Agent("Amy", "amy"), 3)),
        )

        val order = Identities.of(threads, user, emptyList()).map {
            when (it) {
                is Identity.User -> "user"
                is Identity.Agent -> it.id
            }
        }

        assertEquals(listOf("user", "Codex", "amy", "zed"), order)
    }

    @Test
    fun `names by key carry each agent's latest-written name and skip the user`() {
        val threads = listOf(
            thread(said(user), said(Author.Agent("Claude", "claude-builder"), 1)),
            thread(said(claude, 2), said(codex, 3)),
        )

        assertEquals(
            mapOf("claude-builder" to "Claude · builder", "Codex" to "Codex"),
            Identities.namesByKey(threads),
        )
    }
}
