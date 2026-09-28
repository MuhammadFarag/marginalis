package dev.marginalis.core

object WaitingAgents {

    fun describe(names: List<String>): String? = when (names.size) {
        0 -> null
        1 -> "${names.single()} is waiting"
        else -> "${names.dropLast(1).joinToString(", ")} and ${names.last()} are waiting"
    }

    fun handBackText(names: List<String>): String =
        "Hand Back — " + (describe(names) ?: "no agent is waiting; it will be picked up when one starts")
}
