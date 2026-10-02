package dev.marginalis.core

import java.time.Instant

sealed interface Wake {
    val at: Instant
    val reason: String

    data class HandedBack(override val at: Instant, val liveThreadIds: List<String> = emptyList()) : Wake {
        override val reason: String get() = "hand_back"
    }

    data class Live(override val at: Instant, val threadIds: List<String>) : Wake {
        override val reason: String get() = "live"
    }
}
