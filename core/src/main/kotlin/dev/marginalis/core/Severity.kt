package dev.marginalis.core

/**
 * What response a thread asks of its reader — a gate, not a weight.
 * BLOCKER: act before this work proceeds. NIT: taste, dismiss guilt-free.
 * The middle of the scale is deliberately absent: an unmarked thread is an
 * ordinary comment, and only the ends of the scale change the reader's
 * behavior. Importance is not encoded anywhere — it lives in prose, where
 * it can be argued (see the decision log).
 *
 * Agent-side vocabulary only: spans are the human's precision, severity is
 * the agent's.
 */
enum class Severity { BLOCKER, NIT }
