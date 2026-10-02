package dev.marginalis.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class Relayed(
    val source: Source,
    val commentId: String,
    val url: String,
    val name: String,
    val login: String,
    val bot: Boolean = false,
    val avatarUrl: String? = null,
) {
    enum class Source(val wire: String) { GITHUB("github") }

    enum class Kind(val wire: String, val fragment: String) {
        REVIEW_COMMENT("review_comment", "#discussion_r"),
        CONVERSATION_COMMENT("conversation_comment", "#issuecomment-"),
        REVIEW("review", "#pullrequestreview-"),
    }

    data class Key(val kind: Kind?, val commentId: String)

    val kind: Kind?
        get() = Kind.entries.firstOrNull { url.contains(it.fragment) }

    val key: Key
        get() = Key(kind, commentId)

    val discussion: String?
        get() = DISCUSSION.find(url)?.destructured?.let { (where, number) ->
            if (where == "pull") "PR #$number" else "Issue #$number"
        }

    fun isBy(githubLogin: String): Boolean {
        val wanted = githubLogin.trim().removePrefix("@")
        return wanted.isNotEmpty() && login.equals(wanted, ignoreCase = true)
    }

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("source", source.wire)
        addProperty("comment_id", commentId)
        addProperty("url", url)
        addProperty("name", name)
        addProperty("login", login)
        addProperty("bot", bot)
        avatarUrl?.let { addProperty("avatar_url", it) }
    }

    companion object {
        private val DISCUSSION = Regex("/(pull|issues)/(\\d+)")
        private val COMMENT_ID = Regex("\\d+")
        private val LOGIN = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,99}(\\[bot])?")
        private const val BOT_SUFFIX = "[bot]"
        private const val NAME_LIMIT = 100
        private const val AVATAR_LIMIT = 2048
        private const val SHAPE =
            "{\"source\": \"github\", \"comment_id\": \"123\", \"url\": \"https://github.com/o/r/pull/1#discussion_r123\", " +
                "\"name\": \"Mona Lisa\", \"login\": \"octocat\", \"bot\": false, \"avatar_url\": \"https://…\"}"

        private val REQUIRED = listOf(
            "comment_id" to "the GitHub comment's id; relaying the same id again changes nothing",
            "url" to "the comment's html_url, opened from the margin",
            "name" to "the author's display name (the login if they have none)",
            "login" to "the author's GitHub login",
        )

        fun parse(json: JsonElement?, to: Addressee? = null): Parsed<Relayed?> {
            if (json == null || json.isJsonNull) return Parsed.Ok(null)
            if (!json.isJsonObject) return Parsed.Invalid("'relayed' must be an object: $SHAPE.")
            if (to != null) {
                return Parsed.Invalid(
                    "'to' with 'relayed': a relayed message is someone else's words and addresses no one. Drop " +
                        "'to', and address your own reply separately.",
                )
            }
            val fields = json.asJsonObject
            val source = fields.text("source")
            if (!source.equals(Source.GITHUB.wire, ignoreCase = true)) {
                val given = source?.let { "'$it' is not a source" } ?: "'relayed.source' is missing"
                return Parsed.Invalid("$given — relaying knows one source: 'github'. Relay a GitHub comment as $SHAPE.")
            }
            val values = REQUIRED.associate { (field, what) ->
                val value = fields.get(field)?.takeIf { !it.isJsonNull }
                    ?: return Parsed.Invalid("'relayed' is missing '$field': $what.")
                if (!value.isJsonPrimitive || value.asJsonPrimitive.isBoolean) {
                    return Parsed.Invalid(
                        if (field == "comment_id") "'comment_id' must be the comment's numeric id, as a number or a string of digits."
                        else "'$field' must be a string — $what.",
                    )
                }
                field to value.asString.trim()
            }
            val commentId = values.getValue("comment_id")
            if (!COMMENT_ID.matches(commentId)) {
                return Parsed.Invalid("'comment_id' must be the comment's numeric id, digits only — got '$commentId'.")
            }
            val url = values.getValue("url")
            val kind = Kind.entries.firstOrNull { url.contains(it.fragment) }
            if (!WebLink.isHttps(url) || kind == null) {
                return Parsed.Invalid(
                    "'url' must be the comment's https html_url, ending in #discussion_r…, #issuecomment-… or " +
                        "#pullrequestreview-… — got '$url'.",
                )
            }
            val linkedId = url.substringAfter(kind.fragment).takeWhile(Char::isDigit)
            if (linkedId != commentId) {
                return Parsed.Invalid(
                    "'comment_id' $commentId is not the comment 'url' points at (${kind.fragment}$linkedId) — pass " +
                        "the comment's own id and html_url.",
                )
            }
            val name = values.getValue("name")
            if (!isDisplayable(name)) {
                return Parsed.Invalid(
                    "'name' must be a display name of 1 to $NAME_LIMIT characters that doesn't start with '<' — " +
                        "use the login when the author has no name.",
                )
            }
            val login = values.getValue("login")
            if (!LOGIN.matches(login)) {
                return Parsed.Invalid("'login' must be a GitHub login (letters, digits, hyphens and underscores; '…[bot]' for apps) — got '$login'.")
            }
            val botFlag = fields.get("bot")?.takeIf { !it.isJsonNull }
            if (botFlag != null && !(botFlag.isJsonPrimitive && botFlag.asJsonPrimitive.isBoolean)) {
                return Parsed.Invalid(
                    "'bot' must be true or false — whether the author is a bot. Omit it to go by the login " +
                        "('…$BOT_SUFFIX' is a bot).",
                )
            }
            val avatarUrl = fields.text("avatar_url")
            if (avatarUrl != null && (!WebLink.isHttps(avatarUrl) || avatarUrl.length > AVATAR_LIMIT)) {
                return Parsed.Invalid("'avatar_url' must be an https link of at most $AVATAR_LIMIT characters — the author's avatar_url.")
            }
            return Parsed.Ok(
                Relayed(
                    source = Source.GITHUB,
                    commentId = commentId,
                    url = url,
                    name = name,
                    login = login,
                    bot = botFlag?.asBoolean == true || login.endsWith(BOT_SUFFIX),
                    avatarUrl = avatarUrl,
                ),
            )
        }

        fun fromStored(json: JsonElement?): Relayed? {
            if (json == null || json.isJsonNull) return null
            parse(json).getOrElse { null }?.let { return it }
            val fields = json.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
            val login = fields.text("login") ?: ""
            return Relayed(
                source = Source.GITHUB,
                commentId = fields.text("comment_id") ?: "",
                url = fields.text("url") ?: "",
                name = fields.text("name")?.takeIf(::isDisplayable) ?: login.ifEmpty { "GitHub" }.take(NAME_LIMIT),
                login = login,
                bot = fields.get("bot")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true,
                avatarUrl = fields.text("avatar_url"),
            )
        }

        private fun isDisplayable(name: String): Boolean = name.isNotEmpty() && name.length <= NAME_LIMIT && !name.startsWith("<")

        private fun JsonObject.text(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }?.asString?.takeIf { it.isNotBlank() }
    }
}
