package dev.marginalis.plugin.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.pom.Navigatable
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import dev.marginalis.plugin.store.Author
import dev.marginalis.plugin.store.AuthorKind
import dev.marginalis.plugin.store.CommentThread
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.store.ThreadStatus
import dev.marginalis.plugin.ui.MarkdownRenderer
import dev.marginalis.plugin.ui.ThreadInlayManager
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.SortedMap
import java.util.TreeMap
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * The cross-file answer to "which files have notes?" (handover §8): every
 * thread in the project — Open as a directory tree (expanded), Orphaned, and
 * Resolved (folded; doubles as the decision log, §10). Double-click or F4
 * (Jump to Source) navigates to the anchor line and opens the thread panel.
 */
class MarginalisToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MarginalisToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        toolWindow.setTitleActions(listOf(ResolveAllAction(), ClearAllAction()))
    }
}

/** Resolve every open/orphaned thread — the "consolidation is done" sweep. */
private class ResolveAllAction : AnAction("Resolve All", "Mark every open thread resolved", AllIcons.Actions.Selectall) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            MarginalisStore.getInstance(project).all().any { it.status != ThreadStatus.RESOLVED }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val store = MarginalisStore.getInstance(project)
        val pending = store.all().filter { it.status != ThreadStatus.RESOLVED }
        if (pending.isEmpty()) return
        val answer = Messages.showYesNoDialog(
            project,
            "Resolve all ${pending.size} open thread(s)? Their gutter markers will be removed; " +
                "the threads remain in the Resolved log.",
            "Resolve All Margin Threads",
            Messages.getQuestionIcon(),
        )
        if (answer != Messages.YES) return
        for (thread in pending) {
            thread.resolve(Author.HUMAN)
            store.notifyChanged(thread)
        }
    }
}

/** Delete everything, including the resolved log. Destructive; confirms first. */
private class ClearAllAction : AnAction("Clear All", "Delete all threads, including resolved ones", AllIcons.Actions.GC) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && MarginalisStore.getInstance(project).all().isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val store = MarginalisStore.getInstance(project)
        val count = store.all().size
        if (count == 0) return
        val answer = Messages.showYesNoDialog(
            project,
            "Delete all $count margin thread(s), including the resolved log? This cannot be undone.",
            "Clear All Margin Threads",
            Messages.getWarningIcon(),
        )
        if (answer != Messages.YES) return
        store.clear() // marker cleanup happens in the store listener (deleted-thread branch)
    }
}

private sealed class NodeData {
    class Section(val title: String, val count: Int) : NodeData()
    class DirNode(val name: String, val count: Int) : NodeData()
    class FileNode(val name: String, val threads: List<CommentThread>) : NodeData()
    class ThreadNode(val thread: CommentThread, val tourPrefix: String? = null) : NodeData()
}

/** Whose turn is it in this thread? Claude spoke last → the human's. */
private fun awaitsHuman(thread: CommentThread): Boolean =
    thread.messages.lastOrNull()?.author?.kind == AuthorKind.AGENT

/** Path trie for the Open section's directory tree. */
private class PathTrie {
    val dirs: SortedMap<String, PathTrie> = TreeMap()
    val files: SortedMap<String, MutableList<CommentThread>> = TreeMap()

    fun insert(thread: CommentThread) {
        val parts = thread.file.split('/')
        var node = this
        for (dir in parts.dropLast(1)) node = node.dirs.getOrPut(dir) { PathTrie() }
        node.files.getOrPut(parts.last()) { mutableListOf() }.add(thread)
    }

    fun threadCount(): Int = files.values.sumOf { it.size } + dirs.values.sumOf { it.threadCount() }

    /** Earliest tour stop beneath this node — lets guided trees read in tour order. */
    fun minOrder(): Int = minOf(
        files.values.flatten().mapNotNull { it.order }.minOrNull() ?: Int.MAX_VALUE,
        dirs.values.minOfOrNull { it.minOrder() } ?: Int.MAX_VALUE,
    )
}

private class MarginalisToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), UiDataProvider {

    private val tree = Tree()

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = MarginalisTreeRenderer()
        tree.emptyText.text = "No margin threads yet"
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) selectedThread()?.let { navigateTo(it) }
            }
        })
        add(JBScrollPane(tree), BorderLayout.CENTER)

        MarginalisStore.getInstance(project).addListener {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) rebuild()
            }
        }
        rebuild()
    }

    /** F4 / Jump to Source: hand the platform a navigatable for the selection. */
    override fun uiDataSnapshot(sink: DataSink) {
        val thread = selectedThread() ?: return
        sink[CommonDataKeys.NAVIGATABLE] = object : Navigatable {
            override fun navigate(requestFocus: Boolean) = navigateTo(thread)
            override fun canNavigate(): Boolean = true
            override fun canNavigateToSource(): Boolean = true
        }
    }

    private fun selectedThread(): CommentThread? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return (node.userObject as? NodeData.ThreadNode)?.thread
    }

    private fun rebuild() {
        val threads = MarginalisStore.getInstance(project).all()
        threads.forEach { it.currentLine() } // refresh live lines + orphan status

        val root = DefaultMutableTreeNode()
        addGuidedSection(root, threads.filter { it.status == ThreadStatus.OPEN && it.order != null })
        addOpenSection(root, threads.filter { it.status == ThreadStatus.OPEN })
        addFlatSection(root, "Orphaned", threads.filter { it.status == ThreadStatus.ORPHANED })
        addFlatSection(root, "Resolved", threads.filter { it.status == ThreadStatus.RESOLVED })

        tree.model = DefaultTreeModel(root)
        // Everything expanded by default except the Resolved log.
        for (i in 0 until root.childCount) {
            val section = root.getChildAt(i) as DefaultMutableTreeNode
            if ((section.userObject as NodeData.Section).title != "Resolved") expandRecursively(section)
        }
    }

    /**
     * The tours: agent-ordered open threads, across files, in "look here
     * 1st, 2nd, …" sequence — the agent's answer to "where should I look?".
     * Several tours coexist via labels; positions render compactly as
     * (1/4), or (A1/4) once more than one tour is present. Totals are
     * derived live, so they shrink as stops resolve.
     */
    private fun addGuidedSection(root: DefaultMutableTreeNode, threads: List<CommentThread>) {
        if (threads.isEmpty()) return
        val tours = threads.groupBy { it.tour ?: "" }.toSortedMap()
        val labelNeeded = tours.size > 1
        for ((label, tourThreads) in tours) {
            val title = if (label.isEmpty()) "Guided" else "Guided $label"
            val section = DefaultMutableTreeNode(NodeData.Section(title, tourThreads.size))
            val total = tourThreads.size
            val shownLabel = if (labelNeeded && label.isNotEmpty()) label else ""
            val trie = PathTrie().apply { tourThreads.forEach(::insert) }
            emitTrie(trie, section, prefixFor = { thread -> "($shownLabel${thread.order}/$total)" })
            root.add(section)
        }
    }

    private fun addOpenSection(root: DefaultMutableTreeNode, threads: List<CommentThread>) {
        if (threads.isEmpty()) return
        val section = DefaultMutableTreeNode(NodeData.Section("Open", threads.size))
        val trie = PathTrie().apply { threads.forEach(::insert) }
        emitTrie(trie, section)
        root.add(section)
    }

    /**
     * Emit the trie, compressing single-child directory chains (a/b/c → one
     * node). With [prefixFor] (guided mode), directories/files sort by their
     * earliest tour stop and threads by tour order, so the tree reads
     * top-to-bottom in roughly walking order; otherwise alphabetical/by-line.
     */
    private fun emitTrie(
        trie: PathTrie,
        parent: DefaultMutableTreeNode,
        prefixFor: ((CommentThread) -> String)? = null,
    ) {
        val dirEntries =
            if (prefixFor != null) trie.dirs.entries.sortedBy { it.value.minOrder() }
            else trie.dirs.entries.toList()
        for ((name, child) in dirEntries) {
            var display = name
            var node = child
            while (node.files.isEmpty() && node.dirs.size == 1) {
                val (nextName, next) = node.dirs.entries.first()
                display = "$display/$nextName"
                node = next
            }
            val dirTreeNode = DefaultMutableTreeNode(NodeData.DirNode(display, node.threadCount()))
            parent.add(dirTreeNode)
            emitTrie(node, dirTreeNode, prefixFor)
        }
        val fileEntries =
            if (prefixFor != null) {
                trie.files.entries.sortedBy { e -> e.value.mapNotNull { it.order }.minOrNull() ?: Int.MAX_VALUE }
            } else {
                trie.files.entries.toList()
            }
        for ((fileName, fileThreads) in fileEntries) {
            val fileNode = DefaultMutableTreeNode(NodeData.FileNode(fileName, fileThreads))
            val ordered =
                if (prefixFor != null) fileThreads.sortedWith(compareBy({ it.order }, { it.createdAt }))
                else fileThreads.sortedBy { it.line }
            for (thread in ordered) {
                fileNode.add(DefaultMutableTreeNode(NodeData.ThreadNode(thread, prefixFor?.invoke(thread))))
            }
            parent.add(fileNode)
        }
    }

    private fun addFlatSection(root: DefaultMutableTreeNode, title: String, threads: List<CommentThread>) {
        if (threads.isEmpty()) return
        val section = DefaultMutableTreeNode(NodeData.Section(title, threads.size))
        for (thread in threads.sortedBy { it.createdAt }) {
            section.add(DefaultMutableTreeNode(NodeData.ThreadNode(thread)))
        }
        root.add(section)
    }

    private fun expandRecursively(node: DefaultMutableTreeNode) {
        tree.expandPath(TreePath(node.path))
        for (i in 0 until node.childCount) {
            expandRecursively(node.getChildAt(i) as DefaultMutableTreeNode)
        }
    }

    private fun navigateTo(thread: CommentThread) {
        val base = project.guessProjectDir() ?: return
        val vFile = base.findFileByRelativePath(thread.file) ?: return
        OpenFileDescriptor(project, vFile, thread.currentLine(), 0).navigate(true)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        ThreadInlayManager.open(project, editor, thread)
    }
}

private class MarginalisTreeRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val node = value as? DefaultMutableTreeNode ?: return
        when (val data = node.userObject) {
            is NodeData.Section -> {
                append(data.title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append("  ${data.count}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            is NodeData.DirNode -> {
                icon = AllIcons.Nodes.Folder
                append(data.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                append("  ${data.count}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
            is NodeData.FileNode -> {
                // The IDE's own per-filetype icon, so the tree reads like the
                // Project view does.
                icon = FileTypeManager.getInstance().getFileTypeByFileName(data.name).icon
                    ?: AllIcons.FileTypes.Any_type
                append(data.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                val needsYou = data.threads.count { awaitsHuman(it) }
                val onClaude = data.threads.size - needsYou
                if (needsYou > 0) append("  ●$needsYou", VIOLET_ATTRS)
                if (onClaude > 0) append("  ○$onClaude", BLUE_ATTRS)
            }
            is NodeData.ThreadNode -> {
                val thread = data.thread
                icon = when {
                    thread.status == ThreadStatus.RESOLVED -> AllIcons.General.GreenCheckmark
                    thread.status == ThreadStatus.ORPHANED -> AllIcons.General.Warning
                    else -> AllIcons.General.Balloon
                }
                if (data.tourPrefix != null) {
                    append("${data.tourPrefix}  ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                append("L${thread.line + 1}  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                if (thread.status != ThreadStatus.OPEN) {
                    // Flat sections repeat the path; tree sections carry it in structure.
                    append("${thread.file}  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                val preview = MarkdownRenderer.previewText(thread.messages.firstOrNull()?.body ?: "")
                append(StringUtil.shortenTextWithEllipsis(preview, 70, 0))
                if (thread.status == ThreadStatus.OPEN) {
                    append(if (awaitsHuman(thread)) "  ●" else "  ○", if (awaitsHuman(thread)) VIOLET_ATTRS else BLUE_ATTRS)
                }
            }
            else -> {}
        }
    }

    private companion object {
        // Same families as the thread-panel author colors.
        val VIOLET_ATTRS = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor(0x9C27B0, 0xCE93D8))
        val BLUE_ATTRS = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor(0x1565C0, 0x90CAF9))
    }
}
