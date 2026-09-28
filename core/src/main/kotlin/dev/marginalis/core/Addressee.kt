package dev.marginalis.core

sealed interface Addressee {
    val wire: String

    data object User : Addressee {
        override val wire = "user"
    }

    data class Agent(val key: String) : Addressee {
        override val wire: String get() = key
    }

    companion object {
        fun parse(raw: String?): Parsed<Addressee?> = parseTrimmed(raw?.trim())

        private fun parseTrimmed(raw: String?): Parsed<Addressee?> = when {
            raw == null -> Parsed.Ok(null)
            raw.isEmpty() -> Parsed.Invalid(
                "'to' is blank — pass one author_id as comment_identities lists it, or 'user' for the user; " +
                    "omit it to address everyone.",
            )
            raw.equals(User.wire, ignoreCase = true) -> Parsed.Ok(User)
            else -> Parsed.Ok(Agent(raw))
        }

        fun parseLenient(raw: String?): Addressee? = parse(raw).getOrElse { null }
    }
}
