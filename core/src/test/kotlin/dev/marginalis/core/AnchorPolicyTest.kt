package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnchorPolicyTest {

    private val lines = listOf(
        "def fib(n: int) -> int:",
        "    if n < 2:",
        "        return n",
        "    prev, curr = 0, 1",
        "    for _ in range(n - 1):",
        "        prev, curr = curr, prev + curr",
        "    return curr",
    )

    private fun find(near: Int, anchor: String, window: Int = AnchorPolicy.SEARCH_WINDOW) =
        AnchorPolicy.findAnchorLine(lines.size, { lines[it] }, near, anchor, window)

    @Test
    fun `exact trimmed match`() {
        assertTrue(AnchorPolicy.lineMatches("    return curr", "return curr"))
        assertTrue(AnchorPolicy.lineMatches("return curr", "  return curr  "))
    }

    @Test
    fun `containment counts as a match`() {
        assertTrue(AnchorPolicy.lineMatches("    for _ in range(n - 1):", "range(n - 1)"))
    }

    @Test
    fun `blank anchor only matches blank lines`() {
        assertTrue(AnchorPolicy.lineMatches("   ", ""))
        assertEquals(false, AnchorPolicy.lineMatches("code", "  "))
    }

    @Test
    fun `correct hint returns the hinted line`() {
        assertEquals(0, find(near = 0, anchor = "def fib(n: int) -> int:"))
    }

    @Test
    fun `stale hint finds the nearest matching line`() {
        // The agent believed the loop was at line 1; it lives at line 4.
        assertEquals(4, find(near = 1, anchor = "for _ in range(n - 1):"))
    }

    @Test
    fun `nearest match wins when several lines match`() {
        // "prev, curr" appears at lines 3 and 5; hint 5 must not jump to 3.
        assertEquals(5, find(near = 5, anchor = "prev, curr = curr, prev + curr"))
        assertEquals(3, find(near = 2, anchor = "prev, curr"))
    }

    @Test
    fun `no match inside the window is an honest null`() {
        assertNull(find(near = 0, anchor = "this text is nowhere"))
    }

    @Test
    fun `window limits the search`() {
        assertNull(find(near = 0, anchor = "return curr", window = 2))
        assertEquals(6, find(near = 0, anchor = "return curr", window = 6))
    }

    @Test
    fun `hint outside the document still searches its window`() {
        assertEquals(6, find(near = 20, anchor = "return curr"))
    }
}
