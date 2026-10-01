package dev.marginalis.core

/**
 * Content only, no offsets, by design: positions are recomputed from the quote
 * on every attach, so a segment can't go stale the way numbers do.
 */
data class Segment(
    val exact: String,
    /** Up to [AnchorPolicy.SEGMENT_CONTEXT] chars before the selection on its line. */
    val prefix: String = "",
    /** Up to [AnchorPolicy.SEGMENT_CONTEXT] chars after the selection on its line. */
    val suffix: String = "",
)
