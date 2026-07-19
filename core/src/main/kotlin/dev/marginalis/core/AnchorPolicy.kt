package dev.marginalis.core

import kotlin.math.abs

/**
 * The anchoring rules, shared by every path that places a thread on a line
 * (creation with a stale line number, re-anchoring after restart).
 *
 * Line numbers are not identity: the agent's picture of a file goes stale
 * the moment the user types, and files change while the IDE is closed. So
 * anchor text is the truth and the line number is a hint — match at the
 * hinted line, else search a small window around it by distance, else
 * honestly fail. Never silently anchor to the wrong line.
 */
object AnchorPolicy {

    const val SEARCH_WINDOW = 20

    fun lineMatches(actualLineText: String, anchorText: String): Boolean {
        val actual = actualLineText.trim()
        val expected = anchorText.trim()
        if (expected.isEmpty()) return actual.isEmpty()
        return actual == expected || actual.contains(expected)
    }

    /**
     * Find the anchor near [nearLine] in a document exposed as [lineTextAt]
     * over [lineCount] lines (0-based). Returns the matching line, preferring
     * the closest to the hint, or null when nothing in the window matches —
     * the caller should then ask for a fresh read of the file.
     */
    fun findAnchorLine(
        lineCount: Int,
        lineTextAt: (Int) -> String,
        nearLine: Int,
        anchorText: String,
        window: Int = SEARCH_WINDOW,
    ): Int? =
        ((nearLine - window)..(nearLine + window))
            .filter { it in 0 until lineCount }
            .sortedBy { abs(it - nearLine) }
            .firstOrNull { line -> lineMatches(lineTextAt(line), anchorText) }
}
