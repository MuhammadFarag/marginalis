package dev.marginalis.core

/**
 * One fenced code block in a message body: [start, end) spans the fence
 * lines too, [codeStart, codeEnd) only the code between them. [closed] is
 * false for a fence still being typed, which runs to the end of the text.
 */
data class CodeFence(
    val start: Int,
    val end: Int,
    /** The tag after the opening backticks, as written; null when untagged. */
    val language: String?,
    val codeStart: Int,
    val codeEnd: Int,
    val closed: Boolean,
)

/**
 * The one reading of code fences that every surface shares — the rendered
 * message and the composer being typed in — so a block can't highlight in
 * one and not the other. Fences open and close only at the start of a line.
 */
object CodeFences {

    private val OPENING = Regex("```([\\w+#.-]*)[ \\t]*")
    private val CLOSING = Regex("```[ \\t]*")

    // Fence tags that aren't literal file extensions.
    private val LANGUAGE_TO_EXTENSION = mapOf(
        "kotlin" to "kt", "python" to "py", "javascript" to "js",
        "typescript" to "ts", "shell" to "sh", "bash" to "sh", "zsh" to "sh",
        "yaml" to "yml", "markdown" to "md", "rust" to "rs", "ruby" to "rb",
        "c++" to "cpp", "csharp" to "cs", "text" to "txt", "plain" to "txt",
    )

    fun find(text: String): List<CodeFence> {
        val fences = mutableListOf<CodeFence>()
        var openFence: CodeFence? = null
        var lineStart = 0
        while (true) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            val current = openFence
            if (current == null) {
                OPENING.matchEntire(line)?.let {
                    val codeStart = minOf(lineEnd + 1, text.length)
                    openFence = CodeFence(
                        start = lineStart, end = text.length, language = it.groupValues[1].ifEmpty { null },
                        codeStart = codeStart, codeEnd = text.length, closed = false,
                    )
                }
            } else if (CLOSING.matchEntire(line) != null) {
                // The code ends before the newline that precedes the closing fence.
                fences += current.copy(end = lineEnd, codeEnd = maxOf(current.codeStart, lineStart - 1), closed = true)
                openFence = null
            }
            if (lineEnd == text.length) break
            lineStart = lineEnd + 1
        }
        openFence?.let { fences += it }
        return fences
    }

    /** The file extension a fence tag stands for — what the IDE picks a file type by. */
    fun extensionFor(language: String): String =
        language.lowercase().let { LANGUAGE_TO_EXTENSION[it] ?: it }
}
