package dev.marginalis.core

/** Every surface that lists threads must sort with this, so they agree by construction. */
object ThreadOrder {

    val byAnchor: Comparator<CommentThread> =
        Comparator<CommentThread> { a, b -> pathOrder(a.file, b.file) }
            // Null sorts first, which is exactly the rule: no line, read first.
            .thenBy { it.line }
            .thenBy { it.createdAt }

    private fun pathOrder(a: String?, b: String?): Int = when {
        a == null && b == null -> 0
        a == null -> -1
        b == null -> 1
        else -> PathTrie.pathOrder(a, b)
    }
}
