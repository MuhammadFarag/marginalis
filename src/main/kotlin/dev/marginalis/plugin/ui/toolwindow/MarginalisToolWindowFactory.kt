package dev.marginalis.plugin.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.OccurenceNavigator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.pom.Navigatable
import com.intellij.ui.BadgeIconSupplier
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Severity
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.store.Authors
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.MarkdownRenderer
import dev.marginalis.plugin.ui.WalkthroughNavigator
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
 * The cross-file answer to "which files have notes?": every thread in the
 * project, each status section as a directory tree — Open expanded,
 * Resolved folded (the session's record of what concluded, consulted by
 * file). Double-click or F4 (Jump to Source) navigates to the anchor line
 * and opens the thread panel.
 */
class MarginalisToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MarginalisToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        // Walk the steps of a section like a walkthrough: first/prev/next/last.
        // Prev/next are platform occurrence actions, so they arrive with the
        // standard icons and shortcuts (⌘⌥↑ / ⌘⌥↓).
        val common = CommonActionsManager.getInstance()
        toolWindow.setTitleActions(
            listOf(
                FirstStepAction(panel),
                common.createPrevOccurenceAction(panel),
                common.createNextOccurenceAction(panel),
                LastStepAction(panel),
                BlockersOnlyAction(panel),
                ResolveAllAction(),
                ClearAllAction(),
            ),
        )

        // "Is it my turn?" answered from anywhere: a badge on the stripe icon
        // and a count next to the title whenever open threads await the user
        // (the agent spoke last). The margin is turn-based; this is the turn
        // signal, not presence.
        val refreshBadge = {
            val open = MarginalisStore.getInstance(project).threads.all()
                .filter { it.status is ThreadStatus.Open }
            val awaiting = open.count { it.awaitsUser() }
            val blockers = open.count { it.severity == Severity.BLOCKER }
            // Red = act, blue = read: open blockers outrank the turn signal.
            toolWindow.setIcon(
                if (blockers > 0) STRIPE_ICON.getErrorIcon(true) else STRIPE_ICON.getInfoIcon(awaiting > 0),
            )
            content.displayName = if (awaiting > 0) "$awaiting awaiting you" else ""
        }
        MarginalisStore.getInstance(project).threads.addListener {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed && !toolWindow.isDisposed) refreshBadge()
            }
        }
        refreshBadge()
    }

    private companion object {
        val STRIPE_ICON = BadgeIconSupplier(AllIcons.Toolwindows.ToolWindowMessages)
    }
}

/** Jump to a section's first step; disabled when already there. */
private class FirstStepAction(private val panel: MarginalisToolWindowPanel) :
    AnAction("First Step", "Go to the first step in this section", AllIcons.Actions.Play_first) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = panel.canGoFirst()
    }

    override fun actionPerformed(e: AnActionEvent) = panel.goFirst()
}

/** Jump to a section's last step; disabled when already there. */
private class LastStepAction(private val panel: MarginalisToolWindowPanel) :
    AnAction("Last Step", "Go to the last step in this section", AllIcons.Actions.Play_last) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = panel.canGoLast()
    }

    override fun actionPerformed(e: AnActionEvent) = panel.goLast()
}

/** The gate check: show only blockers; step-walking then walks the blockers. */
private class BlockersOnlyAction(private val panel: MarginalisToolWindowPanel) :
    ToggleAction("Blockers Only", "Show only blocker threads", AllIcons.General.Filter), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = panel.blockersOnly

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        panel.blockersOnly = state
    }
}

/** Resolve every open/orphaned thread — the "consolidation is done" sweep. */
private class ResolveAllAction : AnAction("Resolve All", "Mark every open thread resolved", AllIcons.Actions.Selectall) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            MarginalisStore.getInstance(project).threads.all().any { it.status !is ThreadStatus.Resolved }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val store = MarginalisStore.getInstance(project)
        val pending = store.threads.all().filter { it.status !is ThreadStatus.Resolved }
        if (pending.isEmpty()) return
        val blockers = pending.count { it.severity == Severity.BLOCKER }
        val blockerWarning =
            if (blockers > 0) " $blockers of them are blockers — resolve only if their outcomes genuinely landed." else ""
        val answer = Messages.showYesNoDialog(
            project,
            "Resolve all ${pending.size} open thread(s)?$blockerWarning Their gutter markers will be removed; " +
                "the threads remain in the Resolved log.",
            "Resolve All Margin Threads",
            if (blockers > 0) Messages.getWarningIcon() else Messages.getQuestionIcon(),
        )
        if (answer != Messages.YES) return
        for (thread in pending) {
            thread.resolve(Authors.user)
            store.threads.notifyChanged(thread)
        }
    }
}

/** Delete everything, including the resolved log. Destructive; confirms first. */
private class ClearAllAction : AnAction("Delete All", "Delete all threads, including resolved ones", AllIcons.Actions.GC) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && MarginalisStore.getInstance(project).threads.all().isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val store = MarginalisStore.getInstance(project)
        val count = store.threads.all().size
        if (count == 0) return
        val blockers = store.threads.all().count { it.status !is ThreadStatus.Resolved && it.severity == Severity.BLOCKER }
        val blockerWarning = if (blockers > 0) " $blockers open blocker(s) are among them." else ""
        val answer = Messages.showYesNoDialog(
            project,
            "Delete all $count margin thread(s), including the resolved log?$blockerWarning This cannot be undone.",
            "Delete All Margin Threads",
            Messages.getWarningIcon(),
        )
        if (answer != Messages.YES) return
        store.threads.clear() // marker cleanup happens in the store listener (deleted-thread branch)
    }
}

private sealed class NodeData {
    class Section(val title: String, val count: Int, val blockers: Int = 0) : NodeData()
    class DirNode(val name: String, val count: Int) : NodeData()
    class FileNode(val name: String, val threads: List<CommentThread>) : NodeData()
    class ThreadNode(val thread: CommentThread, val walkthroughPrefix: String? = null) : NodeData()
}



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

    /** Earliest walkthrough step beneath this node — lets guided trees read in walkthrough order. */
    fun minOrder(): Int = minOf(
        files.values.flatten().mapNotNull { it.order }.minOrNull() ?: Int.MAX_VALUE,
        dirs.values.minOfOrNull { it.minOrder() } ?: Int.MAX_VALUE,
    )
}

private class MarginalisToolWindowPanel(private val project: Project) :
    JPanel(BorderLayout()), UiDataProvider, OccurenceNavigator {

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

        MarginalisStore.getInstance(project).threads.addListener {
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

    // ------------------------------------------------- step-by-step walking
    //
    // The tree selection is the cursor. The walk is scoped to the section
    // (Guided A, Guided B, Open, …) holding the selection — a walkthrough never
    // bleeds into its neighbor — and runs in display order, which is walkthrough
    // order in Guided sections and file-then-line order elsewhere. With no
    // selection, the walk starts at the first section's first step.

    /** Thread nodes of the active section in display order, plus the cursor index (-1 = before first). */
    private fun steps(): Pair<List<DefaultMutableTreeNode>, Int> {
        val none = emptyList<DefaultMutableTreeNode>() to -1
        val root = tree.model.root as? DefaultMutableTreeNode ?: return none
        val selected = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val section = selected?.path?.getOrNull(1) as? DefaultMutableTreeNode
            ?: root.children().asSequence().filterIsInstance<DefaultMutableTreeNode>().firstOrNull()
            ?: return none
        val walk = section.preorderEnumeration().asSequence()
            .filterIsInstance<DefaultMutableTreeNode>()
            .filter { it.userObject is NodeData.ThreadNode }
            .toList()
        val index = if (selected?.userObject is NodeData.ThreadNode) walk.indexOf(selected) else -1
        return walk to index
    }

    private fun goTo(node: DefaultMutableTreeNode) {
        val path = TreePath(node.path)
        tree.selectionPath = path
        tree.scrollPathToVisible(path)
        (node.userObject as? NodeData.ThreadNode)?.thread?.let { navigateTo(it) }
    }

    override fun hasNextOccurence(): Boolean = steps().let { (walk, i) -> i < walk.size - 1 && walk.isNotEmpty() }

    override fun hasPreviousOccurence(): Boolean = steps().second > 0

    override fun goNextOccurence(): OccurenceNavigator.OccurenceInfo? {
        val (walk, i) = steps()
        val target = walk.getOrNull(i + 1) ?: return null
        goTo(target)
        return OccurenceNavigator.OccurenceInfo.position(i + 2, walk.size)
    }

    override fun goPreviousOccurence(): OccurenceNavigator.OccurenceInfo? {
        val (walk, i) = steps()
        val target = walk.getOrNull(i - 1) ?: return null
        goTo(target)
        return OccurenceNavigator.OccurenceInfo.position(i, walk.size)
    }

    override fun getNextOccurenceActionName(): String = "Next Step"

    override fun getPreviousOccurenceActionName(): String = "Previous Step"

    fun canGoFirst(): Boolean = steps().let { (walk, i) -> walk.isNotEmpty() && i != 0 }

    fun canGoLast(): Boolean = steps().let { (walk, i) -> walk.isNotEmpty() && i != walk.size - 1 }

    fun goFirst() {
        steps().first.firstOrNull()?.let { goTo(it) }
    }

    fun goLast() {
        steps().first.lastOrNull()?.let { goTo(it) }
    }

    /**
     * The pre-merge gate check: filter the tree to blockers, and because
     * step-walking follows the tree as displayed, first/next/prev/last
     * become "walk the blockers" for free. The empty state is the answer
     * everyone wants.
     */
    var blockersOnly: Boolean = false
        set(value) {
            field = value
            rebuild()
        }

    fun rebuild() {
        val store = MarginalisStore.getInstance(project)
        store.syncLines() // refresh live lines + orphan status from markers
        val threads = store.threads.all()
            .filter { !blockersOnly || it.severity == Severity.BLOCKER }
        tree.emptyText.text = if (blockersOnly) "No blockers" else "No margin threads yet"

        val root = DefaultMutableTreeNode()
        addGuidedSection(root, threads)
        addTreeSection(root, "Open", threads.filter { it.status is ThreadStatus.Open })
        addTreeSection(root, "Orphaned", threads.filter { it.status is ThreadStatus.Orphaned })
        addTreeSection(root, "Resolved", threads.filter { it.status is ThreadStatus.Resolved })

        tree.model = DefaultTreeModel(root)
        // Everything expanded by default except the Resolved log.
        for (i in 0 until root.childCount) {
            val section = root.getChildAt(i) as DefaultMutableTreeNode
            if ((section.userObject as NodeData.Section).title != "Resolved") expandRecursively(section)
        }
    }

    /**
     * The walkthroughs: agent-ordered open threads, across files, in "look here
     * 1st, 2nd, …" sequence — the agent's answer to "where should I look?".
     * Several walkthroughs coexist via labels; positions render compactly as
     * (1/4), or (A1/4) once more than one walkthrough is present. The total is
     * fixed for the life of the walkthrough — resolving step 2 of 5 must not turn
     * (4/5) into (4/4); a position only means something against a stable
     * denominator — so it comes from every thread in the walkthrough regardless
     * of status.
     */
    private fun addGuidedSection(root: DefaultMutableTreeNode, allThreads: List<CommentThread>) {
        val openStops = allThreads.filter { it.status is ThreadStatus.Open && it.order != null }
        if (openStops.isEmpty()) return
        val walkthroughs = openStops.groupBy { it.walkthrough ?: "" }.toSortedMap()
        val labelNeeded = walkthroughs.size > 1
        for ((label, walkthroughThreads) in walkthroughs) {
            val title = if (label.isEmpty()) "Guided" else "Guided $label"
            val section = DefaultMutableTreeNode(
                NodeData.Section(title, walkthroughThreads.size, walkthroughThreads.count { it.severity == Severity.BLOCKER }),
            )
            val total = WalkthroughNavigator.stableTotal(project, walkthroughThreads.first())
                ?: walkthroughThreads.size
            val shownLabel = if (labelNeeded && label.isNotEmpty()) label else ""
            val trie = PathTrie().apply { walkthroughThreads.forEach(::insert) }
            emitTrie(trie, section, prefixFor = { thread -> "($shownLabel${thread.order}/$total)" })
            root.add(section)
        }
    }

    /**
     * Every status section shares the directory tree — Resolved included:
     * real usage consults it by file ("what did we decide here?"), not by
     * time, and it's cleared session-to-session anyway (operator finding).
     * The blocker count only ever counts unresolved threads, so Resolved
     * never alarms in red about gates already passed.
     */
    private fun addTreeSection(root: DefaultMutableTreeNode, title: String, threads: List<CommentThread>) {
        if (threads.isEmpty()) return
        val blockers = threads.count { it.status !is ThreadStatus.Resolved && it.severity == Severity.BLOCKER }
        val section = DefaultMutableTreeNode(NodeData.Section(title, threads.size, blockers))
        val trie = PathTrie().apply { threads.forEach(::insert) }
        emitTrie(trie, section)
        root.add(section)
    }

    /**
     * Emit the trie, compressing single-child directory chains (a/b/c → one
     * node). With [prefixFor] (guided mode), directories/files sort by their
     * earliest walkthrough step and threads by walkthrough order, so the tree reads
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

    private fun expandRecursively(node: DefaultMutableTreeNode) {
        tree.expandPath(TreePath(node.path))
        for (i in 0 until node.childCount) {
            expandRecursively(node.getChildAt(i) as DefaultMutableTreeNode)
        }
    }

    private fun navigateTo(thread: CommentThread) = WalkthroughNavigator.navigateTo(project, thread)
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
                if (data.blockers > 0) {
                    append("  ·  ${data.blockers} blocker${if (data.blockers > 1) "s" else ""}", SimpleTextAttributes.ERROR_ATTRIBUTES)
                }
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
                // Turn dots are for live conversations only — under Resolved
                // they'd be noise about turns already over.
                val open = data.threads.filter { it.status is ThreadStatus.Open }
                val needsYou = open.count { it.awaitsUser() }
                val onClaude = open.size - needsYou
                if (needsYou > 0) append("  ●$needsYou", VIOLET_ATTRS)
                if (onClaude > 0) append("  ○$onClaude", BLUE_ATTRS)
            }
            is NodeData.ThreadNode -> {
                val thread = data.thread
                icon = when {
                    thread.status is ThreadStatus.Resolved -> AllIcons.General.GreenCheckmark
                    thread.status is ThreadStatus.Orphaned -> AllIcons.General.Warning
                    else -> AllIcons.General.Balloon
                }
                if (data.walkthroughPrefix != null) {
                    append("${data.walkthroughPrefix}  ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                append("L${thread.line + 1}  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                // One loud mark, one quiet mark, silence: word + color, never
                // color alone. A nit de-emphasizes its whole row.
                when (thread.severity) {
                    Severity.BLOCKER -> append("blocker  ", SimpleTextAttributes.ERROR_ATTRIBUTES)
                    Severity.NIT -> append("nit  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    null -> {}
                }
                val preview = MarkdownRenderer.previewText(thread.messages.firstOrNull()?.body ?: "")
                append(
                    StringUtil.shortenTextWithEllipsis(preview, 70, 0),
                    if (thread.severity == Severity.NIT) SimpleTextAttributes.GRAYED_ATTRIBUTES
                    else SimpleTextAttributes.REGULAR_ATTRIBUTES,
                )
                if (thread.status is ThreadStatus.Open) {
                    append(if (thread.awaitsUser()) "  ●" else "  ○", if (thread.awaitsUser()) VIOLET_ATTRS else BLUE_ATTRS)
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
