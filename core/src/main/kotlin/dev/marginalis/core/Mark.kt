package dev.marginalis.core

enum class MarkSubject { LINE, FILE, PROJECT }

data class Mark(val subject: MarkSubject, val intent: Intent?) {
    companion object {
        fun of(threads: List<CommentThread>): Mark = Mark(
            subject = threads.minOf { it.subject },
            intent = threads.map { it.intent }.distinct().singleOrNull(),
        )

        fun all(): List<Mark> =
            MarkSubject.entries.flatMap { subject -> (listOf(null) + Intent.entries).map { Mark(subject, it) } }

        private val CommentThread.subject: MarkSubject
            get() = when {
                isProjectLevel -> MarkSubject.PROJECT
                isFileLevel -> MarkSubject.FILE
                else -> MarkSubject.LINE
            }
    }
}
