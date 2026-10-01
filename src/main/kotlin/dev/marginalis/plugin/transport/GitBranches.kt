package dev.marginalis.plugin.transport

import java.nio.file.Files
import java.nio.file.Path

// Reads .git/HEAD directly rather than via git4idea: keeps the plugin
// platform-only and works for roots the IDE hasn't mapped to a VCS.
object GitBranches {
    fun of(root: Path): String? {
        var dir: Path? = root
        while (dir != null) {
            val dotGit = dir.resolve(".git")
            when {
                // Linked worktrees have a .git FILE: "gitdir: <main>/.git/worktrees/<name>".
                Files.isRegularFile(dotGit) -> {
                    val gitdir = Files.readString(dotGit).trim().removePrefix("gitdir:").trim()
                    return readHead(dir.resolve(gitdir).normalize())
                }
                Files.isDirectory(dotGit) -> return readHead(dotGit)
            }
            dir = dir.parent
        }
        return null
    }

    private fun readHead(gitDir: Path): String? {
        val head = gitDir.resolve("HEAD")
        if (!Files.isRegularFile(head)) return null
        val text = Files.readString(head).trim()
        return if (text.startsWith("ref: refs/heads/")) {
            text.removePrefix("ref: refs/heads/")
        } else {
            text.take(8)
        }
    }
}
