package dev.marginalis.core

data class Elsewhere(val count: Int, val first: CommentThread) {
    companion object {
        fun of(threads: List<CommentThread>): Elsewhere? {
            val awaitingYou = threads.filter { !it.isProjectLevel && it.turn() == Turn.USER_OWES }
            return awaitingYou.minWithOrNull(ThreadOrder.byAnchor)?.let { Elsewhere(awaitingYou.size, it) }
        }
    }
}
