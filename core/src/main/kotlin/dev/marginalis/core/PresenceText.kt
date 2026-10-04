package dev.marginalis.core

import java.time.Duration
import java.time.Instant

object PresenceText {

    fun tooltip(presence: Presence, now: Instant, name: String = presence.agent.displayName): String = listOfNotNull(
        name,
        presence.state.name.lowercase(),
        elapsed(Duration.between(presence.since, now)),
        "one-shot".takeUnless { presence.staysListening },
    ).joinToString(" · ")

    fun elapsed(duration: Duration): String {
        val minutes = duration.toMinutes()
        val hours = minutes / 60
        val pastTheHour = minutes % 60
        return when {
            minutes < 1 -> "just now"
            hours < 1 -> "$minutes min"
            pastTheHour == 0L -> "$hours h"
            else -> "$hours h $pastTheHour min"
        }
    }
}
