package dev.marginalis.core

sealed interface AvatarSource {
    data class Picture(val key: String) : AvatarSource

    data class GitHub(val cacheName: String, val url: String) : AvatarSource

    data object Monogram : AvatarSource
}

fun Face.sources(githubAvatars: Boolean): List<AvatarSource> = listOfNotNull(
    picture?.let(AvatarSource::Picture),
    login?.takeIf { githubAvatars }?.let { login ->
        GitHubAvatars.url(login, avatarUrl)?.let { url -> AvatarSource.GitHub(GitHubAvatars.cacheName(login), url) }
    },
    AvatarSource.Monogram,
)
