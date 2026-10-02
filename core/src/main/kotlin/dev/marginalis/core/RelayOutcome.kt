package dev.marginalis.core

sealed interface RelayOutcome {
    data class Added(val message: Message) : RelayOutcome
    data class Existing(val message: Message) : RelayOutcome
    data class Elsewhere(val thread: CommentThread) : RelayOutcome
    data object Deleted : RelayOutcome
}
