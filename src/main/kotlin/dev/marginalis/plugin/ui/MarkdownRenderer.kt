package dev.marginalis.plugin.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.Component
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

/**
 * Markdown-lite rendering for message bodies: bold, italic, inline code,
 * links, lists — and fenced code blocks as read-only editor fragments with
 * the IDE's real lexer and color scheme.
 *
 * Deliberately scoped (CommonMark flavour, no tables/images/raw HTML): each
 * extra construct carries its own Swing sizing tax, and conversation rarely
 * needs more. Grow on demand.
 */
object MarkdownRenderer {

    // ```lang\n … \n``` — fences split out first so they can render natively.
    private val FENCE = Regex(
        "^```([\\w+#.-]*)[ \\t]*\\n(.*?)^```[ \\t]*$",
        setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
    )

    // Fence tags that aren't literal file extensions.
    private val LANG_TO_EXT = mapOf(
        "kotlin" to "kt", "python" to "py", "javascript" to "js",
        "typescript" to "ts", "shell" to "sh", "bash" to "sh", "zsh" to "sh",
        "yaml" to "yml", "markdown" to "md", "rust" to "rs", "ruby" to "rb",
        "c++" to "cpp", "csharp" to "cs", "text" to "txt", "plain" to "txt",
    )

    fun render(project: Project, body: String, wrapWidth: Int): JComponent {
        val box = Box.createVerticalBox()
        var consumedUpTo = 0
        for (match in FENCE.findAll(body)) {
            val textBefore = body.substring(consumedUpTo, match.range.first)
            if (textBefore.isNotBlank()) box.add(htmlPane(textBefore, wrapWidth))
            box.add(Box.createVerticalStrut(JBUI.scale(4)))
            box.add(codeBlock(project, match.groupValues[1], match.groupValues[2].trimEnd('\n')))
            box.add(Box.createVerticalStrut(JBUI.scale(4)))
            consumedUpTo = match.range.last + 1
        }
        val remainder = body.substring(consumedUpTo)
        if (remainder.isNotBlank()) box.add(htmlPane(remainder, wrapWidth))
        return box
    }

    /** Strip markdown syntax for one-line previews (tooltips, tool window rows). */
    fun previewText(body: String): String =
        body.replace(FENCE) { " ${it.groupValues[2].take(40)} " }
            .replace(Regex("[`*_#>]"), "")
            .replace('\n', ' ')
            .trim()

    private fun htmlPane(markdown: String, wrapWidth: Int): JComponent {
        val flavour = CommonMarkFlavourDescriptor()
        val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        val html = HtmlGenerator(markdown, tree, flavour).generateHtml()
            .removePrefix("<body>").removeSuffix("</body>")
            // Lite scope: no image loading from message bodies.
            .replace(Regex("<img[^>]*>"), "[image]")

        val pane = JEditorPane()
        val kit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
        // Margin-scale headings: the default HTML sizes are document scale,
        // and a 2x h1 inside a margin panel towers over the code it
        // annotates. Headings here mean structure, not volume — a notch
        // above body text, bold carrying the rest.
        val base = JBUI.Fonts.label().size
        kit.styleSheet.addRule("h1 { font-size: ${(base * 1.2f).toInt()}pt; margin: 6px 0 2px 0; }")
        kit.styleSheet.addRule("h2 { font-size: ${(base * 1.1f).toInt()}pt; margin: 5px 0 2px 0; }")
        kit.styleSheet.addRule("h3, h4, h5, h6 { font-size: ${base}pt; margin: 4px 0 2px 0; }")
        pane.editorKit = kit
        pane.isEditable = false
        pane.isOpaque = false
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        pane.font = JBUI.Fonts.label()
        pane.text = "<html><body>$html</body></html>"
        pane.addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                e.url?.let { BrowserUtil.browse(it) }
            }
        }
        // Measure at the target width so preferred height reflects wrapping
        // (the recurring inlay-sizing dragon; see ThreadPanel).
        pane.setSize(wrapWidth, Int.MAX_VALUE)
        pane.alignmentX = Component.LEFT_ALIGNMENT
        return pane
    }

    /** Fenced block → read-only editor fragment: real lexer, user's color scheme. */
    private fun codeBlock(project: Project, langTag: String, code: String): JComponent {
        val document = EditorFactory.getInstance().createDocument(code)
        val field = EditorTextField(document, project, fileTypeFor(langTag), true, false)
        field.border = JBUI.Borders.customLine(JBColor.border(), 1)
        field.alignmentX = Component.LEFT_ALIGNMENT
        return field
    }

    private fun fileTypeFor(langTag: String): FileType {
        if (langTag.isBlank()) return PlainTextFileType.INSTANCE
        val ext = LANG_TO_EXT[langTag.lowercase()] ?: langTag.lowercase()
        val byName = FileTypeManager.getInstance().getFileTypeByFileName("snippet.$ext")
        return if (byName is UnknownFileType) PlainTextFileType.INSTANCE else byName
    }
}
