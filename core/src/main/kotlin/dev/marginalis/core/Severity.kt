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
enum class Severity {
    BLOCKER, NIT;

    /** Outcome of [parse]: a valid value ([Ok.severity] null = deliberately unmarked) or a teachable rejection. */
    sealed interface Parsed {
        data class Ok(val severity: Severity?) : Parsed
        data class Invalid(val reason: String) : Parsed
    }

    companion object {
        /**
         * The severity vocabulary — deliberately just the two ends, no
         * aliases. An unknown value is [Parsed.Invalid], never a silently
         * unmarked thread: the rejection is what tells a misinformed agent
         * to correct course (the skill teaches the vocabulary; the API
         * enforces it).
         */
        fun parse(raw: String?): Parsed {
            if (raw == null) return Parsed.Ok(null)
            return when (raw.lowercase()) {
                "blocker" -> Parsed.Ok(BLOCKER)
                "nit" -> Parsed.Ok(NIT)
                else -> Parsed.Invalid(
                    "invalid severity '$raw' — use 'blocker' (act before proceeding) or 'nit' " +
                        "(taste, dismissible); omit for an ordinary comment.",
                )
            }
        }

        /** Persistence tolerance: unknown values load as unmarked rather than failing the whole file. */
        fun parseLenient(raw: String?): Severity? = (parse(raw) as? Parsed.Ok)?.severity
    }
}
