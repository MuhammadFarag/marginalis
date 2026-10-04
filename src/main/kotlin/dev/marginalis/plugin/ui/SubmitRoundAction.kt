package dev.marginalis.plugin.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import dev.marginalis.plugin.store.MarginalisStore

internal class SubmitRoundAction : AnAction("Submit round", SENDS_ROUND, MarginalisIcons.SubmitRound), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val canSubmitRound = MarginalisStore.getInstance(project).handBack.canSubmitRound
        e.presentation.isEnabled = canSubmitRound
        e.presentation.description = if (canSubmitRound) SENDS_ROUND else NOBODY_LISTENS
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        MarginalisStore.getInstance(project).recordHandBack()
    }

    private companion object {
        const val SENDS_ROUND = "Send every reply you've written this round"
        const val NOBODY_LISTENS = "No agent is listening"
    }
}
