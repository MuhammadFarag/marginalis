package dev.marginalis.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import dev.marginalis.plugin.store.Authors
import dev.marginalis.plugin.store.MarginalisStore

internal class StopAgentsGroup : ActionGroup("Stop agents", "Stop an agent listening or working on your round", AllIcons.General.ArrowDown), DumbAware {
    init {
        isPopup = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && MarginalisStore.getInstance(project).handBack.presence.isNotEmpty()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        val handBack = MarginalisStore.getInstance(project).handBack
        val faces = Authors.faces
        val stopEach = handBack.presence.map { presence ->
            val key = presence.agent.receiptKey
            StopAction("Stop ${faces.of(presence.agent).name}") { handBack.stop(key) }
        }
        if (stopEach.isEmpty()) return emptyArray()
        return (stopEach + Separator.getInstance() + StopAction("Stop all agents") { handBack.stopAll() }).toTypedArray()
    }
}

internal class StopAction(text: String, private val stop: () -> Unit) : DumbAwareAction() {
    init {
        templatePresentation.setText(text, false)
    }

    override fun actionPerformed(e: AnActionEvent) = stop()
}
