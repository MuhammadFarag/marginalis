package dev.marginalis.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.ThreadGutterIconRenderer

/**
 * Keeps the collapsed state honest: whenever a thread changes (reply,
 * resolve/reopen, from either party), re-set its gutter renderer so the icon
 * reflects the new status/unread state. Setting the renderer triggers the
 * repaint; the renderer's equals() makes it a no-op when nothing visible
 * changed.
 */
class MarginalisStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        MarginalisStore.getInstance(project).addListener { thread ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val highlighter = thread.highlighter ?: return@invokeLater
                if (highlighter.isValid) {
                    highlighter.gutterIconRenderer = ThreadGutterIconRenderer(project, thread)
                }
            }
        }
    }
}
