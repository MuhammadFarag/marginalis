package dev.marginalis.plugin.ui.tab

import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.testFramework.LightVirtualFile

// VirtualFileWithoutContent: editor restore skips preloading content, and no
// Document is ever created for it.
object MarginFile : LightVirtualFile("Margin"), VirtualFileWithoutContent {
    const val PATH = "margin"

    init {
        isWritable = false
    }

    override fun getFileSystem(): VirtualFileSystem = detachedFileSystem

    override fun getPath(): String = PATH
}

private val detachedFileSystem = MarginFileSystem()
