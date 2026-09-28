package dev.marginalis.core

enum class SendOption {
    HAND_BACK,
    COMMENT_ON_FILE,
    COMMENT_ON_PROJECT;

    companion object {
        // Retargeting is offered only where it is still a choice — a thread
        // being started — and only upward: a reply belongs to the thread it is
        // in, and nothing widens past the project.
        fun offered(thread: CommentThread, isDraft: Boolean, isEditing: Boolean, anyoneWaiting: Boolean): List<SendOption> {
            val handBack = listOfNotNull(HAND_BACK.takeIf { anyoneWaiting })
            return when {
                isEditing || !isDraft || thread.isProjectLevel -> handBack
                thread.isFileLevel -> handBack + COMMENT_ON_PROJECT
                else -> handBack + COMMENT_ON_FILE + COMMENT_ON_PROJECT
            }
        }
    }
}
