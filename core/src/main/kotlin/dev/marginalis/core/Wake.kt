package dev.marginalis.core

import java.time.Instant

sealed interface Wake {
    val reason: String

    sealed interface Delivery : Wake {
        val at: Instant
    }

    data class HandedBack(override val at: Instant, val liveThreadIds: List<String> = emptyList()) : Delivery {
        override val reason: String get() = "hand_back"
    }

    data class Live(override val at: Instant, val threadIds: List<String>) : Delivery {
        override val reason: String get() = "live"
    }

    sealed class Ending(override val reason: String) : Wake

    data object TimedOut : Ending("timeout")

    data object Stopped : Ending("stopped")

    data object Closing : Ending("closing")
}
