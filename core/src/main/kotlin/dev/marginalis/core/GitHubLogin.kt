package dev.marginalis.core

internal object GitHubLogin {
    const val BOT_SUFFIX = "[bot]"

    fun bare(login: String): String = login.trim().removePrefix("@")
}
