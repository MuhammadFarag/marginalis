package dev.marginalis.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadOrderTest {

    private var tick = 0L

    private fun thread(file: String, line: Int? = null, createdAt: Instant = Instant.ofEpochSecond(tick++)) =
        CommentThread(file = file, line = line, anchorText = line?.let { "x" }, createdAt = createdAt)

    @Test
    fun `within a file, what is about the whole file is read first`() {
        val top = thread("src/a.py", line = 0)
        val deep = thread("src/a.py", line = 40)
        val whole = thread("src/a.py")
        assertEquals(listOf(whole, top, deep), listOf(deep, top, whole).sortedWith(ThreadOrder.byAnchor))
    }

    @Test
    fun `files come in directory-tree order, dirs before files at each level`() {
        val readme = thread("readme.md")
        val deep = thread("src/util/a.py", line = 5)
        val main = thread("src/main.py", line = 9)
        assertEquals(
            listOf(deep, main, readme),
            listOf(readme, main, deep).sortedWith(ThreadOrder.byAnchor),
        )
    }

    @Test
    fun `co-located threads keep their conversation order — oldest first`() {
        val first = thread("a.py", line = 2)
        val second = thread("a.py", line = 2)
        val firstFileLevel = thread("a.py")
        val secondFileLevel = thread("a.py")
        assertEquals(
            listOf(firstFileLevel, secondFileLevel, first, second),
            listOf(second, secondFileLevel, first, firstFileLevel).sortedWith(ThreadOrder.byAnchor),
        )
    }
}
