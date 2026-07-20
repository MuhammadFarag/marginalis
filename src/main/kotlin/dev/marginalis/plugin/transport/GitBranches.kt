package dev.marginalis.plugin.transport

import java.nio.file.Files
import java.nio.file.Path

/**
 * Current git branch of a directory, read straight from `.git/HEAD` — no
 * git4idea dependency, so the plugin stays platform-only and the answer is
 * available even for VCS setups the IDE hasn't mapped.
 *
 * The branch is what disambiguates same-layout worktrees (the one case
 * where project name and file layout are identical by construction), so
 * ping and resolution errors carry it.
 */
object GitBranches {

    /** Branch name of the checkout containing [root]; short SHA when detached; null when not git. */
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
            text.take(8) // detached HEAD: enough SHA to identify
        }
    }
}
