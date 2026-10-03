package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FacesTest {

    private val user = Author.User("Muhammad")
    private val claude = Author.Agent("Claude", "claude-main")
    private val builder = Author.Agent("Claude · builder", "claude-builder")
    private val you = You("Muhammad Farag", "mfarag", picture = "me.png")
    private val people = People(
        listOf(
            People.Row("OctoCat", People.Kind.PERSON, "Mona", picture = "mona.png"),
            People.Row("claude-builder", People.Kind.AGENT, "Builder", picture = "builder.png"),
        ),
    )
    private val faces = Faces(people, you)

    private fun relayed(login: String, name: String = login, bot: Boolean = false, avatarUrl: String? = null) =
        Relayed(Relayed.Source.GITHUB, "1", "https://github.com/o/r/pull/1#discussion_r1", name, login, bot, avatarUrl)

    @Test
    fun `the user speaks as themselves, coloured as the user, with no badge`() {
        assertEquals(
            Face(FaceKey.User, "Muhammad Farag", login = "mfarag", avatarUrl = null, picture = "me.png", agent = false),
            faces.of(Message(user, "…")),
        )
    }

    @Test
    fun `a message the user relayed from GitHub speaks as the user`() {
        val mine = Message(claude, "…", relayed = relayed("MFarag", "Muhammad"))

        assertEquals(faces.of(Message(user, "…")), faces.of(mine))
    }

    @Test
    fun `a relayed GitHub comment speaks as the relayed person under their login, not as the relaying agent, and carries no badge, even for a bot`() {
        val avatar = "https://avatars.githubusercontent.com/u/583231?v=4"

        assertEquals(
            Face(FaceKey.GitHub("octocat"), "Mona", login = "octocat", avatarUrl = avatar, picture = "mona.png", agent = false),
            faces.of(Message(builder, "…", relayed = relayed("octocat", "Mona Lisa", avatarUrl = avatar))),
        )
        assertEquals(
            Face(FaceKey.GitHub("dependabot[bot]"), "dependabot[bot]", login = "dependabot[bot]", avatarUrl = null, picture = null, agent = false),
            faces.of(Message(claude, "…", relayed = relayed("dependabot[bot]", bot = true))),
        )
    }

    @Test
    fun `an agent speaks with the agent badge under its receipt key`() {
        assertEquals(
            Face(FaceKey.Agent("claude-builder"), "Builder", login = null, avatarUrl = null, picture = "builder.png", agent = true),
            faces.of(Message(builder, "…")),
        )
        assertEquals(
            Face(FaceKey.Agent("Codex"), "Codex", login = null, avatarUrl = null, picture = null, agent = true),
            faces.of(Message(Author.Agent("Codex"), "…")),
        )
    }

    @Test
    fun `a configured picture comes first, then a GitHub avatar, then the monogram`() {
        assertEquals(
            listOf(
                AvatarSource.Picture("me.png"),
                AvatarSource.GitHub("mfarag", "https://github.com/mfarag.png?size=64"),
                AvatarSource.Monogram,
            ),
            faces.of(Message(user, "…")).sources(githubAvatars = true),
        )
    }

    @Test
    fun `a relayed person's GitHub avatar is their login's, preferring the avatar_url they were relayed with`() {
        val avatar = "https://avatars.githubusercontent.com/u/1?v=4"

        assertEquals(
            listOf(AvatarSource.GitHub("hubot", "https://github.com/hubot.png?size=64"), AvatarSource.Monogram),
            faces.of(Message(claude, "…", relayed = relayed("hubot"))).sources(githubAvatars = true),
        )
        assertEquals(
            AvatarSource.GitHub("hubot", "$avatar&s=64"),
            faces.of(Message(claude, "…", relayed = relayed("hubot", avatarUrl = avatar))).sources(githubAvatars = true).first(),
        )
    }

    @Test
    fun `the user's GitHub avatar follows their GitHub login, and none is offered without one`() {
        val anonymous = Faces(people, You("Muhammad Farag", " ", picture = ""))

        assertEquals(
            AvatarSource.GitHub(cacheName = "mfarag", url = "https://github.com/mfarag.png?size=64"),
            Faces(people, You("Muhammad Farag", "@MFarag", picture = null)).of(Message(user, "…")).sources(githubAvatars = true).first(),
        )
        assertEquals(listOf(AvatarSource.Monogram), anonymous.of(Message(user, "…")).sources(githubAvatars = true))
    }

    @Test
    fun `the user's login drops the at sign and surrounding space but keeps its case, and is absent when blank`() {
        assertEquals("MFarag", You("Muhammad Farag", " @MFarag ", picture = null).login)
        assertEquals(null, You("Muhammad Farag", " @ ", picture = null).login)
    }

    @Test
    fun `a relayed person's avatar is cached under their login whatever its case`() {
        assertEquals(
            AvatarSource.GitHub(cacheName = "octocat", url = "https://github.com/octocat.png?size=64"),
            faces.of(Message(claude, "…", relayed = relayed("OctoCat"))).sources(githubAvatars = true)[1],
        )
    }

    @Test
    fun `a Person row gets a GitHub avatar from its login, while an agent never does`() {
        assertEquals(
            listOf(
                AvatarSource.Picture("mona.png"),
                AvatarSource.GitHub("octocat", "https://github.com/octocat.png?size=64"),
                AvatarSource.Monogram,
            ),
            faces.of(Message(claude, "…", relayed = relayed("octocat"))).sources(githubAvatars = true),
        )
        assertEquals(
            listOf(AvatarSource.Picture("builder.png"), AvatarSource.Monogram),
            faces.of(Message(builder, "…")).sources(githubAvatars = true),
        )
        assertEquals(listOf(AvatarSource.Monogram), faces.of(Message(claude, "…")).sources(githubAvatars = true))
    }

    @Test
    fun `turning GitHub avatars off leaves only the picture and the monogram`() {
        assertEquals(
            listOf(AvatarSource.Picture("me.png"), AvatarSource.Monogram),
            faces.of(Message(user, "…")).sources(githubAvatars = false),
        )
        assertEquals(
            listOf(AvatarSource.Monogram),
            faces.of(Message(claude, "…", relayed = relayed("hubot", avatarUrl = "https://avatars.githubusercontent.com/u/1"))).sources(githubAvatars = false),
        )
    }

    @Test
    fun `a bot without a relayed avatar_url gets no GitHub fetch`() {
        assertEquals(
            listOf(AvatarSource.Monogram),
            faces.of(Message(claude, "…", relayed = relayed("dependabot[bot]", bot = true))).sources(githubAvatars = true),
        )
    }

    @Test
    fun `a People row shows the face its person or agent gets in a thread`() {
        val octocat = People.Row("octocat", People.Kind.PERSON, "Mona", picture = "mona.png")
        val agent = People.Row("claude-builder", People.Kind.AGENT, "Builder", picture = "builder.png")
        val unnamed = People.Row("hubot", People.Kind.PERSON)
        val listed = Faces(People(listOf(octocat, agent)), you)

        assertEquals(listed.of(Message(claude, "…", relayed = relayed("octocat", "Mona Lisa"))), octocat.face())
        assertEquals(listed.of(Message(builder, "…")), agent.face())
        assertEquals(Face(FaceKey.GitHub("hubot"), "hubot", login = "hubot", avatarUrl = null, picture = null, agent = false), unnamed.face())
    }
}
