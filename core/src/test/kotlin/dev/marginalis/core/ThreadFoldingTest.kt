package dev.marginalis.core

import dev.marginalis.core.ThreadFolding.Level
import dev.marginalis.core.ThreadFolding.Row
import dev.marginalis.core.ThreadFolding.View
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadFoldingTest {

    private val agent = Author.Agent("Claude", "claude-main")
    private val user = Author.User("Ada")

    private fun read(body: String) = Message(agent, body, id = body).also { it.markReadByUser() }

    private fun unread(body: String) = Message(agent, body, id = body)

    private fun agreement(id: String) = Message(user, "Agreed.", id = id, agrees = true)

    private fun reads(count: Int) = (1..count).map { read("m$it") }

    private fun shape(messages: List<Message>, view: View = View()): List<String> = ThreadFolding.rows(messages, view).map { row ->
        when (row) {
            is Row.Open -> row.message.body
            is Row.Folded -> "(${row.message.body})"
            is Row.Stack -> "stack(${row.messages.joinToString(",") { it.body }})"
        }
    }

    @Test
    fun `a read message folds while the last two stay open`() {
        assertEquals(listOf("(m1)", "(m2)", "m3", "m4"), shape(reads(4)))
    }

    @Test
    fun `a thread of two read messages keeps both open`() {
        assertEquals(listOf("m1", "m2"), shape(reads(2)))
    }

    @Test
    fun `an unread message stays open`() {
        val messages = listOf(read("m1"), unread("m2"), read("m3"), read("m4"), read("m5"))

        assertEquals(listOf("(m1)", "m2", "(m3)", "m4", "m5"), shape(messages))
    }

    @Test
    fun `a message the panel keeps open stays open`() {
        assertEquals(listOf("(m1)", "m2", "(m3)", "m4", "m5"), shape(reads(5), View(keptOpen = setOf("m2"))))
    }

    @Test
    fun `the message being edited stays open`() {
        assertEquals(listOf("(m1)", "m2", "(m3)", "m4", "m5"), shape(reads(5), View(editing = "m2")))
    }

    @Test
    fun `a message the user opened stays open`() {
        assertEquals(listOf("(m1)", "m2", "(m3)", "m4", "m5"), shape(reads(5), View(opened = setOf("m2"))))
    }

    @Test
    fun `a message the user folded folds even when it would stay open`() {
        val messages = listOf(read("m1"), unread("m2"), read("m3"))

        assertEquals(listOf("(m1)", "(m2)", "(m3)"), shape(messages, View(folded = setOf("m2", "m3"))))
    }

    @Test
    fun `an agreement never folds and is not one of the last two`() {
        val messages = reads(3) + agreement("a1")

        assertEquals(listOf("(m1)", "m2", "m3", "Agreed."), shape(messages))
    }

    @Test
    fun `an agreement the user folded still shows`() {
        val messages = reads(3) + agreement("a1")

        assertEquals(listOf("(m1)", "m2", "m3", "Agreed."), shape(messages, View(folded = setOf("a1"))))
    }

    @Test
    fun `three folded messages in a row stay as folded rows`() {
        val messages = listOf(unread("m1")) + reads(6).drop(1)

        assertEquals(listOf("m1", "(m2)", "(m3)", "(m4)", "m5", "m6"), shape(messages))
    }

    @Test
    fun `more than three folded messages in a row stack behind one row`() {
        assertEquals(listOf("(m1)", "stack(m2,m3,m4,m5)", "m6", "m7"), shape(reads(7)))
    }

    @Test
    fun `the first message never goes into a stack`() {
        assertEquals(listOf("(m1)", "(m2)", "(m3)", "(m4)", "m5", "m6"), shape(reads(6)))
    }

    @Test
    fun `an open message splits a run into runs that stack on their own`() {
        val messages = reads(11)

        assertEquals(
            listOf("(m1)", "stack(m2,m3,m4,m5)", "m6", "(m7)", "(m8)", "(m9)", "m10", "m11"),
            shape(messages, View(keptOpen = setOf("m6"))),
        )
    }

    @Test
    fun `an agreement breaks a run of folded messages`() {
        val messages = reads(4) + agreement("a1") + reads(9).drop(4)

        assertEquals(
            listOf("(m1)", "(m2)", "(m3)", "(m4)", "Agreed.", "(m5)", "(m6)", "(m7)", "m8", "m9"),
            shape(messages),
        )
    }

    @Test
    fun `a revealed stack shows its messages as folded rows`() {
        assertEquals(
            listOf("(m1)", "(m2)", "(m3)", "(m4)", "(m5)", "m6", "m7"),
            shape(reads(7), View(revealed = setOf("m2", "m3", "m4", "m5"))),
        )
    }

    @Test
    fun `messages the user folded never stack`() {
        val folded = setOf("m1", "m2", "m3", "m4", "m5", "m6")

        assertEquals(listOf("(m1)", "(m2)", "(m3)", "(m4)", "(m5)", "(m6)"), shape(reads(6), View(folded = folded)))
    }

    @Test
    fun `a message the user folded stays in view and splits the run around it`() {
        assertEquals(
            listOf("(m1)", "(m2)", "(m3)", "(m4)", "(m5)", "(m6)", "(m7)", "(m8)", "m9", "m10"),
            shape(reads(10), View(folded = setOf("m5"))),
        )
    }

    @Test
    fun `opening a message out of a stack re-stacks only a remaining run of more than three`() {
        val messages = reads(10)

        assertEquals(
            listOf("(m1)", "(m2)", "m3", "stack(m4,m5,m6,m7,m8)", "m9", "m10"),
            shape(messages, View(opened = setOf("m3"))),
        )
    }

    @Test
    fun `all folded folds every message, unread and kept open included`() {
        val messages = listOf(read("m1"), unread("m2"), read("m3"))

        assertEquals(listOf("(m1)", "(m2)", "(m3)"), shape(messages, View(level = Level.ALL_FOLDED, keptOpen = setOf("m3"))))
    }

    @Test
    fun `all folded keeps the message being edited open`() {
        assertEquals(listOf("(m1)", "m2", "(m3)"), shape(reads(3), View(level = Level.ALL_FOLDED, editing = "m2")))
    }

    @Test
    fun `all folded stacks long runs`() {
        assertEquals(listOf("(m1)", "stack(m2,m3,m4,m5,m6)"), shape(reads(6), View(level = Level.ALL_FOLDED)))
    }

    @Test
    fun `all folded still opens a message the user opened`() {
        assertEquals(listOf("(m1)", "m2", "(m3)"), shape(reads(3), View(level = Level.ALL_FOLDED, opened = setOf("m2"))))
    }

    @Test
    fun `all expanded opens every message`() {
        assertEquals(listOf("m1", "m2", "m3", "m4", "m5", "m6"), shape(reads(6), View(level = Level.ALL_EXPANDED)))
    }

    @Test
    fun `all expanded still folds a message the user folded, never stacking`() {
        val folded = setOf("m1", "m2", "m3", "m4", "m5")

        assertEquals(listOf("(m1)", "(m2)", "(m3)", "(m4)", "(m5)", "m6"), shape(reads(6), View(level = Level.ALL_EXPANDED, folded = folded)))
    }

    @Test
    fun `folding steps from all expanded to default to all folded`() {
        assertEquals(Level.DEFAULT, Level.ALL_EXPANDED.folded())
        assertEquals(Level.ALL_FOLDED, Level.DEFAULT.folded())
        assertNull(Level.ALL_FOLDED.folded())
    }

    @Test
    fun `expanding steps from all folded to default to all expanded`() {
        assertEquals(Level.DEFAULT, Level.ALL_FOLDED.expanded())
        assertEquals(Level.ALL_EXPANDED, Level.DEFAULT.expanded())
        assertNull(Level.ALL_EXPANDED.expanded())
    }
}
