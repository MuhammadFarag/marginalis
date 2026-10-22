package dev.marginalis.plugin.ui.tab

import com.intellij.openapi.vfs.VirtualFileManager

class OpeningTheTabTest : MarginTabTestCase() {

    fun testAReloadedPluginBringsThePinnedTabBackBehindTheFileYouAreReading() {
        sample()
        tab.pin()
        tab.openFileInFront(SAMPLE)

        tab.reloadPlugin()

        assertTrue(tab.isOpen)
        assertTrue(tab.isPinned)
        assertFalse(tab.isInFront)
        assertEquals("Sample.kt", tab.editors.selectedFiles.single().name)
        assertEquals(listOf("Margin", "Sample.kt"), tab.openTabs())
    }

    fun testAReloadedPluginBringsAnUnpinnedTabBackUnpinned() {
        tab.reloadPlugin()

        assertTrue(tab.isOpen)
        assertFalse(tab.isPinned)
        assertTrue(tab.isInFront)
    }

    fun testUpdatingThePluginWhileTheTabIsClosedTakesThreadPanelsAndTheTabOutOfTheIde() {
        sample()
        val thread = agent.addOnLine(5, "body" to "Still open in the editor")
        user.open(thread)
        tab.close()
        var tabAddressWhileUnloading: Any? = Unit

        val failure = tab.unloadPluginForUpdate {
            tabAddressWhileUnloading = VirtualFileManager.getInstance().findFileByUrl("marginalis://margin")
        }

        assertNull(failure)
        assertEquals(emptyList<Any>(), user.panels())
        assertNull(tabAddressWhileUnloading)
    }

    fun testWhileThePluginIsUnloadedTheTabCannotBeRestoredByItsAddress() {
        var found: Any? = MarginFile
        tab.reloadPlugin { found = VirtualFileManager.getInstance().findFileByUrl("marginalis://margin") }

        assertNull(found)
        assertTrue(tab.isOpen)
    }

    fun testAClosedTabStaysClosedWhenTheProjectOpensAgain() {
        tab.close()

        tab.openOnStartup()

        assertFalse(tab.isOpen)
    }
}
