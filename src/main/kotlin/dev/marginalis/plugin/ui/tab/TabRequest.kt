package dev.marginalis.plugin.ui.tab

import dev.marginalis.core.Message

sealed interface TabRequest {
    data class Reveal(val threadId: String, val message: Message?) : TabRequest
    data object DraftNew : TabRequest
}
