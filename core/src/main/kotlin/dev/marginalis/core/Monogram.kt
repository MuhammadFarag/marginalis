package dev.marginalis.core

object Monogram {
    private val WORD_BREAK = Regex("[\\s._-]+")

    fun initials(name: String): String =
        name.trim().removeSuffix(GitHubLogin.BOT_SUFFIX)
            .split(WORD_BREAK)
            .mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
            .take(2)
            .joinToString("") { it.uppercaseChar().toString() }
            .ifEmpty { "?" }
}
