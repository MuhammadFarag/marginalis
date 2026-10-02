package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PeopleTest {

    private val people = People(mapOf("@OctoCat" to "Mona", " hubot " to "Hu", "dependabot[bot]" to "  ", "" to "Nobody"))

    @Test
    fun `a nickname is found by login regardless of case or a leading at`() {
        assertEquals("Mona", people.nicknameFor("octocat"))
        assertEquals("Mona", people.nicknameFor("@OCTOCAT"))
        assertEquals("Hu", people.nicknameFor("hubot"))
    }

    @Test
    fun `a blank nickname, a blank login or a stranger has none`() {
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
    fun `a login listed twice, however it is written, is found`() {
        assertEquals("octocat", People.duplicateLogin(listOf("OctoCat", "hubot", " @octocat ")))
        assertNull(People.duplicateLogin(listOf("octocat", "hubot", "")))
        assertNull(People.duplicateLogin(listOf("", " ")))
    }
}
