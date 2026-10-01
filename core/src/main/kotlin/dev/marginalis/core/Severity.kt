package dev.marginalis.core

/**
 * A gate, not a weight: the middle of the scale is deliberately absent (an
 * unmarked thread is an ordinary comment). Set by agents only.
 */
enum class Severity {
    BLOCKER, NIT;

    companion object {
        /**
         * No aliases. Unknown values are rejected, never silently unmarked:
         * the rejection is what corrects a misinformed agent.
         */
        fun parse(raw: String?): Parsed<Severity?> {
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

        /** For persistence: an unknown value loads as unmarked rather than failing the whole file. */
        fun parseLenient(raw: String?): Severity? = parse(raw).getOrElse { null }
    }
}
