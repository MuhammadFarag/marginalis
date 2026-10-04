package dev.marginalis.core

class NewTags {
    private val taggedThisLook = HashMap<String, MutableSet<String>>()

    fun tagsOnSight(thread: CommentThread): Set<String> {
        val tags = taggedThisLook.getOrPut(thread.id) { LinkedHashSet() }
        tags.retainAll(thread.messages.mapTo(HashSet()) { it.id })
        thread.messages.filter { !it.readByUser }.mapTo(tags) { it.id }
        return tags.toSet()
    }

    fun endLook(threadId: String) {
        taggedThisLook.remove(threadId)
    }

    fun endLook() {
        taggedThisLook.clear()
    }
}
