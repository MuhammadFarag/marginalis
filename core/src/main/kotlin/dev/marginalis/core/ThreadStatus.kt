package dev.marginalis.core

sealed interface ThreadStatus {
    data object Open : ThreadStatus
    /** The outcome is in the code, or explicitly needs none. */
    data class Resolved(val by: Author) : ThreadStatus
    data object Orphaned : ThreadStatus

    enum class Kind { OPEN, RESOLVED, ORPHANED }

    val kind: Kind
        get() = when (this) {
            is Open -> Kind.OPEN
            is Resolved -> Kind.RESOLVED
            is Orphaned -> Kind.ORPHANED
        }
}
