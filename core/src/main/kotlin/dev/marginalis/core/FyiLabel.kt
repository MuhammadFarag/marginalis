package dev.marginalis.core

object FyiLabel {
    private const val MAX_LENGTH = 20
    private const val ECHO_LENGTH = 40
    private const val EXAMPLES = "'praise', 'copied', 'context' or 'heads-up'"
    private val SHAPE = Regex("^[a-z0-9]+(?:[ -][a-z0-9]+)*$")
    private val VOCABULARY = setOf("blocker", "nit", "finding", "guidance", "question", "fyi")

    fun parse(raw: String?, intent: Intent?): Parsed<String?> {
        if (raw == null) return Parsed.Ok(null)
        if (intent != Intent.FYI) {
            return Parsed.Invalid(
                "'label' names what kind of fyi this is; it goes with intent 'fyi' only. Drop 'label', or pass " +
                    "intent 'fyi' if nothing is owed.",
            )
        }
        val label = raw.lowercase()
        if (label in VOCABULARY) {
            return Parsed.Invalid(
                "label '$label' is already a word of the margin's vocabulary and would read as one — name the kind " +
                    "of fyi instead, e.g. $EXAMPLES; omit for a plain fyi.",
            )
        }
        if (label.length > MAX_LENGTH || !SHAPE.matches(label)) {
            return Parsed.Invalid(
                "invalid label '${echo(raw)}' — a word or two naming the kind of fyi, e.g. $EXAMPLES: a–z and 0–9, " +
                    "single spaces or hyphens between words, at most $MAX_LENGTH characters; omit for a plain fyi.",
            )
        }
        return Parsed.Ok(label)
    }

    private fun echo(raw: String): String = if (raw.length > ECHO_LENGTH) raw.take(ECHO_LENGTH) + "…" else raw

    fun parseLenient(raw: String?, intent: Intent?): String? = parse(raw, intent).getOrElse { null }
}
