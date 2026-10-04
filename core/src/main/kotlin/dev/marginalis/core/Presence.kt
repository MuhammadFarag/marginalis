package dev.marginalis.core

import java.time.Instant

data class Presence(val agent: Author.Agent, val state: State, val since: Instant, val staysListening: Boolean) {
    enum class State { LISTENING, WORKING }
}
