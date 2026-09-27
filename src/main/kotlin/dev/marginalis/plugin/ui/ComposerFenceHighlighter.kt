package dev.marginalis.plugin.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import dev.marginalis.core.CodeFences

/**
 * Colors code fences in the composer while they're typed. The composer's own
 * highlighter is Markdown's, which leaves fence interiors flat; on each change
 * this runs every fence's code through its language's own lexer and paints
 * the tokens one layer above — the same colors the message will get once
 * rendered, without a bespoke lexer or the daemon on a text field.
 */
object ComposerFenceHighlighter {

    private val PAINTED = Key.create<List<RangeHighlighter>>("marginalis.composerFenceHighlights")

    /** Replace this editor's fence colors with ones for its current text. EDT. */
    fun repaint(editor: Editor, project: Project) {
        val markup = editor.markupModel
        editor.getUserData(PAINTED)?.forEach(markup::removeHighlighter)
        val text = editor.document.charsSequence
        val painted = mutableListOf<RangeHighlighter>()
        for (fence in CodeFences.find(text.toString())) {
            val fileType = CodeFenceFileTypes.of(fence.language)
            if (fileType == PlainTextFileType.INSTANCE || fence.codeStart == fence.codeEnd) continue
            val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, project, null) ?: continue
            val lexer = highlighter.highlightingLexer
            lexer.start(text, fence.codeStart, fence.codeEnd, 0)
            while (true) {
                val token = lexer.tokenType ?: break
                val attributes = highlighter.getTokenHighlights(token)
                    .fold(TextAttributes()) { merged, key -> TextAttributes.merge(merged, editor.colorsScheme.getAttributes(key)) }
                if (!attributes.isEmpty) {
                    painted += markup.addRangeHighlighter(
                        lexer.tokenStart, lexer.tokenEnd, HighlighterLayer.SYNTAX + 1, attributes,
                        HighlighterTargetArea.EXACT_RANGE,
                    )
                }
                lexer.advance()
            }
        }
        editor.putUserData(PAINTED, painted)
    }
}
