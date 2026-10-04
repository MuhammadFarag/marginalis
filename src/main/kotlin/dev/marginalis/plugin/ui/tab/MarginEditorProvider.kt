package dev.marginalis.plugin.ui.tab

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

// DumbAware: editors restore during startup indexing, and a provider that
// isn't shows the "not available while indexing" placeholder instead.
class MarginEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean = file === MarginFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = MarginEditor(project)

    override fun getEditorTypeId(): String = "marginalis-project-tab"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
