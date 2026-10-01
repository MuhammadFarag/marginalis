package dev.marginalis.core

data class Classification(val intent: Intent?, val severity: Severity?, val label: String?) {
    companion object {
        fun parse(intent: String?, severity: String?, label: String?): Parsed<Classification> {
            val parsedIntent = Intent.parse(intent).getOrElse { return Parsed.Invalid(it) }
            val parsedSeverity = Severity.parse(severity).getOrElse { return Parsed.Invalid(it) }
            if (parsedIntent == Intent.FYI && parsedSeverity != null) return Parsed.Invalid(FYI_SEVERITY)
            val parsedLabel = FyiLabel.parse(label, parsedIntent).getOrElse { return Parsed.Invalid(it) }
            return Parsed.Ok(Classification(parsedIntent, parsedSeverity, parsedLabel))
        }

        private const val FYI_SEVERITY =
            "an fyi asks for nothing, so it carries no severity; use a finding if something should change, or " +
                "drop 'severity'."
    }
}
