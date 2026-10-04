package dev.marginalis.plugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicBoolean

class CoalescedEdtRunner(private val project: Project, private val action: () -> Unit) {

    private val queued = AtomicBoolean(false)

    fun request() {
        if (!queued.compareAndSet(false, true)) return
        ApplicationManager.getApplication().invokeLater {
            queued.set(false)
            if (!project.isDisposed) action()
        }
    }
}
