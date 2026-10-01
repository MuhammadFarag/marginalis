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
 * The composer's Markdown highlighter leaves fence interiors flat, and the
 * daemon doesn't run on a text field — so fences are lexed here and painted
 * one layer above.
 */
object ComposerFenceHighlighter {

    private val PAINTED = Key.create<List<RangeHighlighter>>("marginalis.composerFenceHighlights")

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
