package dev.marginalis.core

/**
 * The canonical reading order for a set of threads: by file in directory-tree
 * order ([PathTrie.pathOrder]), then — within one file — file-level threads
 * before the threads pinned to lines, then down the file, then oldest first.
 *
 * A file-level thread is about everything below it, so it is read first; the
 * rest follow the code. Every surface that lists threads (the API's
 * comment_list, the tool window's file nodes, an unordered walk) sorts with
 * this comparator, so they agree by construction rather than by coincidence.
 */
object ThreadOrder {

    val byAnchor: Comparator<CommentThread> =
        Comparator<CommentThread> { a, b -> PathTrie.pathOrder(a.file, b.file) }
            // Null sorts first, which is exactly the rule: no line, read first.
            .thenBy { it.line }
            .thenBy { it.createdAt }
}
