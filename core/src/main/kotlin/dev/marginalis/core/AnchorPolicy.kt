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
    const val SEGMENT_CONTEXT = 32

    /** Where a thread landed: a character span on a line, or just the line. */
    sealed interface Anchor {
        val line: Int

        /** [start] inclusive, [endExclusive] exclusive, both within the line. */
        data class Span(override val line: Int, val start: Int, val endExclusive: Int) : Anchor
        data class Line(override val line: Int) : Anchor
    }

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
        candidateLines(lineCount, nearLine, window)
            .firstOrNull { line -> lineMatches(lineTextAt(line), anchorText) }

    /**
     * Start of [segment] within one line's text (its end is always
     * `start + exact.length`). Several occurrences of the exact text are
     * disambiguated by context — full prefix+suffix beats one-sided beats
     * bare, earliest occurrence breaks ties. Null when the exact text does
     * not occur at all.
     */
    fun findSegmentStart(lineText: String, segment: Segment): Int? {
        if (segment.exact.isEmpty()) return null
        var bestStart = -1
        var bestScore = -1
        var from = 0
        while (true) {
            val at = lineText.indexOf(segment.exact, from)
            if (at < 0) break
            val end = at + segment.exact.length
            var score = 0
            if (segment.prefix.isNotEmpty() && lineText.take(at).endsWith(segment.prefix)) score++
            if (segment.suffix.isNotEmpty() && lineText.substring(end).startsWith(segment.suffix)) score++
            if (score > bestScore) {
                bestScore = score
                bestStart = at
            }
            from = at + 1
        }
        return bestStart.takeIf { it >= 0 }
    }

    /**
     * The degradation ladder: the segment's quote near the hint, else the
     * anchor line (hint first, then the window), else an honest null. A
     * reworded span degrades to a line comment, not a cliff; only a vanished
     * line is the caller's cue to orphan.
     */
    fun findAnchor(
        lineCount: Int,
        lineTextAt: (Int) -> String,
        nearLine: Int,
        anchorText: String,
        segment: Segment? = null,
        window: Int = SEARCH_WINDOW,
    ): Anchor? {
        if (segment != null) {
            candidateLines(lineCount, nearLine, window)
                .firstNotNullOfOrNull { line ->
                    findSegmentStart(lineTextAt(line), segment)?.let { start ->
                        Anchor.Span(line, start, start + segment.exact.length)
                    }
                }
                ?.let { return it }
        }
        return findAnchorLine(lineCount, lineTextAt, nearLine, anchorText, window)
            ?.let { Anchor.Line(it) }
    }

    /** Outcome of [resolveHint] — where the hint landed, or why it honestly couldn't. */
    sealed interface HintResolution {
        /** [adjusted]: the anchor was found, but not where the hint said. */
        data class Placed(val line: Int, val adjusted: Boolean) : HintResolution
        data class OutOfRange(val lineCount: Int) : HintResolution
        data object NoMatch : HintResolution
    }

    /**
     * The full anchoring contract for a (line hint, anchor text) pair, as
     * used by every operation that places or moves a thread: the hint may
     * be stale, anchor text is the truth. In range and matching (or no
     * anchor text to verify against — an in-range hint is then taken at
     * its word): placed as hinted. Matching elsewhere in the window:
     * placed with [HintResolution.Placed.adjusted]. Otherwise the caller
     * gets an honest failure to relay — never a silent wrong line.
     * [hintLine] is 0-based.
     */
    fun resolveHint(
        lineCount: Int,
        lineTextAt: (Int) -> String,
        hintLine: Int,
        anchorText: String?,
        window: Int = SEARCH_WINDOW,
    ): HintResolution {
        if (hintLine < 0 || hintLine >= lineCount) return HintResolution.OutOfRange(lineCount)
        if (anchorText != null && !lineMatches(lineTextAt(hintLine), anchorText)) {
            val found = findAnchorLine(lineCount, lineTextAt, hintLine, anchorText, window)
                ?: return HintResolution.NoMatch
            return HintResolution.Placed(found, adjusted = true)
        }
        return HintResolution.Placed(hintLine, adjusted = false)
    }

    /** The window's lines in preference order: nearest to the hint first. */
    private fun candidateLines(lineCount: Int, nearLine: Int, window: Int): List<Int> =
        ((nearLine - window)..(nearLine + window))
            .filter { it in 0 until lineCount }
            .sortedBy { abs(it - nearLine) }
}
