package dev.marginalis.core

object ThreadFolding {

    private const val OPEN_AT_THE_END = 2
    private const val STACK_ABOVE = 3

    enum class Level {
        ALL_FOLDED, DEFAULT, ALL_EXPANDED;

        fun folded(): Level? = entries.getOrNull(ordinal - 1)

        fun expanded(): Level? = entries.getOrNull(ordinal + 1)
    }

    sealed interface Row {
        data class Open(val message: Message) : Row
        data class Folded(val message: Message) : Row
        data class Stack(val messages: List<Message>) : Row
    }

    data class View(
        val level: Level = Level.DEFAULT,
        val keptOpen: Set<String> = emptySet(),
        val editing: String? = null,
        val opened: Set<String> = emptySet(),
        val folded: Set<String> = emptySet(),
        val revealed: Set<String> = emptySet(),
    )

    fun rows(messages: List<Message>, view: View): List<Row> {
        val lastOpen = messages.filterNot(Message::agrees).takeLast(OPEN_AT_THE_END).mapTo(mutableSetOf()) { it.id }
        val rows = mutableListOf<Row>()
        val run = mutableListOf<Message>()
        fun endRun() {
            if (run.size > STACK_ABOVE) rows += Row.Stack(run.toList()) else run.mapTo(rows, Row::Folded)
            run.clear()
        }
        messages.forEachIndexed { index, message ->
            when {
                !folds(message, view, lastOpen) -> {
                    endRun()
                    rows += Row.Open(message)
                }
                index > 0 && stacks(message, view) -> run += message
                else -> {
                    endRun()
                    rows += Row.Folded(message)
                }
            }
        }
        endRun()
        return rows
    }

    private fun folds(message: Message, view: View, lastOpen: Set<String>): Boolean = when {
        message.agrees || message.id == view.editing || message.id in view.opened -> false
        message.id in view.folded -> true
        else -> when (view.level) {
            Level.ALL_FOLDED -> true
            Level.ALL_EXPANDED -> false
            Level.DEFAULT -> message.readByUser && message.id !in view.keptOpen && message.id !in lastOpen
        }
    }

    private fun stacks(message: Message, view: View): Boolean =
        view.level != Level.ALL_EXPANDED && message.id !in view.folded && message.id !in view.revealed
}
