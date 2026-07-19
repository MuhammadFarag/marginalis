package dev.marginalis.core

/**
 * Thread lifecycle as a sealed hierarchy: the resolver travels inside the
 * Resolved state, so "open but with a resolver" is unrepresentable.
 *
 * OPEN means unfinished conversation. RESOLVED means the outcome is in the
 * code, or explicitly needs none — which is why editing a file demands its
 * open threads be driven to resolution first. ORPHANED means the anchored
 * code no longer exists; the conversation is kept, not silently dropped.
 */
sealed interface ThreadStatus {
    data object Open : ThreadStatus
    data class Resolved(val by: Author) : ThreadStatus
    data object Orphaned : ThreadStatus

    /** Projection for filtering and serialization. */
    enum class Kind { OPEN, RESOLVED, ORPHANED }

    val kind: Kind
        get() = when (this) {
            is Open -> Kind.OPEN
            is Resolved -> Kind.RESOLVED
            is Orphaned -> Kind.ORPHANED
        }
}
