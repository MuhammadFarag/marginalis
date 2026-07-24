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

    // ---------------------------------------------------------- segments

    private fun anchor(near: Int, anchorText: String, segment: Segment?) =
        AnchorPolicy.findAnchor(lines.size, { lines[it] }, near, anchorText, segment)

    @Test
    fun `unique segment resolves to its span`() {
        val start = AnchorPolicy.findSegmentStart("    prev, curr = 0, 1", Segment("curr"))
        assertEquals(10, start)
    }

    @Test
    fun `context disambiguates repeated exact text`() {
        // "curr" occurs three times; the suffix pins the middle one.
        val text = "        prev, curr = curr, prev + curr"
        assertEquals(21, AnchorPolicy.findSegmentStart(text, Segment("curr", prefix = "= ", suffix = ",")))
        // Bare quote falls back to the earliest occurrence.
        assertEquals(14, AnchorPolicy.findSegmentStart(text, Segment("curr")))
    }

    @Test
    fun `one-sided context beats none`() {
        val text = "        prev, curr = curr, prev + curr"
        assertEquals(34, AnchorPolicy.findSegmentStart(text, Segment("curr", prefix = "prev + ")))
    }

    @Test
    fun `absent exact text is an honest null`() {
        assertNull(AnchorPolicy.findSegmentStart("    return curr", Segment("velocity")))
        assertNull(AnchorPolicy.findSegmentStart("anything", Segment("")))
    }

    @Test
    fun `ladder rung 1 - segment found near the hint`() {
        val found = anchor(near = 3, anchorText = "prev, curr = 0, 1", segment = Segment("0, 1", prefix = "= "))
        assertEquals(AnchorPolicy.Anchor.Span(line = 3, start = 17, endExclusive = 21), found)
    }

    @Test
    fun `ladder rung 1 - segment survives a stale line hint`() {
        val found = anchor(near = 0, anchorText = "prev, curr = 0, 1", segment = Segment("0, 1", prefix = "= "))
        assertEquals(AnchorPolicy.Anchor.Span(line = 3, start = 17, endExclusive = 21), found)
    }

    @Test
    fun `ladder rung 2 - reworded span degrades to the line, not a cliff`() {
        // The span text is gone but the anchor line still matches.
        val found = anchor(near = 3, anchorText = "prev, curr", segment = Segment("initial seed"))
        assertEquals(AnchorPolicy.Anchor.Line(3), found)
    }

    @Test
    fun `ladder rung 3 - nothing matches is an honest null`() {
        assertNull(anchor(near = 0, anchorText = "vanished line", segment = Segment("vanished span")))
    }

    @Test
    fun `no segment behaves exactly like the line ladder`() {
        assertEquals(AnchorPolicy.Anchor.Line(4), anchor(near = 1, anchorText = "for _ in range(n - 1):", segment = null))
    }
}
