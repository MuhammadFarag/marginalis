package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PeopleTest {

    private val people = People(
        listOf(
            People.Row("@OctoCat", People.Kind.PERSON, "Mona"),
            People.Row(" hubot ", People.Kind.PERSON, "Hu"),
            People.Row("dependabot[bot]", People.Kind.PERSON, "  ", picture = "dependabot.png"),
            People.Row("", People.Kind.PERSON, "Nobody"),
            People.Row(" claude-builder ", People.Kind.AGENT, "Builder", picture = "builder.png"),
        ),
    )
    private val builder = Author.Agent("Claude · builder", "claude-builder")
    private val octocatComment = Relayed(Relayed.Source.GITHUB, "1", "https://github.com/o/r/pull/1#discussion_r1", "Mona Lisa", "octocat")

    @Test
    fun `a Person row's nickname is found by login regardless of case or a leading at`() {
        assertEquals("Mona", people.nicknameFor("octocat"))
        assertEquals("Mona", people.nicknameFor("@OCTOCAT"))
        assertEquals("Hu", people.nicknameFor("hubot"))
    }

    @Test
    fun `a blank nickname, a blank identity or a stranger has none`() {
        assertNull(people.nicknameFor("dependabot[bot]"))
        assertNull(people.nicknameFor(""))
        assertNull(people.nicknameFor("someone"))
        assertNull(People.NONE.nicknameFor("octocat"))
    }

    @Test
    fun `a relayed message speaks by its author's nickname when there is one`() {
        val claude = Author.Agent("Claude", "claude-main")
        val relayed = Message(
            claude, "…",
            relayed = Relayed(Relayed.Source.GITHUB, "1", "https://github.com/o/r/pull/1#discussion_r1", "Mona Lisa", "octocat"),
        )
        val stranger = Message(
            claude, "…",
            relayed = Relayed(Relayed.Source.GITHUB, "2", "https://github.com/o/r/pull/1#discussion_r2", "Hal", "hal9000"),
        )

        assertEquals("Mona", relayed.speaker(people))
        assertEquals("Mona Lisa", relayed.speaker(People.NONE))
        assertEquals("Hal", stranger.speaker(people))
        assertEquals("Claude", Message(claude, "…").speaker(people))
    }

    @Test
    fun `an identity listed twice within the same kind is reported, however it is written`() {
        val again = People.Row(" @octocat ", People.Kind.PERSON)
        val agentAgain = People.Row(" claude-main ", People.Kind.AGENT, "Main")

        assertEquals(again, People.duplicate(listOf(People.Row("OctoCat", People.Kind.PERSON), person("hubot"), again)))
        assertEquals(agentAgain, People.duplicate(listOf(agent("claude-main"), agentAgain)))
        assertNull(People.duplicate(listOf(person("octocat"), person("hubot"), person(""), person(" "))))
        assertNull(People.duplicate(listOf(agent("claude-main"), agent("Claude-Main"))))
    }

    @Test
    fun `the same text as a Person and as an Agent is not a duplicate`() {
        assertNull(People.duplicate(listOf(person("claude-main"), agent("claude-main"))))
    }

    private fun person(login: String) = People.Row(login, People.Kind.PERSON)

    private fun agent(id: String) = People.Row(id, People.Kind.AGENT)

    @Test
    fun `an Agent row renames that agent wherever it speaks, matched by its author id and not by its display name`() {
        assertEquals("Builder", people.nicknameForAgent("claude-builder"))
        assertEquals("Builder", people.displayNameOf(builder))
        assertEquals("Builder", Message(builder, "…").speaker(people))
        assertEquals("claude-builder", people.displayNameOf(Author.Agent("claude-builder", "claude-review")))
        assertEquals("Claude · builder", People.NONE.displayNameOf(builder))
        assertEquals("Muhammad", people.displayNameOf(Author.User("Muhammad")))
    }

    @Test
    fun `an Agent row never matches a GitHub login and a Person row never matches an agent id`() {
        val crossed = People(
            listOf(
                People.Row("octocat", People.Kind.AGENT, "Agent Octo"),
                People.Row("claude-main", People.Kind.PERSON, "Person Claude"),
            ),
        )
        assertNull(crossed.nicknameFor("octocat"))
        assertNull(crossed.nicknameForAgent("claude-main"))
        assertEquals("Claude", crossed.displayNameOf(Author.Agent("Claude", "claude-main")))
        assertEquals("Mona Lisa", Message(Author.Agent("Octo", "octocat"), "…", relayed = octocatComment).speaker(crossed))
    }

    @Test
    fun `a relayed message names its relaying agent by the agent's nickname in via`() {
        val relayed = Message(builder, "…", relayed = octocatComment)

        assertEquals("Mona", relayed.speaker(people))
        assertEquals("Builder", people.displayNameOf(relayed.author))
    }

    @Test
    fun `a row's picture is found for the identity it names`() {
        assertEquals("dependabot.png", people.pictureFor(FaceKey.GitHub("@Dependabot[bot]")))
        assertEquals("builder.png", people.pictureFor(FaceKey.Agent("claude-builder")))
        assertNull(people.pictureFor(FaceKey.GitHub("claude-builder")))
        assertNull(people.pictureFor(FaceKey.Agent("dependabot[bot]")))
        assertNull(people.pictureFor(FaceKey.GitHub("octocat")))
        assertNull(people.pictureFor(FaceKey.User))
    }

    @Test
    fun `migrating twice adds nothing, and a row already in the table wins over a legacy nickname`() {
        val legacy = mapOf("octocat" to "Mona", "hubot" to "Hu")
        val table = listOf(
            People.Row("@OctoCat", People.Kind.PERSON, "Octo", picture = "octo.png"),
            People.Row("hubot", People.Kind.AGENT, "Bot"),
        )
        val once = People.migrated(legacy, table)

        assertEquals(once, People.migrated(legacy, once))
        assertEquals(table + People.Row("hubot", People.Kind.PERSON, "Hu"), once)
        assertEquals("Octo", People(once).nicknameFor("octocat"))
    }

    @Test
    fun `a table row becomes a Person row with its login trimmed and the at stripped, and an Agent row keeps its id as written`() {
        assertEquals(
            People.Row("OctoCat", People.Kind.PERSON, "Mona"),
            People.Row.normalizedOrNull(" @OctoCat ", People.Kind.PERSON, " Mona ", picture = ""),
        )
        assertEquals(
            People.Row("@Claude-Builder", People.Kind.AGENT, "Builder", picture = "builder.png"),
            People.Row.normalizedOrNull(" @Claude-Builder ", People.Kind.AGENT, "Builder", picture = "builder.png"),
        )
    }

    @Test
    fun `a row with no identity, or with neither a nickname nor a picture, is dropped`() {
        assertNull(People.Row.normalizedOrNull(" @ ", People.Kind.PERSON, "Mona", picture = "mona.png"))
        assertNull(People.Row.normalizedOrNull("  ", People.Kind.AGENT, "Builder", picture = null))
        assertNull(People.Row.normalizedOrNull("octocat", People.Kind.PERSON, "  ", picture = " "))
        assertNull(People.Row.normalizedOrNull("claude-main", People.Kind.AGENT, "", picture = null))
    }

    @Test
    fun `a picture-only row is kept and still gives that person their picture`() {
        val row = People.Row.normalizedOrNull("@Hubot", People.Kind.PERSON, "", picture = "hubot.png")
        val pictured = People(listOfNotNull(row))

        assertEquals(People.Row("Hubot", People.Kind.PERSON, picture = "hubot.png"), row)
        assertEquals("hubot.png", pictured.pictureFor(FaceKey.GitHub("hubot")))
        assertNull(pictured.nicknameFor("hubot"))
    }

    @Test
    fun `an existing nicknames-only settings file loads as Person rows, and saving it again changes nothing`() {
        val loaded = People.migrated(mapOf(" @OctoCat " to " Mona ", "hubot" to "Hu"), emptyList())
        val savedAgain = loaded.mapNotNull { People.Row.normalizedOrNull(it.identity, it.kind, it.nickname, it.picture) }

        assertEquals(
            listOf(People.Row("OctoCat", People.Kind.PERSON, "Mona"), People.Row("hubot", People.Kind.PERSON, "Hu")),
            loaded,
        )
        assertEquals(loaded, savedAgain)
        assertEquals(loaded, People.migrated(emptyMap(), savedAgain))
    }

    @Test
    fun `a picture no row and not the user refers to is reported for cleanup, and referenced ones are kept`() {
        val rows = listOf(
            People.Row("octocat", People.Kind.PERSON, "Mona", picture = "octo.png"),
            People.Row("claude-builder", People.Kind.AGENT, picture = "builder.png"),
            People.Row("hubot", People.Kind.PERSON, "Hu"),
        )
        val stored = listOf("octo.png", "builder.png", "mine.png", "old.png", "replaced.png")

        assertEquals(listOf("old.png", "replaced.png"), People.unreferencedPictures(stored, rows, yours = "mine.png"))
        assertEquals(listOf("mine.png"), People.unreferencedPictures(listOf("mine.png"), emptyList(), yours = " "))
    }
}
