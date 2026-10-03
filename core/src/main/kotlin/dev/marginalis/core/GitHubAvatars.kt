package dev.marginalis.core

import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import java.time.Instant

object GitHubAvatars {
    private const val SIZE_PARAM = "s"
    private const val SIDE = 64
    private const val AVATAR_HOST = "avatars.githubusercontent.com"

    fun url(login: String, relayedAvatarUrl: String?): String? {
        relayedAvatarUrl?.takeIf(::isGitHubAvatar)?.let { return sized(it) }
        val key = cacheName(login)
        return if (key.isEmpty() || key.endsWith(GitHubLogin.BOT_SUFFIX)) null else "https://github.com/$key.png?size=$SIDE"
    }

    fun cacheName(login: String): String = People.Kind.PERSON.keyOf(login)

    private fun isGitHubAvatar(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return false
        }
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.rawUserInfo == null &&
            uri.port == -1 &&
            uri.host.equals(AVATAR_HOST, ignoreCase = true)
    }

    private fun sized(avatarUrl: String): String {
        val base = avatarUrl.substringBefore('?')
        val params = avatarUrl.substringAfter('?', "").split('&')
            .filter { it.isNotEmpty() && it.substringBefore('=') != SIZE_PARAM }
        return base + "?" + (params + "$SIZE_PARAM=$SIDE").joinToString("&")
    }
}

class GitHubAvatarCachePolicy(
    private val ttl: Duration = Duration.ofDays(7),
    private val retryDelay: Duration = Duration.ofMinutes(5),
) {
    enum class FailedFetch { MARK_MISSING, USE_STALE, RETRY_LATER }

    fun needsFetch(storedAt: Instant?, now: Instant): Boolean = storedAt == null || !now.isBefore(expiresAt(storedAt))

    fun expiresAt(storedAt: Instant): Instant = storedAt.plus(ttl)

    fun afterFailure(status: Int?, staleKept: Boolean): FailedFetch = when {
        status == NOT_FOUND -> FailedFetch.MARK_MISSING
        staleKept -> FailedFetch.USE_STALE
        else -> FailedFetch.RETRY_LATER
    }

    fun retryAt(failedAt: Instant): Instant = failedAt.plus(retryDelay)

    private companion object {
        const val NOT_FOUND = 404
    }
}
