package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthorPaletteTest {

    private val size = 6

    @Test
    fun `the anonymous agent keeps the first palette colour`() {
        assertEquals(0, FaceKey.Agent(Author.Agent.ANONYMOUS_NAME).paletteIndex(size))
    }

    @Test
    fun `an agent's colour is the same index it had before the move`() {
        listOf("claude-main", "claude-builder", "Codex", "x").forEach { key ->
            assertEquals(Math.floorMod(key.hashCode(), size), FaceKey.Agent(key).paletteIndex(size), key)
        }
    }

    @Test
    fun `a relayed person's colour depends on their login regardless of case, as before`() {
        listOf("octocat", "MFarag", "dependabot[bot]").forEach { login ->
            val before = Math.floorMod(login.lowercase().hashCode(), size)
            assertEquals(before, FaceKey.GitHub(login).paletteIndex(size), login)
            assertEquals(before, FaceKey.GitHub(login.uppercase()).paletteIndex(size), login)
        }
    }

    @Test
    fun `the user has no palette index and paints in the user colour`() {
        assertNull(FaceKey.User.paletteIndex(size))
    }
}
