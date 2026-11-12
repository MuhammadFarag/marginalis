package dev.marginalis.plugin.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Message
import dev.marginalis.core.ThreadListing
import dev.marginalis.core.WalkPosition
import dev.marginalis.core.Walkthrough
import dev.marginalis.plugin.settings.MarginalisSettings
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.tab.ProjectTab
import java.time.Instant

object WalkthroughNavigator {

    fun walkFrom(project: Project, thread: CommentThread): WalkPosition =
        Walkthrough.walkFrom(MarginalisStore.getInstance(project).threads.all(), thread)

    fun stableTotal(project: Project, thread: CommentThread): Int? =
        Walkthrough.stableTotal(MarginalisStore.getInstance(project).threads.all(), thread)

    fun startTime(): (Instant) -> String = MarginalisSettings.getInstance().timeFormat.formatter()::format

    fun title(project: Project, thread: CommentThread): String =
        ThreadListing.titleOf(MarginalisStore.getInstance(project).threads.all(), thread, startTime())

    fun navigateTo(
        project: Project,
        thread: CommentThread,
        revealing: Message? = null,
        onShown: () -> Unit = {},
        onNotShown: (problem: String) -> Unit = { warn(project, it) },
    ) {
        val lastStep = LastStep.getInstance(project)
        if (thread.file == null) {
            lastStep.landedOn(thread)
            ProjectTab.reveal(project, thread, revealing)
            return onShown()
        }
        val place = placeOf(project, thread) ?: return onNotShown(couldNotShow(project, thread, "${thread.file} is no longer in the project"))
        val editors = FileEditorManager.getInstance(project)
        fun closedAsItOpened() = couldNotShow(project, thread, "the IDE closed ${thread.file} as it opened")
        // The step file's own editor: when its tab closes while opening, the selected one is still the previous file's.
        val editor = editors.openTextEditor(place, true)
            ?: return onNotShown(
                if (editors.isFileOpen(place.file)) couldNotShow(project, thread, "${thread.file} doesn't open in a text editor")
                else closedAsItOpened(),
            )
        if (editor.isDisposed) return onNotShown(closedAsItOpened())
        val stepBefore = lastStep.mark
        ThreadInlayManager.open(project, editor, thread, revealing)
        // A file whose preview editor fails to build (Swagger UI on an OpenAPI spec) opens and is closed right after.
        ApplicationManager.getApplication().invokeLater({
            if (editor.isDisposed || !editors.isFileOpen(place.file)) {
                lastStep.returnTo(stepBefore)
                onNotShown(closedAsItOpened())
            } else {
                onShown()
            }
        }, project.disposed)
    }

    fun navigateWithin(editor: Editor, project: Project, thread: CommentThread, beforeMoving: () -> Unit = {}): Boolean {
        if (editor.isDisposed) return false
        val place = placeOf(project, thread)?.takeIf { editor.shows(it.file) } ?: return false
        beforeMoving()
        place.navigateInRecordingHistory(editor)
        editor.contentComponent.requestFocusInWindow()
        ThreadInlayManager.open(project, editor, thread)
        return true
    }

    fun warn(project: Project, problem: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("Marginalis")
            .createNotification(StringUtil.escapeXmlEntities(problem), NotificationType.WARNING)
            .notify(project)
    }

    private fun couldNotShow(project: Project, thread: CommentThread, why: String): String {
        val what = thread.order?.let { order -> "walkthrough step $order" + stableTotal(project, thread)?.let { "/$it" }.orEmpty() } ?: "the thread"
        return "Couldn't show $what: $why."
    }

    private fun placeOf(project: Project, thread: CommentThread): OpenFileDescriptor? {
        val path = thread.file ?: return null
        val file = project.guessProjectDir()?.findFileByRelativePath(path) ?: return null
        val line = MarginalisStore.getInstance(project).syncLine(thread) ?: 0
        return OpenFileDescriptor(project, file, line, 0)
    }

    private fun Editor.shows(file: VirtualFile): Boolean = FileDocumentManager.getInstance().getFile(document) == file

    private fun OpenFileDescriptor.navigateInRecordingHistory(editor: Editor) {
        CommandProcessor.getInstance().executeCommand(project, {
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
            navigateIn(editor)
        }, null, null)
    }
}
