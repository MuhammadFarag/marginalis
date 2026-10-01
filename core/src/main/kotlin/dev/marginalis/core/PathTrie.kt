package dev.marginalis.core

import java.util.SortedMap
import java.util.TreeMap

/**
 * The one file-order rule, as a tree (walk `dirs` before `files`) and as a
 * comparator ([pathOrder]); the tool window and the walks must share it so
 * they visit files in the same sequence.
 */
class PathTrie {
    val dirs: SortedMap<String, PathTrie> = TreeMap()
    val files: SortedMap<String, MutableList<CommentThread>> = TreeMap()

    fun insert(thread: CommentThread) {
        val parts = (thread.file ?: return).split('/')
        var node = this
        for (dir in parts.dropLast(1)) node = node.dirs.getOrPut(dir) { PathTrie() }
        node.files.getOrPut(parts.last()) { mutableListOf() }.add(thread)
    }

    fun threadCount(): Int = files.values.sumOf { it.size } + dirs.values.sumOf { it.threadCount() }

    fun minOrder(): Int = minOf(
        files.values.flatten().mapNotNull { it.order }.minOrNull() ?: Int.MAX_VALUE,
        dirs.values.minOfOrNull { it.minOrder() } ?: Int.MAX_VALUE,
    )

    companion object {
        fun pathOrder(a: String, b: String): Int {
            val pa = a.split('/')
            val pb = b.split('/')
            for (i in 0 until minOf(pa.size, pb.size)) {
                val aIsDir = i < pa.size - 1
                val bIsDir = i < pb.size - 1
                if (aIsDir != bIsDir) return if (aIsDir) -1 else 1
                val byName = pa[i].compareTo(pb[i])
                if (byName != 0) return byName
            }
            return pa.size - pb.size
        }
    }
}
