package dev.marginalis.core

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubAvatarsTest {

    private val policy = GitHubAvatarCachePolicy()
    private val storedAt = Instant.parse("2026-10-01T12:00:00Z")

    @Test
    fun `a login's avatar is its GitHub png at 64 pixels`() {
        assertEquals("https://github.com/octocat.png?size=64", GitHubAvatars.url("octocat", null))
        assertEquals("https://github.com/octocat.png?size=64", GitHubAvatars.url("@OctoCat", null))
    }

    @Test
    fun `a relayed avatar_url is preferred, sized by its s parameter`() {
        assertEquals(
            "https://avatars.githubusercontent.com/u/583231?v=4&s=64",
            GitHubAvatars.url("octocat", "https://avatars.githubusercontent.com/u/583231?v=4"),
        )
        assertEquals(
            "https://avatars.githubusercontent.com/in/29110?s=64",
            GitHubAvatars.url("dependabot[bot]", "https://avatars.githubusercontent.com/in/29110"),
        )
        assertEquals(
            "https://avatars.githubusercontent.com/u/1?v=4&s=64",
            GitHubAvatars.url("octocat", "https://avatars.githubusercontent.com/u/1?s=460&v=4"),
        )
    }

    @Test
    fun `a relayed avatar_url off GitHub's https avatar host is ignored for the login's png`() {
        listOf(
            "http://avatars.githubusercontent.com/u/583231?v=4",
            "file:///etc/passwd",
            "https://tracker.example.com/pixel.png",
            "https://avatars.githubusercontent.com.evil.example/u/1",
            "https://user@avatars.githubusercontent.com:8443/u/1",
            "https://169.254.169.254/latest/meta-data",
            "not a url",
        ).forEach { stored ->
            assertEquals("https://github.com/octocat.png?size=64", GitHubAvatars.url("octocat", stored), stored)
        }
        assertNull(GitHubAvatars.url("dependabot[bot]", "http://avatars.githubusercontent.com/in/29110"))
    }

    @Test
    fun `a bot without a relayed avatar_url, or no login at all, has no GitHub avatar`() {
        assertNull(GitHubAvatars.url("dependabot[bot]", null))
        assertNull(GitHubAvatars.url(" ", null))
    }

    @Test
    fun `a GitHub avatar is fresh for seven days and fetched again after`() {
        assertFalse(policy.needsFetch(storedAt, storedAt.plus(Duration.ofDays(7)).minusSeconds(1)))
        assertTrue(policy.needsFetch(storedAt, storedAt.plus(Duration.ofDays(7))))
        assertTrue(GitHubAvatarCachePolicy(Duration.ofHours(1)).needsFetch(storedAt, storedAt.plus(Duration.ofHours(2))))
    }

    @Test
    fun `a stored GitHub avatar expires seven days after it was stored, whenever it is next asked for`() {
        assertEquals(storedAt.plus(Duration.ofDays(7)), policy.expiresAt(storedAt))
        assertEquals(storedAt.plus(Duration.ofHours(1)), GitHubAvatarCachePolicy(Duration.ofHours(1)).expiresAt(storedAt))
    }

    @Test
    fun `a missing GitHub avatar is remembered until it expires, then asked for again`() {
        val missingMarkedAt = storedAt

        assertFalse(policy.needsFetch(missingMarkedAt, missingMarkedAt.plus(Duration.ofDays(3))))
        assertTrue(policy.needsFetch(missingMarkedAt, missingMarkedAt.plus(Duration.ofDays(8))))
    }

    @Test
    fun `only a 404 marks a GitHub avatar missing`() {
        assertEquals(GitHubAvatarCachePolicy.FailedFetch.MARK_MISSING, policy.afterFailure(status = 404, staleKept = true))
        assertEquals(GitHubAvatarCachePolicy.FailedFetch.MARK_MISSING, policy.afterFailure(status = 404, staleKept = false))
    }

    @Test
    fun `a rate limit, server error or dropped connection falls back to the stale avatar`() {
        listOf(403, 429, 503, null).forEach { status ->
            assertEquals(GitHubAvatarCachePolicy.FailedFetch.USE_STALE, policy.afterFailure(status, staleKept = true), "$status")
        }
    }

    @Test
    fun `a transient failure with nothing stored is tried again after a short wait`() {
        listOf(403, 429, 503, null).forEach { status ->
            assertEquals(GitHubAvatarCachePolicy.FailedFetch.RETRY_LATER, policy.afterFailure(status, staleKept = false), "$status")
        }
        assertEquals(storedAt.plus(Duration.ofMinutes(5)), policy.retryAt(storedAt))
        assertEquals(storedAt.plusSeconds(30), GitHubAvatarCachePolicy(retryDelay = Duration.ofSeconds(30)).retryAt(storedAt))
    }

    @Test
    fun `a login never fetched is fetched`() {
        assertTrue(policy.needsFetch(null, storedAt))
    }

    @Test
    fun `a login maps to a stable, case-insensitive cache file name`() {
        assertEquals(GitHubAvatars.cacheName("octocat"), GitHubAvatars.cacheName("@OctoCat "))
        assertEquals("octocat", GitHubAvatars.cacheName("OctoCat"))
        assertNotEquals(GitHubAvatars.cacheName("octocat"), GitHubAvatars.cacheName("hubot"))
    }
}
