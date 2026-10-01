package dev.marginalis.core

class Reference internal constructor(idPrefix: String) {

    private val idPrefix = idPrefix.lowercase()

    fun resolveIn(threads: Collection<CommentThread>): Resolution {
        val candidates = threads.flatMap { thread ->
            listOfNotNull(Referent(thread, null).takeIf { thread.id.startsWith(idPrefix) }) +
                thread.messages.filter { it.id.startsWith(idPrefix) }.map { Referent(thread, it) }
        }
        return when (candidates.size) {
            0 -> Resolution.Unknown
            1 -> Resolution.Found(candidates.single())
            else -> Resolution.Ambiguous(this, candidates)
        }
    }

    override fun toString(): String = "$SCHEME$idPrefix"

    companion object {
        private const val SCHEME = "mg:"
        private const val SHORT_LENGTH = 8
        private const val EXAMPLE = "mg:3d4770ad"
        private val SYNTAX = Regex("$SCHEME([0-9a-f]{$SHORT_LENGTH}[0-9a-f-]*)", RegexOption.IGNORE_CASE)
        private val IN_PROSE = Regex("(?<![\\w:/.-])${SYNTAX.pattern}(?![\\w-])", RegexOption.IGNORE_CASE)
        private val TAG = Regex("<(/?)(\\w+)[^>]*>")
        private val VERBATIM_ELEMENTS = setOf("code", "pre", "a")

        fun of(id: String): Reference = Reference(id.take(SHORT_LENGTH))

        fun looksLike(target: String): Boolean = target.trim().startsWith(SCHEME, ignoreCase = true)

        fun parse(text: String?): Parsed<Reference?> {
            if (text == null) return Parsed.Ok(null)
            val match = SYNTAX.matchEntire(text.trim())
                ?: return Parsed.Invalid(
                    "'$text' is not a reference: a reference is 'mg:' and at least the first $SHORT_LENGTH characters " +
                        "of a thread or message id, e.g. $EXAMPLE — as Copy Reference puts it on the clipboard.",
                )
            return Parsed.Ok(Reference(match.groupValues[1]))
        }

        fun linkify(html: String): String {
            val linked = StringBuilder()
            var verbatimDepth = 0
            var textStart = 0
            for (tag in TAG.findAll(html)) {
                linked.append(linkProse(html.substring(textStart, tag.range.first), verbatimDepth > 0))
                linked.append(tag.value)
                if (tag.groupValues[2].lowercase() in VERBATIM_ELEMENTS) {
                    verbatimDepth = (verbatimDepth + if (tag.groupValues[1].isEmpty()) 1 else -1).coerceAtLeast(0)
                }
                textStart = tag.range.last + 1
            }
            linked.append(linkProse(html.substring(textStart), verbatimDepth > 0))
            return linked.toString()
        }

        private fun linkProse(text: String, verbatim: Boolean): String =
            if (verbatim) text else IN_PROSE.replace(text) { "<a href=\"${it.value}\">${it.value}</a>" }
    }
}

class Referent(val thread: CommentThread, val message: Message?) {
    val fullReference: Reference
        get() = Reference(message?.id ?: thread.id)
}

sealed interface Resolution {
    class Found(val referent: Referent) : Resolution

    class Ambiguous(reference: Reference, val candidates: List<Referent>) : Resolution {
        val summary: String = "$reference names ${candidates.size} threads or messages"
        val reason: String =
            "$summary — pass a longer prefix of the id you mean; each candidate's 'ref' is its full, unambiguous one."
    }

    data object Unknown : Resolution
}
