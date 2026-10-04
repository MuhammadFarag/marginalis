package dev.marginalis.core

enum class SendOption {
    SEND_ROUND,
    COMMENT_ON_FILE,
    COMMENT_ON_PROJECT;

    companion object {
        fun offered(thread: CommentThread, isDraft: Boolean, isEditing: Boolean, anyoneListening: Boolean): List<SendOption> {
            val sendRound = listOfNotNull(SEND_ROUND.takeIf { anyoneListening })
            return when {
                isEditing || !isDraft || thread.isProjectLevel -> sendRound
                thread.isFileLevel -> sendRound + COMMENT_ON_PROJECT
                else -> sendRound + COMMENT_ON_FILE + COMMENT_ON_PROJECT
            }
        }
    }
}
