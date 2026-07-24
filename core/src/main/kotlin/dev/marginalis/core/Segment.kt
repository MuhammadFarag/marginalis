package dev.marginalis.core

/**
 * A span within the anchor line — the quote selector (W3C TextQuoteSelector
 * prior art): the selected text plus its immediate within-line context,
 * captured from the live selection at creation, never guessed.
 *
 * Content only, no offsets: positions are recomputed from the quote on
 * every attach, so a segment cannot go stale the way numbers do — it either
 * re-finds its text or degrades to a line anchor (see [AnchorPolicy]).
 */
data class Segment(
    /** The selected text itself. */
    val exact: String,
    /** Up to [AnchorPolicy.SEGMENT_CONTEXT] chars before the selection on its line. */
    val prefix: String = "",
    /** Up to [AnchorPolicy.SEGMENT_CONTEXT] chars after the selection on its line. */
    val suffix: String = "",
)
