package dev.marginalis.core

/**
 * The walking rules: which threads form a walk, in what order, and how a
 * step's (n/total) denominator stays stable. Pure ordering policy over
 * threads — navigation and rendering are adapter concerns, but every
 * surface that walks (thread panel buttons, tool window) must agree on the
 * sequence, so the rules live here, once. A walkthrough step walks its own
 * walkthrough in step order (never bleeding into a neighboring
 * walkthrough); an unordered thread walks every open thread in the canonical
 * reading order (see [ThreadOrder.byAnchor]).
 */
object Walkthrough {

    /** The walk containing [thread] within [threads], and its position in it (-1 = not a member, e.g. resolved). */
    fun walkFrom(threads: List<CommentThread>, thread: CommentThread): Pair<List<CommentThread>, Int> {
        val open = threads.filter { it.status is ThreadStatus.Open }
        val walk = if (thread.order != null) {
            open.filter { it.order != null && (it.walkthrough ?: "") == (thread.walkthrough ?: "") }
                .sortedWith(compareBy({ it.order }, { it.createdAt }))
        } else {
            open.sortedWith(ThreadOrder.byAnchor)
        }
        return walk to walk.indexOfFirst { it.id == thread.id }
    }

    /**
     * The fixed denominator for a step's (n/total). A label alone can't
     * identify one walkthrough — every unlabeled walkthrough ever run
     * shares "" — so the cohort is same-label ordered threads created
     * at-or-after the earliest still-open step: finished walkthroughs
     * predate that and drop out; steps resolved mid-walk (created
     * together) stay counted. Null when [thread] isn't an ordered step or
     * its walkthrough has no open steps.
     */
    fun stableTotal(threads: List<CommentThread>, thread: CommentThread): Int? {
        if (thread.order == null) return null
        val label = thread.walkthrough ?: ""
        val sameLabel = threads.filter { it.order != null && (it.walkthrough ?: "") == label }
        val earliestOpen = sameLabel.filter { it.status is ThreadStatus.Open }
            .minOfOrNull { it.createdAt } ?: return null
        return sameLabel.filter { it.createdAt >= earliestOpen }.maxOf { it.order!! }
    }
}
