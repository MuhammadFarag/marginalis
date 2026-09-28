package dev.marginalis.core

sealed interface Parsed<out T> {
    data class Ok<out T>(val value: T) : Parsed<T>
    data class Invalid(val reason: String) : Parsed<Nothing>
}

inline fun <T> Parsed<T>.getOrElse(onInvalid: (reason: String) -> T): T = when (this) {
    is Parsed.Ok -> value
    is Parsed.Invalid -> onInvalid(reason)
}
