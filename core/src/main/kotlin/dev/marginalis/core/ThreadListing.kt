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
            val wholeWalkthroughs = walkthroughsIn(all + shown)
            val titles = titlesOf(wholeWalkthroughs, startTime)
            val open = shown.filter { it.status is ThreadStatus.Open }
            return ThreadListing(
                guided = groupsOf(open.filter { it.order != null }, wholeWalkthroughs, titles),
                open = open.filter { it.order == null },
                orphaned = sectionOf(shown.filter { it.status is ThreadStatus.Orphaned }, wholeWalkthroughs, titles),
                resolved = sectionOf(shown.filter { it.status is ThreadStatus.Resolved }, wholeWalkthroughs, titles),
            )
        }

        fun titleOf(threads: List<CommentThread>, thread: CommentThread, startTime: (Instant) -> String): String {
            val title = thread.order?.let { titlesOf(walkthroughsIn(threads), startTime)[thread.walkthroughId] }
            return title ?: Walkthrough.title(thread.walkthrough)
        }

        private fun walkthroughsIn(threads: List<CommentThread>): Map<String?, List<CommentThread>> =
            threads.filter { it.order != null }.distinctBy { it.id }.groupBy { it.walkthroughId }

        private fun titlesOf(walkthroughs: Map<String?, List<CommentThread>>, startTime: (Instant) -> String): Map<String?, String> {
            val ordered = walkthroughs.map { (id, steps) -> Triple(id, steps.first().walkthrough, steps.minOf { it.createdAt }) }
                .sortedWith(compareBy({ (_, label, _) -> label.orEmpty() }, { (_, _, startedAt) -> startedAt }))
            val titles = toldApart(ordered.map { (_, label, _) -> Walkthrough.title(label) }) { index, plain ->
                "$plain · ${startTime(ordered[index].third)}"
            }.let { stamped -> toldApart(stamped) { index, title -> "$title (${stamped.take(index + 1).count { it == title }})" } }
            return ordered.mapIndexed { index, (id, _, _) -> id to titles[index] }.toMap()
        }

        private fun sectionOf(
            threads: List<CommentThread>,
            wholeWalkthroughs: Map<String?, List<CommentThread>>,
            titles: Map<String?, String>,
        ): ListedSection {
            val (steps, comments) = threads.partition { it.order != null }
            return ListedSection(groupsOf(steps, wholeWalkthroughs, titles), comments)
        }

        private fun groupsOf(
            steps: List<CommentThread>,
            wholeWalkthroughs: Map<String?, List<CommentThread>>,
            titles: Map<String?, String>,
        ): List<WalkthroughGroup> =
            steps.groupBy { it.walkthroughId }.map { (id, shownSteps) ->
                val whole = wholeWalkthroughs.getValue(id)
                WalkthroughGroup(
                    id, shownSteps.first().walkthrough, whole.minOf { it.createdAt }, whole.maxOf { it.order!! },
                    shownSteps.sortedWith(Walkthrough.byStepNumber), titles.getValue(id),
                )
            }.sortedWith(compareBy({ it.label.orEmpty() }, { it.startedAt }))

        private fun toldApart(titles: List<String>, distinguish: (Int, String) -> String): List<String> {
            val shared = titles.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            return titles.mapIndexed { index, title -> if (title in shared) distinguish(index, title) else title }
        }
    }
}
