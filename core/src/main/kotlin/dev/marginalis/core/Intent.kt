package dev.marginalis.core

/** Deliberately independent of [Severity]: the two compose freely (a guidance blocker is legitimate). */
enum class Intent {
    FINDING, GUIDANCE, QUESTION, FYI;

    companion object {
        /**
         * No aliases. Unknown values are rejected, never silently unmarked:
         * the rejection is what corrects a misinformed agent.
         */
        fun parse(raw: String?): Parsed<Intent?> {
            if (raw == null) return Parsed.Ok(null)
            return when (raw.lowercase()) {
                "finding" -> Parsed.Ok(FINDING)
                "guidance" -> Parsed.Ok(GUIDANCE)
                "question" -> Parsed.Ok(QUESTION)
                "fyi" -> Parsed.Ok(FYI)
                else -> Parsed.Invalid(
                    "invalid intent '$raw' — use 'finding' (something to fix), 'guidance' (how to write the " +
                        "code around here), 'question' (an answer is wanted) or 'fyi' (nothing is owed: praise, " +
                        "context, a heads-up); omit for an ordinary comment.",
                )
            }
        }

        /** For persistence: an unknown value loads as unmarked rather than failing the whole file. */
        fun parseLenient(raw: String?): Intent? = parse(raw).getOrElse { null }
    }
}
