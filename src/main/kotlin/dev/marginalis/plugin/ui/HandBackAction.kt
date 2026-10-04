package dev.marginalis.plugin.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.IconLoader
import dev.marginalis.core.WaitingAgents
import dev.marginalis.plugin.store.MarginalisStore

internal class HandBackAction :
    AnAction("Hand Back", "Hand the turn back to the waiting agents", MarginalisIcons.HandBack) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val waitingNames = MarginalisStore.getInstance(project).handBack.waitingNames
        e.presentation.icon =
            if (waitingNames.isNotEmpty()) MarginalisIcons.HandBack else IconLoader.getDisabledIcon(MarginalisIcons.HandBack)
        e.presentation.text = WaitingAgents.handBackText(waitingNames)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        MarginalisStore.getInstance(project).recordHandBack()
    }
}
