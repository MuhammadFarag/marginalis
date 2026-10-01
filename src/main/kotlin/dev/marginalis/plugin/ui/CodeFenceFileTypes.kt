package dev.marginalis.plugin.ui

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import dev.marginalis.core.CodeFences

object CodeFenceFileTypes {

    fun of(language: String?): FileType {
        if (language.isNullOrBlank()) return PlainTextFileType.INSTANCE
        val byName = FileTypeManager.getInstance().getFileTypeByFileName("snippet.${CodeFences.extensionFor(language)}")
        return if (byName is UnknownFileType) PlainTextFileType.INSTANCE else byName
    }
}
