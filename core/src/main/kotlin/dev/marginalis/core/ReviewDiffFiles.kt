package dev.marginalis.core

object ReviewDiffFiles {

    fun switchTarget(stepFile: String, projectRoot: String, diffFiles: List<String?>, shownIndex: Int): Int? {
        val index = diffFiles.indexOf("$projectRoot/$stepFile")
        return index.takeIf { it >= 0 && it != shownIndex }
    }
}
