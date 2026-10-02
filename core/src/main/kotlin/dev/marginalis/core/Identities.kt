package dev.marginalis.core

sealed interface Identity {
    val messagesWritten: Int

    data class User(val name: String, override val messagesWritten: Int) : Identity

    data class Agent(
        val id: String,
        val name: String?,
        override val messagesWritten: Int,
        val unread: Int,
        val waiting: Boolean,
    ) : Identity
}

object Identities {

    fun of(threads: List<CommentThread>, user: Author.User, waiting: List<Author.Agent>): List<Identity> {
        val messages = threads.flatMap { it.messages }
        val written = writtenByKey(messages)
        val names = namesByKey(written)
        val waitingByKey = waiting.associateBy { it.receiptKey }
        val keys = written.keys + messages.flatMap { it.seenBy } + waitingByKey.keys

        val agents = keys.map { key ->
            Identity.Agent(
                id = key,
                name = names[key] ?: waitingByKey[key]?.displayName,
                messagesWritten = written[key].orEmpty().count { it.relayed == null },
                unread = threads.sumOf { it.unreadCountFor(key) },
                waiting = key in waitingByKey,
            )
        }.sortedWith(compareByDescending<Identity.Agent> { it.messagesWritten }.thenBy { it.id })

        return listOf(Identity.User(user.displayName, messages.count { it.author is Author.User })) + agents
    }

    fun namesByKey(threads: List<CommentThread>): Map<String, String> =
        namesByKey(writtenByKey(threads.flatMap { it.messages }))

    private fun writtenByKey(messages: List<Message>): Map<String, List<Message>> =
        messages.filter { it.author is Author.Agent }.groupBy { (it.author as Author.Agent).receiptKey }

    private fun namesByKey(written: Map<String, List<Message>>): Map<String, String> =
        written.mapValues { (_, wrote) -> wrote.maxBy { it.createdAt }.author.displayName }
}
