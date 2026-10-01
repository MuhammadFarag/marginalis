package dev.marginalis.core

import java.io.ByteArrayOutputStream

class CodeLink internal constructor(val path: String, val lines: IntRange?) {

    override fun toString(): String = path + when {
        lines == null -> ""
        lines.first == lines.last -> "#L${lines.first}"
        else -> "#L${lines.first}-L${lines.last}"
    }

    companion object {
        private const val EXAMPLE = "src/Main.kt#L42"
        private const val RANGE_EXAMPLE = "src/Main.kt#L42-L50"
        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
        private val LINE_ANCHOR = Regex("L([1-9][0-9]{0,8})(?:-L([1-9][0-9]{0,8}))?")

        fun parse(target: String): Parsed<CodeLink> {
            val encodedPath = target.substringBefore('#')
            val path = percentDecoded(encodedPath)
            val scheme = SCHEME.find(path)
            val pathProblem = when {
                scheme != null -> "it starts with '${scheme.value}', a scheme or drive rather than a project path"
                '?' in encodedPath -> "it carries a '?' query"
                path.isEmpty() -> "it names no file"
                path.startsWith("/") -> "the path is absolute"
                path.any { it.isISOControl() } -> "the path holds a control character"
                '\\' in path -> "the path uses '\\' — separate folders with '/'"
                ".." in path.split('/') -> "the path climbs out of the project with '..'"
                else -> null
            }
            if (pathProblem != null) return invalid(target, pathProblem)

            if ('#' !in target) return Parsed.Ok(CodeLink(path, null))
            val fragment = target.substringAfter('#')
            val anchor = LINE_ANCHOR.matchEntire(fragment)
                ?: return invalid(target, "'#$fragment' is not a line anchor")
            val first = anchor.groupValues[1].toInt()
            val last = anchor.groupValues[2].ifEmpty { null }?.toInt() ?: first
            if (last < first) return invalid(target, "the range ends before it starts")
            return Parsed.Ok(CodeLink(path, first..last))
        }

        private fun percentDecoded(text: String): String {
            val bytes = ByteArrayOutputStream()
            var i = 0
            while (i < text.length) {
                val high = text.getOrNull(i + 1)?.digitToIntOrNull(16)
                val low = text.getOrNull(i + 2)?.digitToIntOrNull(16)
                if (text[i] == '%' && high != null && low != null) {
                    bytes.write(high * 16 + low)
                    i += 3
                } else {
                    val literalEnd = text.indexOf('%', i + 1).takeIf { it >= 0 } ?: text.length
                    bytes.write(text.substring(i, literalEnd).toByteArray(Charsets.UTF_8))
                    i = literalEnd
                }
            }
            return bytes.toString(Charsets.UTF_8)
        }

        private fun invalid(target: String, problem: String) = Parsed.Invalid(
            "'$target' is not a code link: $problem. A code link is a path relative to the project root, " +
                "optionally with a 1-based GitHub line anchor — e.g. $EXAMPLE, or $RANGE_EXAMPLE for a range.",
        )
    }
}
