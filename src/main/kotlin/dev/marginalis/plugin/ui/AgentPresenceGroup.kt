package dev.marginalis.plugin.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.UIUtil
import dev.marginalis.core.Face
import dev.marginalis.core.Presence
import dev.marginalis.core.PresenceText
import dev.marginalis.plugin.store.Authors
import dev.marginalis.plugin.store.MarginalisStore
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

internal class AgentPresenceGroup : ActionGroup(), DumbAware {
    private val actions = ConcurrentHashMap<String, AgentPresenceAction>()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return emptyArray()
        val present = MarginalisStore.getInstance(project).handBack.presence.map { it.agent.receiptKey }
        actions.keys.retainAll(present.toSet())
        return present.map { key -> actions.computeIfAbsent(key, ::AgentPresenceAction) }.toTypedArray()
    }
}

private class AgentPresenceAction(private val agentKey: String) : DumbAwareAction() {
    @Volatile private var icons: PresenceIcons? = null

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val presence = MarginalisStore.getInstance(project).handBack.presence.firstOrNull { it.agent.receiptKey == agentKey }
        e.presentation.isVisible = presence != null
        if (presence == null) return
        val face = Authors.faces.of(presence.agent)
        e.presentation.setText(PresenceText.tooltip(presence, Instant.now(), face.name), false)
        e.presentation.icon = iconsFor(face, oneShot = !presence.staysListening).of(presence.state)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val handBack = MarginalisStore.getInstance(project).handBack
        val agent = handBack.presence.firstOrNull { it.agent.receiptKey == agentKey }?.agent ?: return
        val stop = StopAction("Stop ${Authors.faces.of(agent).name}") { handBack.stop(agentKey) }
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(null, DefaultActionGroup(stop), e.dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
        val avatar = e.inputEvent?.component
        if (avatar != null) popup.showUnderneathOf(avatar) else popup.showInBestPositionFor(e.dataContext)
    }

    private fun iconsFor(face: Face, oneShot: Boolean): PresenceIcons =
        icons?.takeIf { it.face == face && it.oneShot == oneShot } ?: PresenceIcons(face, oneShot).also { icons = it }
}

private class PresenceIcons(val face: Face, val oneShot: Boolean) {
    private val listening = PresenceIcon.listening(face, oneShot, UIUtil::getPanelBackground)
    private val working = PresenceIcon.working(face, oneShot, UIUtil::getPanelBackground)

    fun of(state: Presence.State): Icon = when (state) {
        Presence.State.LISTENING -> listening
        Presence.State.WORKING -> working
    }
}
