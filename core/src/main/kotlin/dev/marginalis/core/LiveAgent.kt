package dev.marginalis.core

object LiveAgent {

    fun keyOf(messages: List<Message>, to: Addressee?, waiting: List<Author.Agent> = emptyList()): String? {
        val spoken = messages.filter { it.relayed == null }
        val userAddressed = spoken.lastOrNull()?.takeIf { it.author is Author.User }?.to
        return (to as? Addressee.Agent)?.key
            ?: (userAddressed as? Addressee.Agent)?.key
            ?: (spoken.lastOrNull { it.author is Author.Agent } ?: messages.lastOrNull { it.author is Author.Agent })
                ?.let { (it.author as Author.Agent).receiptKey }
            ?: waiting.map { it.receiptKey }.distinct().singleOrNull()
    }
}
