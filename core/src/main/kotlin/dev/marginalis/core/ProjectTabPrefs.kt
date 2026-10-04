package dev.marginalis.core

enum class ListWhileInFront(val stored: String) {
    LIVE("LIVE"),
    HOLD_STILL("HOLD_STILL");

    companion object {
        fun fromStored(stored: String): ListWhileInFront = entries.firstOrNull { it.stored == stored } ?: LIVE
    }
}

enum class ReadWhen(val stored: String) {
    EXPANDED_IN_FRONT("EXPANDED_IN_FRONT"),
    CLICKED_INTO("CLICKED_INTO");

    fun reads(inFront: Boolean, expanded: Boolean, clickedInto: Boolean = false): Boolean =
        inFront && expanded && (this == EXPANDED_IN_FRONT || clickedInto)

    companion object {
        fun fromStored(stored: String): ReadWhen = entries.firstOrNull { it.stored == stored } ?: EXPANDED_IN_FRONT
    }
}

data class ProjectTabPrefs(
    val list: ListWhileInFront = ListWhileInFront.LIVE,
    val expandOnYourMove: Boolean = true,
    val readWhen: ReadWhen = ReadWhen.EXPANDED_IN_FRONT,
    val groupRelayed: Boolean = true,
)
