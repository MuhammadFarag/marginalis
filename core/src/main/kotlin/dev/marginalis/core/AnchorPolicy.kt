package dev.marginalis.core

import kotlin.math.abs

/**
 * Anchor text is the truth; the line number is only a hint. Never silently
 * anchor to the wrong line.
 */
object AnchorPolicy {

    const val SEARCH_WINDOW = 20
    const val SEGMENT_CONTEXT = 32

    sealed interface Anchor {
        val line: Int

        data class Span(override val line: Int, val start: Int, val endExclusive: Int) : Anchor
        data class Line(override val line: Int) : Anchor
    }

    fun lineMatches(actualLineText: String, anchorText: String): Boolean {
        val actual = actualLineText.trim()
        val expected = anchorText.trim()
        if (expected.isEmpty()) return actual.isEmpty()
        return actual == expected || actual.contains(expected)
    }

    /** Lines are 0-based. */
    fun findAnchorLine(
        lineCount: Int,
        lineTextAt: (Int) -> String,
        nearLine: Int,
        anchorText: String,
        window: Int = SEARCH_WINDOW,
    ): Int? =
        candidateLines(lineCount, nearLine, window)
            .firstOrNull { line -> lineMatches(lineTextAt(line), anchorText) }

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

    /** A reworded span degrades to a line anchor; only a vanished line (null) is the caller's cue to orphan. */
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

    sealed interface HintResolution {
        data class Placed(val line: Int, val adjusted: Boolean) : HintResolution
        data class OutOfRange(val lineCount: Int) : HintResolution
        data object NoMatch : HintResolution
    }

    /** [hintLine] is 0-based. */
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

    private fun candidateLines(lineCount: Int, nearLine: Int, window: Int): List<Int> =
        ((nearLine - window)..(nearLine + window))
            .filter { it in 0 until lineCount }
            .sortedBy { abs(it - nearLine) }
}
