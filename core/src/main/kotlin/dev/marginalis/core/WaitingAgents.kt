package dev.marginalis.core

object WaitingAgents {

    fun describe(names: List<String>): String? = when (names.size) {
        0 -> null
        1 -> "${names.single()} is waiting"
        else -> "${names.dropLast(1).joinToString(", ")} and ${names.last()} are waiting"
    }

    fun handBackTooltip(names: List<String>): String =
        describe(names)?.let { "$it — hand back" }
            ?: "No agent is waiting — your hand back will be picked up when one starts"
}
