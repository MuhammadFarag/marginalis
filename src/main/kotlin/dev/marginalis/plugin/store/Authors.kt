package dev.marginalis.plugin.store

import dev.marginalis.core.Author
import dev.marginalis.core.Faces
import dev.marginalis.core.People
import dev.marginalis.core.You
import dev.marginalis.plugin.settings.MarginalisSettings

object Authors {
    val agent: Author.Agent = Author.Agent.ANONYMOUS

    val user: Author.User
        get() = userNamed(MarginalisSettings.getInstance().state.displayName)

    fun userNamed(displayName: String): Author.User {
        val name = displayName.trim().ifEmpty {
            System.getProperty("user.name", "User").replaceFirstChar { it.uppercaseChar() }
        }
        return Author.User(name)
    }

    val faces: Faces
        get() = facesOf(MarginalisSettings.getInstance().people)

    fun facesOf(people: People): Faces {
        val state = MarginalisSettings.getInstance().state
        return Faces(people, You(user.displayName, state.githubLogin, state.userPicture))
    }
}
