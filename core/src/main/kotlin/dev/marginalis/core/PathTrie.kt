package dev.marginalis.core

import java.util.SortedMap
import java.util.TreeMap

/**
 * The canonical "which file comes first" rule, in both of its shapes: as a
 * tree ([insert] then walk `dirs` before `files`) and as a comparator
 * ([pathOrder]). At each level all directories sort before all files, then
 * by name — so the tool window's tree and the step-by-step walks visit
 * files in the same sequence because they share this one rule, not because
 * two implementations happen to agree.
 */
class PathTrie {
    val dirs: SortedMap<String, PathTrie> = TreeMap()
    val files: SortedMap<String, MutableList<CommentThread>> = TreeMap()

    /**
     * File the thread under its path. A project-level thread has no path and
     * so belongs to no node of this tree — it is not part of the file
     * hierarchy at all, and its surfaces render it beside the root.
     */
    fun insert(thread: CommentThread) {
        val parts = (thread.file ?: return).split('/')
        var node = this
        for (dir in parts.dropLast(1)) node = node.dirs.getOrPut(dir) { PathTrie() }
        node.files.getOrPut(parts.last()) { mutableListOf() }.add(thread)
    }

    fun threadCount(): Int = files.values.sumOf { it.size } + dirs.values.sumOf { it.threadCount() }

    /** Earliest walkthrough step beneath this node — lets guided trees read in walkthrough order. */
    fun minOrder(): Int = minOf(
        files.values.flatten().mapNotNull { it.order }.minOrNull() ?: Int.MAX_VALUE,
        dirs.values.minOfOrNull { it.minOrder() } ?: Int.MAX_VALUE,
    )

    companion object {
        /** The same rule as a comparator over project-relative paths. */
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
