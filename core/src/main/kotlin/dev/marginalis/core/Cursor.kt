package dev.marginalis.core

import java.time.Instant
import java.time.format.DateTimeParseException

object Cursor {

    fun parse(param: String, raw: String?, howToGetOne: String): Parsed<Instant?> {
        if (raw == null) return Parsed.Ok(null)
        return try {
            Parsed.Ok(Instant.parse(raw))
        } catch (e: DateTimeParseException) {
            Parsed.Invalid("'$param' must be an ISO-8601 instant (e.g. 2026-08-14T09:30:00Z) — $howToGetOne")
        }
    }
}
