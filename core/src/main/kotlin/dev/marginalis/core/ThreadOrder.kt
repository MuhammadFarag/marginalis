package dev.marginalis.core

/**
 * The canonical reading order for a set of threads: widest subject first.
 * Project-level threads lead, then each file in directory-tree order
 * ([PathTrie.pathOrder]), and within a file the file-level threads before
 * the ones pinned to lines, then down the file, then oldest first.
 *
 * The rule is one idea — a thread about everything below it is read before
 * the parts — and every surface that lists threads (the API's comment_list,
 * the tool window, an unordered walk) sorts with this comparator, so they
 * agree by construction rather than by coincidence.
 */
object ThreadOrder {

    val byAnchor: Comparator<CommentThread> =
        Comparator<CommentThread> { a, b -> pathOrder(a.file, b.file) }
            // Null sorts first, which is exactly the rule: no line, read first.
            .thenBy { it.line }
            .thenBy { it.createdAt }

    /** [PathTrie.pathOrder], with "no file at all" ahead of every path. */
    private fun pathOrder(a: String?, b: String?): Int = when {
        a == null && b == null -> 0
        a == null -> -1
        b == null -> 1
        else -> PathTrie.pathOrder(a, b)
    }
}
