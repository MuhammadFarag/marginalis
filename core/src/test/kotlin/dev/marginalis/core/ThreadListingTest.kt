package dev.marginalis.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadListingTest {

    private val user = Author.User("Muhammad")
    private var tick = 0L
    private val clock: (Instant) -> String = { "t${it.epochSecond}" }

    private fun step(order: Int, walkthroughId: String, label: String? = null, createdAt: Instant = Instant.ofEpochSecond(tick++)) =
        CommentThread(
            file = "a.py", line = order, anchorText = "x",
            createdAt = createdAt, order = order, walkthrough = label, walkthroughId = walkthroughId,
        )

    private fun comment() = CommentThread("a.py", 1, "x", createdAt = Instant.ofEpochSecond(tick++))

    private fun CommentThread.resolved() = apply { resolve(user) }

    private fun CommentThread.orphaned() = apply { markOrphaned() }

    @Test
    fun `open lists the ordinary comments alone and guided lists the open steps of each walkthrough`() {
        val loose = comment()
        val one = step(1, "w")
        val two = step(2, "w")
        val listing = ThreadListing.of(listOf(two, loose, one))

        assertEquals(listOf(loose), listing.open)
        assertEquals(listOf(listOf(one, two)), listing.guided.map { it.steps })
    }

    @Test
    fun `resolved and orphaned steps are grouped by walkthrough ahead of the comments of that status`() {
        val resolvedComment = comment().resolved()
        val orphanedComment = comment().orphaned()
        val first = step(1, "w").resolved()
        val second = step(2, "w").orphaned()
        val third = step(3, "w")
        val listing = ThreadListing.of(listOf(resolvedComment, orphanedComment, third, second, first))

        assertEquals(listOf(listOf(first)), listing.resolved.walkthroughs.map { it.steps })
        assertEquals(listOf(resolvedComment), listing.resolved.comments)
        assertEquals(listOf(listOf(second)), listing.orphaned.walkthroughs.map { it.steps })
        assertEquals(listOf(orphanedComment), listing.orphaned.comments)
        assertEquals(listOf(listOf(third)), listing.guided.map { it.steps })
    }

    @Test
    fun `a group numbers its steps out of the whole walkthrough even when the rest is filtered out or finished`() {
        val one = step(1, "w").resolved()
        val two = step(2, "w").resolved()
        val three = step(3, "w").resolved()
        val listing = ThreadListing.of(shown = listOf(two), all = listOf(one, two, three))

        assertEquals(3, listing.resolved.walkthroughs.single().total)
    }

    @Test
    fun `two walkthroughs under one label are two groups ordered by when they started`() {
        val later = step(1, "later", "tour", createdAt = Instant.ofEpochSecond(200))
        val earlier = step(1, "earlier", "tour", createdAt = Instant.ofEpochSecond(100))
        val listing = ThreadListing.of(listOf(later, earlier))

        assertEquals(listOf("earlier", "later"), listing.guided.map { it.walkthroughId })
    }

    @Test
    fun `walkthroughs are ordered by label, the unlabelled first`() {
        val labelled = step(1, "r2", "r2")
        val plain = step(1, "plain")
        val shop = step(1, "shop", "SHOP-42")
        val listing = ThreadListing.of(listOf(labelled, plain, shop), startTime = clock)

        assertEquals(listOf("Walkthrough", "SHOP-42", "r2"), listing.guided.map { it.title })
    }

    @Test
    fun `groups that would read the same gain their start time and the rest stay plain`() {
        val first = step(1, "first", createdAt = Instant.ofEpochSecond(100))
        val firstLater = step(2, "first", createdAt = Instant.ofEpochSecond(150))
        val second = step(1, "second", createdAt = Instant.ofEpochSecond(300))
        val tour = step(1, "tour", "tour")
        val listing = ThreadListing.of(listOf(second, firstLater, first, tour), startTime = clock)

        assertEquals(listOf("Walkthrough · t100", "Walkthrough · t300", "tour"), listing.guided.map { it.title })
    }

    @Test
    fun `a walkthrough starts when its earliest step was created, shown or not`() {
        val hidden = step(1, "w", createdAt = Instant.ofEpochSecond(50)).resolved()
        val shown = step(2, "w", createdAt = Instant.ofEpochSecond(400))
        val other = step(1, "v", createdAt = Instant.ofEpochSecond(500))
        val listing = ThreadListing.of(shown = listOf(shown, other), all = listOf(hidden, shown, other), startTime = clock)

        assertEquals(listOf("Walkthrough · t50", "Walkthrough · t500"), listing.guided.map { it.title })
    }

    @Test
    fun `titles are told apart within one list only`() {
        val openStep = step(1, "w")
        val resolvedStep = step(2, "v").resolved()
        val listing = ThreadListing.of(listOf(openStep, resolvedStep), startTime = clock)

        assertEquals(listOf("Walkthrough"), listing.guided.map { it.title })
        assertEquals(listOf("Walkthrough"), listing.resolved.walkthroughs.map { it.title })
    }

    @Test
    fun `groups whose start times read the same are numbered in the order they started`() {
        val first = step(1, "first", createdAt = Instant.ofEpochSecond(100))
        val second = step(1, "second", createdAt = Instant.ofEpochSecond(110))
        val other = step(1, "other", createdAt = Instant.ofEpochSecond(5000))
        val listing = ThreadListing.of(listOf(second, other, first), startTime = { if (it.epochSecond < 1000) "14:40" else "15:55" })

        assertEquals(listOf("Walkthrough · 14:40 (1)", "Walkthrough · 14:40 (2)", "Walkthrough · 15:55"), listing.guided.map { it.title })
    }

    @Test
    fun `long labels are told apart by their shortened titles and keep the whole time suffix`() {
        val base = "x".repeat(45)
        val first = step(1, "first", base + "A", createdAt = Instant.ofEpochSecond(100))
        val second = step(1, "second", base + "B", createdAt = Instant.ofEpochSecond(300))
        val listing = ThreadListing.of(listOf(first, second), startTime = clock)

        assertEquals(listOf("${"x".repeat(37)}... · t100", "${"x".repeat(37)}... · t300"), listing.guided.map { it.title })
    }

    @Test
    fun `a thread's walkthrough title is the one its section shows`() {
        val first = step(1, "first", "tour", createdAt = Instant.ofEpochSecond(100))
        val second = step(1, "second", "tour", createdAt = Instant.ofEpochSecond(300))
        val alone = step(1, "alone", "solo")
        val resolvedTwin = step(2, "second", "tour", createdAt = Instant.ofEpochSecond(310)).resolved()
        val all = listOf(first, second, alone, resolvedTwin)

        assertEquals("tour · t300", ThreadListing.titleOf(all, second, clock))
        assertEquals("solo", ThreadListing.titleOf(all, alone, clock))
        assertEquals("tour", ThreadListing.titleOf(all, resolvedTwin, clock))
    }
}
