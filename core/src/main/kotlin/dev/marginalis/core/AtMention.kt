package dev.marginalis.core

object AtMention {

    fun startsAt(text: String, offset: Int): Boolean {
        if (text.getOrNull(offset) != '@') return false
        if (offset > 0 && !text[offset - 1].isWhitespace()) return false
        return CodeFences.find(text).none { offset in it.start until it.end }
    }
}
