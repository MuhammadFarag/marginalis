package dev.marginalis.plugin.ui.tab

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Message

object ProjectTab {

    private const val REOPEN_AFTER_RELOAD = "marginalis.projectTab.reopen"
    private const val PINNED_BEFORE_RELOAD = "marginalis.projectTab.pinned"
    private const val INTRODUCED = "marginalis.projectTab.introduced"

    fun reveal(project: Project, thread: CommentThread, revealing: Message? = null) =
        deliver(project, TabRequest.Reveal(thread.id, revealing))

    fun draftNew(project: Project) {
        ProjectTabState.getInstance(project).ensureDraft()
        deliver(project, TabRequest.DraftNew)
    }

    fun isInFront(project: Project): Boolean =
        FileEditorManager.getInstance(project).selectedEditor?.file == MarginFile

    fun refreshPresentation(project: Project) {
        val editors = FileEditorManager.getInstance(project)
        if (editors.isFileOpen(MarginFile)) editors.updateFilePresentation(MarginFile)
    }

    fun applyPrefs() {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            FileEditorManager.getInstance(project).getAllEditors(MarginFile)
                .filterIsInstance<MarginEditor>()
                .forEach(MarginEditor::applyPrefs)
        }
    }

    fun closeEverywhere() {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val editors = FileEditorManager.getInstance(project)
            val properties = PropertiesComponent.getInstance(project)
            properties.setValue(REOPEN_AFTER_RELOAD, editors.isFileOpen(MarginFile))
            properties.setValue(PINNED_BEFORE_RELOAD, isPinned(project))
            editors.closeFile(MarginFile)
            EditorHistoryManager.getInstance(project).removeFile(MarginFile)
        }
        MarginFile.isValid = false
    }

    fun openOnStartup(project: Project) {
        val properties = PropertiesComponent.getInstance(project)
        val reopen = properties.getBoolean(REOPEN_AFTER_RELOAD)
        val pinned = properties.getBoolean(PINNED_BEFORE_RELOAD)
        properties.unsetValue(REOPEN_AFTER_RELOAD)
        properties.unsetValue(PINNED_BEFORE_RELOAD)
        val firstRun = !properties.getBoolean(INTRODUCED)
        properties.setValue(INTRODUCED, true)
        if (reopen || firstRun) openBehindSelection(project, pin = pinned || firstRun)
    }

    private fun openBehindSelection(project: Project, pin: Boolean) {
        val editors = FileEditorManager.getInstance(project)
        val selected = editors.selectedFiles.firstOrNull()
        editors.openFile(MarginFile, false, true)
        if (pin) pin(project)
        selected?.let { editors.openFile(it, false, true) }
    }

    private fun isPinned(project: Project): Boolean =
        FileEditorManagerEx.getInstanceEx(project).windows.any { it.isFilePinned(MarginFile) }

    private fun pin(project: Project) =
        FileEditorManagerEx.getInstanceEx(project).windows
            .filter { it.isFileOpen(MarginFile) }
            .forEach { it.setFilePinned(MarginFile, true) }

    private fun deliver(project: Project, request: TabRequest) {
        val state = ProjectTabState.getInstance(project)
        state.request(request)
        val shown = FileEditorManager.getInstance(project).openFile(MarginFile, true, true).filterIsInstance<MarginEditor>()
        val pending = state.takeRequest() ?: return
        shown.forEach { it.handle(pending) }
    }
}
