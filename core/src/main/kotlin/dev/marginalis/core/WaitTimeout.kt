package dev.marginalis.core

import java.time.Duration

object WaitTimeout {
    val DEFAULT: Duration = Duration.ofHours(1)
    val CAP: Duration = Duration.ofHours(4)

    fun parse(raw: String?): Parsed<Duration> {
        if (raw == null) return Parsed.Ok(DEFAULT)
        if (raw.isEmpty() || !raw.all { it in '0'..'9' }) {
            return Parsed.Invalid(
                "invalid timeout '$raw' — a whole number of seconds (e.g. 3600); omit it for the default of " +
                    "${DEFAULT.seconds} seconds. Longer than ${CAP.seconds} seconds is capped.",
            )
        }
        val seconds = raw.toBigInteger()
        return Parsed.Ok(if (seconds > CAP.seconds.toBigInteger()) CAP else Duration.ofSeconds(seconds.toLong()))
    }
}
