package dev.marginalis.plugin

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.ProjectManager
import dev.marginalis.plugin.store.MarginalisPersistence
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.ThreadInlayManager

/**
 * Hot reload's other half (MarginalisStartup being the load half): before a
 * dynamic unload, remove every trace of this plugin's classes from platform
 * structures that outlive the classloader. Two of them exist:
 *
 *  - gutter highlighters live on the persistent document markup model, each
 *    holding a ThreadGutterIconRenderer;
 *  - thread panels live as inlays and user data on open editors.
 *
 * Anything left behind pins the unloaded classloader and the IDE falls back
 * to demanding a restart. Threads themselves are persisted (belt and braces
 * — every change already saves), and the startup activity re-runs on the
 * next dynamic load, rehydrating threads and re-attaching markers.
 */
class MarginalisUnloadListener : DynamicPluginListener {

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId.idString != "dev.marginalis.plugin") return

        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val store = MarginalisStore.getInstance(project)
            store.syncLines()
            MarginalisPersistence.save(project, store.threads.all())
            for (thread in store.threads.all()) {
                store.removeMarker(thread)?.let { marker ->
                    if (marker.isValid) {
                        DocumentMarkupModel.forDocument(marker.document, project, false)
                            ?.removeHighlighter(marker)
                    }
                }
            }
        }
        ThreadInlayManager.disposeAll()
    }
}
