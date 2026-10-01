package dev.marginalis.plugin.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Message
import dev.marginalis.core.WalkPosition
import dev.marginalis.core.Walkthrough
import dev.marginalis.plugin.store.MarginalisStore

object WalkthroughNavigator {

    fun walkFrom(project: Project, thread: CommentThread): WalkPosition =
        Walkthrough.walkFrom(MarginalisStore.getInstance(project).threads.all(), thread)

    fun stableTotal(project: Project, thread: CommentThread): Int? =
        Walkthrough.stableTotal(MarginalisStore.getInstance(project).threads.all(), thread)

    fun navigateTo(project: Project, thread: CommentThread, revealing: Message? = null) {
        val path = thread.file ?: return ProjectThreadPopup.open(project, thread, revealing)
        val base = project.guessProjectDir() ?: return
        val vFile = base.findFileByRelativePath(path) ?: return
        val line = MarginalisStore.getInstance(project).syncLine(thread) ?: 0
        OpenFileDescriptor(project, vFile, line, 0).navigate(true)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        ThreadInlayManager.open(project, editor, thread, revealing)
    }
}
