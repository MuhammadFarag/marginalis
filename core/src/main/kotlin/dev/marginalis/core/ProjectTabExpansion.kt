package dev.marginalis.core

class ProjectTabExpansion(private val holdsDraft: (String) -> Boolean = { false }) {
    private val defaults = HashMap<String, Boolean>()
    private val overrides = HashMap<String, Boolean>()
    private val lastTurns = HashMap<String, Turn?>()
    private val memberIds = HashMap<String, List<String>>()

    fun observe(entries: List<ProjectTabLayout.Entry>, expandOnYourMove: Boolean) {
        forgetAllBut(entries.flatMapTo(HashSet()) { entry -> entry.threads.map { it.id } + entry.key })
        for (entry in entries) {
            memberIds[entry.key] = entry.threads.map { it.id }
            when (entry) {
                is ProjectTabLayout.Entry.Single -> follow(entry.thread, expandOnYourMove)
                is ProjectTabLayout.Entry.Conversation -> {
                    defaults[entry.key] = false
                    entry.threads.forEach { follow(it, expandOnYourMove) }
                }
            }
        }
    }

    fun isExpanded(key: String): Boolean =
        (memberIds[key] ?: listOf(key)).any(holdsDraft) || overrides[key] ?: defaults[key] ?: false

    fun toggle(key: String) {
        overrides[key] = !isExpanded(key)
    }

    fun reveal(key: String) {
        overrides[key] = true
        memberIds.filterValues { key in it }.keys.forEach { overrides[it] = true }
    }

    private fun forgetAllBut(present: Set<String>) {
        listOf(defaults, overrides, lastTurns, memberIds).forEach { it.keys.retainAll(present) }
    }

    private fun follow(thread: CommentThread, expandOnYourMove: Boolean) {
        val key = thread.id
        memberIds.putIfAbsent(key, listOf(key))
        val wasExpanded = isExpanded(key)
        val turn = thread.turn()
        val turnChanged = key in lastTurns && lastTurns[key] != turn
        lastTurns[key] = turn
        val yourMove = turn == Turn.USER_OWES && !thread.isRelayedRoot
        defaults[key] = yourMove
        if (turnChanged) overrides[key] = (yourMove && expandOnYourMove) || wasExpanded
    }
}
