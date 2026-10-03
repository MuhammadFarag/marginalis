package dev.marginalis.core

sealed interface FaceKey {
    fun paletteIndex(size: Int): Int?

    data object User : FaceKey {
        override fun paletteIndex(size: Int): Int? = null
    }

    data class Agent(val id: String) : FaceKey {
        override fun paletteIndex(size: Int): Int =
            if (id == Author.Agent.ANONYMOUS_NAME) 0 else Math.floorMod(id.hashCode(), size)
    }

    @ConsistentCopyVisibility
    data class GitHub private constructor(val login: String) : FaceKey {
        override fun paletteIndex(size: Int): Int = Math.floorMod(login.hashCode(), size)

        companion object {
            operator fun invoke(login: String): GitHub = GitHub(People.Kind.PERSON.keyOf(login))
        }
    }
}

data class Face(
    val key: FaceKey,
    val name: String,
    val login: String?,
    val avatarUrl: String?,
    val picture: String?,
    val agent: Boolean,
)

data class You(val name: String, val githubLogin: String, val picture: String?) {
    val login: String? get() = GitHubLogin.bare(githubLogin).ifEmpty { null }
}

class Faces(private val people: People, private val you: You) {

    val yours = Face(
        FaceKey.User,
        you.name,
        login = you.login,
        avatarUrl = null,
        picture = you.picture?.takeIf { it.isNotBlank() },
        agent = false,
    )

    fun of(message: Message): Face {
        val relayed = message.relayed
        val author = message.author
        return when {
            relayed != null && relayed.isBy(you.githubLogin) -> yours
            relayed != null -> FaceKey.GitHub(relayed.login).let { key ->
                Face(key, message.speaker(people), relayed.login, relayed.avatarUrl, people.pictureFor(key), agent = false)
            }
            author is Author.Agent -> FaceKey.Agent(author.receiptKey).let { key ->
                Face(key, people.displayNameOf(author), login = null, avatarUrl = null, people.pictureFor(key), agent = true)
            }
            else -> yours
        }
    }

    fun participants(thread: CommentThread, max: Int = 3): Participants {
        val everyone = thread.messages.map(::of).distinctBy { it.key }
        return Participants(everyone.take(max), (everyone.size - max).coerceAtLeast(0))
    }
}
