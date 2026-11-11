package dev.marginalis.core

import java.time.Instant

class WalkthroughGroup(
    val walkthroughId: String?,
    val label: String?,
    val startedAt: Instant,
    val total: Int,
    val steps: List<CommentThread>,
    val title: String,
)

class ListedSection(val walkthroughs: List<WalkthroughGroup>, val comments: List<CommentThread>)

class ThreadListing(
    val guided: List<WalkthroughGroup>,
    val open: List<CommentThread>,
    val orphaned: ListedSection,
    val resolved: ListedSection,
) {
    companion object {
        fun of(
            shown: List<CommentThread>,
            all: List<CommentThread> = shown,
            startTime: (Instant) -> String = Instant::toString,
        ): ThreadListing {
            val wholeWalkthroughs = all.filter { it.order != null }.groupBy { it.walkthroughId }
            val open = shown.filter { it.status is ThreadStatus.Open }
            return ThreadListing(
                guided = groupsOf(open.filter { it.order != null }, wholeWalkthroughs, startTime),
                open = open.filter { it.order == null },
                orphaned = sectionOf(shown.filter { it.status is ThreadStatus.Orphaned }, wholeWalkthroughs, startTime),
                resolved = sectionOf(shown.filter { it.status is ThreadStatus.Resolved }, wholeWalkthroughs, startTime),
            )
        }

        fun titleOf(threads: List<CommentThread>, thread: CommentThread, startTime: (Instant) -> String): String {
            val listing = of(threads, threads, startTime)
            val section = when (thread.status) {
                is ThreadStatus.Open -> listing.guided
                is ThreadStatus.Resolved -> listing.resolved.walkthroughs
                is ThreadStatus.Orphaned -> listing.orphaned.walkthroughs
            }
            return section.firstOrNull { group -> group.steps.any { it.id == thread.id } }?.title ?: Walkthrough.title(thread.walkthrough)
        }

        private fun sectionOf(
            threads: List<CommentThread>,
            wholeWalkthroughs: Map<String?, List<CommentThread>>,
            startTime: (Instant) -> String,
        ): ListedSection {
            val (steps, comments) = threads.partition { it.order != null }
            return ListedSection(groupsOf(steps, wholeWalkthroughs, startTime), comments)
        }

        private fun groupsOf(
            steps: List<CommentThread>,
            wholeWalkthroughs: Map<String?, List<CommentThread>>,
            startTime: (Instant) -> String,
        ): List<WalkthroughGroup> {
            val walkthroughs = steps.groupBy { it.walkthroughId }.map { (id, shownSteps) ->
                val whole = wholeWalkthroughs[id].orEmpty() + shownSteps
                Gathered(id, shownSteps.first().walkthrough, whole.minOf { it.createdAt }, whole.maxOf { it.order!! }, shownSteps)
            }.sortedWith(compareBy({ it.label.orEmpty() }, { it.startedAt }))
            val titles = toldApart(walkthroughs.map { Walkthrough.title(it.label) }) { index, plain ->
                "$plain · ${startTime(walkthroughs[index].startedAt)}"
            }.let { stamped -> toldApart(stamped) { index, title -> "$title (${stamped.take(index + 1).count { it == title }})" } }
            return walkthroughs.mapIndexed { index, it ->
                WalkthroughGroup(it.id, it.label, it.startedAt, it.total, it.steps.sortedWith(Walkthrough.byStepNumber), titles[index])
            }
        }

        private fun toldApart(titles: List<String>, distinguish: (Int, String) -> String): List<String> {
            val shared = titles.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            return titles.mapIndexed { index, title -> if (title in shared) distinguish(index, title) else title }
        }

        private class Gathered(
            val id: String?,
            val label: String?,
            val startedAt: Instant,
            val total: Int,
            val steps: List<CommentThread>,
        )
    }
}
