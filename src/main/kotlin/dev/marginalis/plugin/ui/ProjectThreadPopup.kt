package dev.marginalis.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Message
import dev.marginalis.plugin.store.MarginalisStore
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

/**
 * Where a thread about the project unfolds. Every other thread has a place
 * in the code to open beside — a line, or the top of its file; this one has
 * none, so it gets a window of its own rather than borrowing some innocent
 * file's margin and pretending to be about it.
 *
 * The panel inside is the same one the editor hosts, so the conversation,
 * the composer and the step buttons all behave identically.
 */
object ProjectThreadPopup {

    private const val MAX_HEIGHT = 520
    private const val BOTTOM_SLACK = 8
    private const val SCROLL_STEP = 16

    /** Read and reply: an existing thread, opened from the tool window or a walk. */
    fun open(project: Project, thread: CommentThread, revealing: Message? = null) = show(project, thread, revealing) {}

    /**
     * A thread being started: nothing is stored until the first message is
     * sent, so an abandoned draft leaves no trace — the same bargain the
     * line and file gestures make.
     */
    fun openDraft(project: Project, thread: CommentThread) = show(project, thread, revealing = null) {
        val store = MarginalisStore.getInstance(project)
        if (store.threads.byId(thread.id) == null) store.threads.add(thread)
    }

    private fun show(project: Project, thread: CommentThread, revealing: Message?, ensureStored: () -> Unit) {
        var popup: JBPopup? = null
        val panel = ThreadPanel(project, editor = null, thread = thread, ensureStored = ensureStored) {
            popup?.cancel()
        }
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollingToNewest(panel), panel)
            .setTitle(project.name)
            .setRequestFocus(true)
            .setMovable(true)
            .setResizable(true)
            .setCancelOnClickOutside(false)
            .setCancelButton(IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered))
            .createPopup()
        Disposer.register(MarginalisStore.getInstance(project), popup)
        popup.showCenteredInCurrentWindow(project)
        ApplicationManager.getApplication().invokeLater {
            panel.focusDefault()
            revealing?.let(panel::reveal)
        }
    }

    private fun scrollingToNewest(panel: ThreadPanel): JComponent {
        val view = WidthTrackingView(panel)
        val scroll = JBScrollPane(view, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER).apply {
            border = JBUI.Borders.empty()
        }
        var lastHeight = 0
        view.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                val bar = scroll.verticalScrollBar
                val wasAtBottom = bar.value + bar.visibleAmount >= lastHeight - JBUI.scale(BOTTOM_SLACK)
                lastHeight = view.height
                if (wasAtBottom) bar.value = bar.maximum
            }
        })
        return scroll
    }

    private class WidthTrackingView(content: JComponent) : JPanel(BorderLayout()), Scrollable {
        init {
            isOpaque = false
            add(content, BorderLayout.CENTER)
        }

        override fun getPreferredScrollableViewportSize(): Dimension =
            preferredSize.let { Dimension(it.width, it.height.coerceAtMost(JBUI.scale(MAX_HEIGHT))) }
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) =
            JBUI.scale(SCROLL_STEP)
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }

}
