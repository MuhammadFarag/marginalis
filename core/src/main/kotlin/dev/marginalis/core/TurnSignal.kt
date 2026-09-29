package dev.marginalis.core

object TurnSignal {
    fun spoken(turn: Turn): String = when (turn) {
        Turn.USER -> "your move"
        Turn.AGENT -> "agent's move"
    }

    fun spoken(tally: TurnTally): String = listOfNotNull(
        tally.user.takeIf { it > 0 }?.let { "${spoken(Turn.USER)} $it" },
        tally.agent.takeIf { it > 0 }?.let { "${spoken(Turn.AGENT)} $it" },
    ).joinToString(", ")
}

data class TurnTally(val user: Int, val agent: Int) {
    companion object {
        fun of(threads: List<CommentThread>): TurnTally {
            val turns = threads.mapNotNull { it.turn() }
            return TurnTally(user = turns.count { it == Turn.USER }, agent = turns.count { it == Turn.AGENT })
        }
    }
}
