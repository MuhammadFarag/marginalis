package dev.marginalis.plugin.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import dev.marginalis.core.CodeFence
import dev.marginalis.core.CodeFences
import dev.marginalis.core.Parsed
import dev.marginalis.core.Reference
import dev.marginalis.core.Resolution
import dev.marginalis.plugin.store.MarginalisStore
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.event.HyperlinkEvent
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

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

    private const val FENCE_PREVIEW_CHARS = 40

    fun render(project: Project, body: String, wrapWidth: Int): JComponent {
        val box = Box.createVerticalBox()
        var consumedUpTo = 0
        // Closed fences split out first so they can render natively; an
        // unclosed one is still prose to CommonMark.
        for (fence in closedFences(body)) {
            val textBefore = body.substring(consumedUpTo, fence.start)
            if (textBefore.isNotBlank()) box.add(htmlPane(project, textBefore, wrapWidth))
            box.add(Box.createVerticalStrut(JBUI.scale(4)))
            // A code block ends at its last line of code: trailing blank
            // lines are layout noise in a rendered message.
            box.add(codeBlock(project, fence.language, body.substring(fence.codeStart, fence.codeEnd).trimEnd('\n')))
            box.add(Box.createVerticalStrut(JBUI.scale(4)))
            consumedUpTo = fence.end
        }
        val remainder = body.substring(consumedUpTo)
        if (remainder.isNotBlank()) box.add(htmlPane(project, remainder, wrapWidth))
        return box
    }

    /**
     * One-line preview (tooltips, tool window rows): markdown syntax stripped,
     * each fenced block sampled inline by its first [FENCE_PREVIEW_CHARS]
     * characters of code.
     */
    fun previewText(body: String): String {
        val flattened = StringBuilder()
        var consumedUpTo = 0
        for (fence in closedFences(body)) {
            flattened.append(body, consumedUpTo, fence.start)
                .append(' ').append(body.substring(fence.codeStart, fence.codeEnd).take(FENCE_PREVIEW_CHARS)).append(' ')
            consumedUpTo = fence.end
        }
        flattened.append(body, consumedUpTo, body.length)
        return flattened.toString()
            .replace(Regex("[`*_#>]"), "")
            .replace('\n', ' ')
            .trim()
    }

    private fun closedFences(body: String): List<CodeFence> = CodeFences.find(body).filter { it.closed }

    private fun htmlPane(project: Project, markdown: String, wrapWidth: Int): JComponent {
        val flavour = CommonMarkFlavourDescriptor()
        val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        val html = HtmlGenerator(markdown, tree, flavour).generateHtml()
            .removePrefix("<body>").removeSuffix("</body>")
            // Lite scope: no image loading from message bodies.
            .replace(Regex("<img[^>]*>"), "[image]")
            .let(Reference::linkify)

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
                when (val reference = Reference.parse(e.description)) {
                    is Parsed.Ok -> reference.value?.let { follow(project, it, e) }
                    is Parsed.Invalid -> e.url?.let { BrowserUtil.browse(it) }
                }
            }
        }
        // Selectable text deserves a right-click: Swing installs no context
        // menu on its own, so "copy" was undiscoverable (operator finding).
        pane.componentPopupMenu = JPopupMenu().also { menu ->
            val copy = JMenuItem("Copy").apply { addActionListener { pane.copy() } }
            val selectAll = JMenuItem("Select All").apply { addActionListener { pane.selectAll() } }
            menu.add(copy)
            menu.add(selectAll)
            menu.addPopupMenuListener(object : PopupMenuListener {
                override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
                    copy.isEnabled = !pane.selectedText.isNullOrEmpty()
                }
                override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {}
                override fun popupMenuCanceled(e: PopupMenuEvent) {}
            })
        }
        // Measure at the target width so preferred height reflects wrapping
        // (the recurring inlay-sizing dragon; see ThreadPanel).
        pane.setSize(wrapWidth, Int.MAX_VALUE)
        pane.alignmentX = Component.LEFT_ALIGNMENT
        return pane
    }

    private fun follow(project: Project, reference: Reference, click: HyperlinkEvent) {
        val problem = when (val resolution = reference.resolveIn(MarginalisStore.getInstance(project).threads.all())) {
            is Resolution.Found -> return WalkthroughNavigator.navigateTo(project, resolution.referent.thread, revealing = resolution.referent.message)
            is Resolution.Ambiguous -> "${resolution.summary} in this project"
            Resolution.Unknown -> "$reference names no thread or message in this project"
        }
        val source = click.source as JComponent
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(problem, MessageType.WARNING, null)
            .createBalloon()
            .show((click.inputEvent as? MouseEvent)?.let(::RelativePoint) ?: RelativePoint.getCenterOf(source), Balloon.Position.above)
    }

    /** Fenced block → read-only editor fragment: real lexer, user's color scheme. */
    private fun codeBlock(project: Project, language: String?, code: String): JComponent {
        val document = EditorFactory.getInstance().createDocument(code)
        val field = EditorTextField(document, project, CodeFenceFileTypes.of(language), true, false)
        field.addSettingsProvider { editor ->
            editor.installPopupHandler(
                ContextMenuPopupHandler.Simple(
                    DefaultActionGroup(
                        ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_COPY),
                        ActionManager.getInstance().getAction(IdeActions.ACTION_SELECT_ALL),
                    ),
                ),
            )
        }
        field.border = JBUI.Borders.customLine(JBColor.border(), 1)
        field.alignmentX = Component.LEFT_ALIGNMENT
        return field
    }
}
