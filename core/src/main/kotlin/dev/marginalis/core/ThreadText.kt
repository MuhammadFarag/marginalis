package dev.marginalis.core

object ThreadText {

    fun matches(thread: CommentThread, filter: String, people: People = People(emptyList())): Boolean {
        val wanted = filter.trim()
        return wanted.isEmpty() || searchable(thread, people).any { it.contains(wanted, ignoreCase = true) }
    }

    fun matches(entry: ProjectTabLayout.Entry, filter: String, people: People = People(emptyList())): Boolean =
        entry.threads.any { matches(it, filter, people) }

    private fun searchable(thread: CommentThread, people: People): Sequence<String> =
        thread.messages.asSequence().flatMap { message ->
            sequenceOf(message.body, message.speaker(people), message.relayed?.name, message.relayed?.discussion)
        }.filterNotNull()
}
