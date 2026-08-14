package dev.marginalis.core

/**
 * What kind of response a thread is asking for — a gate, not a weight, the
 * same shape [Severity] has and deliberately independent of it.
 * FINDING: something is wrong here, and the thread ends by fixing it.
 * GUIDANCE: how the code around here should be written, ending by being
 * followed. QUESTION: an answer is genuinely wanted, ending in one.
 *
 * Omitted is the common case: an ordinary comment asks for nothing in
 * particular, and marking everything would make the marks meaningless. The
 * two vocabularies compose freely — a guidance blocker ("do NOT bring the
 * rejected approach back") is a legitimate and useful thing to say.
 */
enum class Intent {
    FINDING, GUIDANCE, QUESTION;

    /** Outcome of [parse]: a valid value ([Ok.intent] null = deliberately unmarked) or a teachable rejection. */
    sealed interface Parsed {
        data class Ok(val intent: Intent?) : Parsed
        data class Invalid(val reason: String) : Parsed
    }

    companion object {
        /**
         * The intent vocabulary — three words, no aliases and no synonyms.
         * An unknown value is [Parsed.Invalid], never a silently unmarked
         * thread: the rejection is what tells a misinformed agent to correct
         * course (severity's rule, and for the same reason).
         */
        fun parse(raw: String?): Parsed {
            if (raw == null) return Parsed.Ok(null)
            return when (raw.lowercase()) {
                "finding" -> Parsed.Ok(FINDING)
                "guidance" -> Parsed.Ok(GUIDANCE)
                "question" -> Parsed.Ok(QUESTION)
                else -> Parsed.Invalid(
                    "invalid intent '$raw' — use 'finding' (something to fix), 'guidance' (how to write the " +
                        "code around here) or 'question' (an answer is wanted); omit for an ordinary comment.",
                )
            }
        }

        /** Persistence tolerance: unknown values load as unmarked rather than failing the whole file. */
        fun parseLenient(raw: String?): Intent? = (parse(raw) as? Parsed.Ok)?.intent
    }
}
