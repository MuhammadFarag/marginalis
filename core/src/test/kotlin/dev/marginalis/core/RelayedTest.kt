package dev.marginalis.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayedTest {

    private val octocat =
        """{"source":"github","comment_id":1234567890123,"url":"https://github.com/o/r/pull/42#discussion_r1234567890123",""" +
            """"name":"Mona Lisa","login":"octocat"}"""

    private fun octocatWith(edit: JsonObject.() -> Unit): String =
        JsonParser.parseString(octocat).asJsonObject.apply(edit).toString()

    private fun parse(json: String?, to: Addressee? = null) = Relayed.parse(json?.let { JsonParser.parseString(it) }, to)

    private fun relayed(json: String): Relayed = assertIs<Parsed.Ok<Relayed?>>(parse(json)).value!!

    private fun reason(json: String, to: Addressee? = null): String = assertIs<Parsed.Invalid>(parse(json, to)).reason

    @Test
    fun `a GitHub comment relays with its id, link and author, and is no bot unless it says so`() {
        val parsed = relayed(octocat)

        assertEquals(Relayed.Source.GITHUB, parsed.source)
        assertEquals("1234567890123", parsed.commentId)
        assertEquals("https://github.com/o/r/pull/42#discussion_r1234567890123", parsed.url)
        assertEquals("Mona Lisa", parsed.name)
        assertEquals("octocat", parsed.login)
        assertFalse(parsed.bot)
        assertNull(parsed.avatarUrl)
    }

    @Test
    fun `no relayed field means an ordinary message`() {
        assertEquals(Parsed.Ok(null), parse(null))
        assertEquals(Parsed.Ok(null), parse("null"))
    }

    @Test
    fun `a bot is one that says so, or whose login ends in the bot suffix`() {
        assertTrue(relayed(octocatWith { addProperty("bot", true) }).bot)
        assertTrue(relayed(octocatWith { addProperty("login", "coderabbitai[bot]") }).bot)
    }

    @Test
    fun `the avatar is kept for later`() {
        assertEquals("https://a/x.png", relayed(octocatWith { addProperty("avatar_url", "https://a/x.png") }).avatarUrl)
    }

    @Test
    fun `GitHub is the only source, and anything else is taught`() {
        assertContains(reason(octocatWith { addProperty("source", "gitlab") }), "'github'")
        assertContains(reason(octocatWith { remove("source") }), "'github'")
    }

    @Test
    fun `each required field missing is named`() {
        for (field in listOf("comment_id", "url", "name", "login")) {
            assertContains(reason(octocatWith { remove(field) }), "'$field'")
        }
    }

    @Test
    fun `a field of the wrong type is refused as the wrong type, not as missing`() {
        for (field in listOf("url", "name", "login")) {
            val refused = reason(octocatWith { add(field, JsonParser.parseString("[1]")) })
            assertContains(refused, "'$field' must be a string")
        }
        assertContains(reason(octocatWith { addProperty("comment_id", true) }), "'comment_id' must be")
    }

    @Test
    fun `a relay that is not an object, or a bot flag that is not a boolean, is refused`() {
        assertContains(reason("\"octocat\""), "object")
        assertContains(reason(octocatWith { addProperty("bot", "yes") }), "'bot'")
    }

    @Test
    fun `the teaching shape is itself valid JSON`() {
        val shape = reason("\"octocat\"").substringAfter("object: ").removeSuffix(".")

        assertTrue(JsonParser.parseString(shape).isJsonObject)
    }

    @Test
    fun `a comment id is digits, trimmed, and nothing else`() {
        assertEquals("123", relayed(octocatWith { addProperty("comment_id", " 123 "); addProperty("url", "https://github.com/o/r/pull/42#discussion_r123") }).commentId)
        for (notAnId in listOf("123.0", "1.23e2", "-5", "abc", "")) {
            assertContains(reason(octocatWith { add("comment_id", JsonParser.parseString(notAnId.ifEmpty { "\"\"" })) }), "'comment_id'")
        }
    }

    @Test
    fun `the link must be an https link to the comment itself`() {
        for (url in listOf(
            "http://github.com/o/r/pull/42#discussion_r1",
            "file:///etc/passwd#discussion_r1",
            "javascript:alert(1)//#discussion_r1",
            "github.com/o/r/pull/42#discussion_r1",
            "https://github.com/o/r/pull/42",
        )) {
            assertContains(reason(octocatWith { addProperty("url", url) }), "'url'", message = url)
        }
    }

    @Test
    fun `GitHub Enterprise hosts relay too`() {
        assertEquals("PR #7", relayed(octocatWith { addProperty("comment_id", 9); addProperty("url", "https://ghe.example.com/o/r/pull/7#issuecomment-9") }).discussion)
    }

    @Test
    fun `a display name is short and can never be mistaken for markup`() {
        assertContains(reason(octocatWith { addProperty("name", "<html><b>Mona</b>") }), "'name'")
        assertContains(reason(octocatWith { addProperty("name", "M".repeat(101)) }), "'name'")
        assertEquals("M".repeat(100), relayed(octocatWith { addProperty("name", "M".repeat(100)) }).name)
    }

    @Test
    fun `a login must be a GitHub login, bot suffix allowed`() {
        for (login in listOf("<b>x</b>", "-octo", "octo cat", "o".repeat(101), "octo[bot]x")) {
            assertContains(reason(octocatWith { addProperty("login", login) }), "'login'", message = login)
        }
        assertContains(reason(octocatWith { addProperty("login", "octo cat") }), "underscores")
        assertEquals("dependabot[bot]", relayed(octocatWith { addProperty("login", "dependabot[bot]") }).login)
        assertEquals("o-c-1", relayed(octocatWith { addProperty("login", "o-c-1") }).login)
        assertEquals("mona_acme", relayed(octocatWith { addProperty("login", "mona_acme") }).login)
        assertEquals("o".repeat(100), relayed(octocatWith { addProperty("login", "o".repeat(100)) }).login)
    }

    @Test
    fun `a relay is someone else's words, so it addresses no one`() {
        assertContains(reason(octocat, to = Addressee.User), "'to'")
    }

    @Test
    fun `the kind of comment comes from its link, so review and conversation ids never collide`() {
        val review = relayed(octocatWith { addProperty("comment_id", "5"); addProperty("url", "https://github.com/o/r/pull/1#discussion_r5") })
        val conversation = relayed(octocatWith { addProperty("comment_id", "5"); addProperty("url", "https://github.com/o/r/pull/1#issuecomment-5") })
        val summary = relayed(octocatWith { addProperty("comment_id", "5"); addProperty("url", "https://github.com/o/r/pull/1#pullrequestreview-5") })

        assertEquals(Relayed.Kind.REVIEW_COMMENT, review.kind)
        assertEquals(Relayed.Kind.CONVERSATION_COMMENT, conversation.kind)
        assertEquals(Relayed.Kind.REVIEW, summary.kind)
        assertNotEquals(review.key, conversation.key)
        assertNotEquals(conversation.key, summary.key)
    }

    @Test
    fun `the discussion it came from is named from the link, pull request or issue`() {
        assertEquals("PR #42", relayed(octocat).discussion)
        val onIssue = relayed(octocatWith { addProperty("comment_id", 9); addProperty("url", "https://github.com/o/r/issues/12#issuecomment-9") })
        assertEquals("Issue #12", onIssue.discussion)
    }

    @Test
    fun `the link must point at the comment it relays`() {
        assertContains(reason(octocatWith { addProperty("url", "https://github.com/o/r/pull/42#discussion_r99") }), "'comment_id'")
    }

    @Test
    fun `an avatar must be a short https link`() {
        assertContains(reason(octocatWith { addProperty("avatar_url", "http://a/x.png") }), "'avatar_url'")
        assertContains(reason(octocatWith { addProperty("avatar_url", "https://a/" + "x".repeat(2100)) }), "'avatar_url'")
    }

    @Test
    fun `a relay is by a GitHub login regardless of case or a leading at, and a blank login is nobody`() {
        val parsed = relayed(octocat)

        assertTrue(parsed.isBy("OctoCat"))
        assertTrue(parsed.isBy(" @octocat "))
        assertFalse(parsed.isBy("hubot"))
        assertFalse(parsed.isBy(""))
        assertFalse(parsed.isBy("@"))
    }

    @Test
    fun `a stored relay that no longer parses still loads as relayed, keeping what it can`() {
        val degraded = Relayed.fromStored(JsonParser.parseString("""{"source":"gitlab","name":"Mona","comment_id":"7"}"""))!!

        assertEquals("Mona", degraded.name)
        assertEquals("7", degraded.commentId)
        assertNull(Relayed.fromStored(null))
        val marked = Relayed.fromStored(JsonParser.parseString("""{"name":"<html><b>x","login":"octocat"}"""))!!
        assertEquals("octocat", marked.name)
    }
}
