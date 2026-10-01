package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeLinkTest {

    private fun parsed(target: String): CodeLink = assertIs<Parsed.Ok<CodeLink>>(CodeLink.parse(target), target).value

    @Test
    fun `a bare path names the file and no lines`() {
        val link = parsed("src/Clabo.kt")

        assertEquals("src/Clabo.kt", link.path)
        assertNull(link.lines)
    }

    @Test
    fun `a GitHub line anchor names one line`() {
        val link = parsed("src/Clabo.kt#L42")

        assertEquals("src/Clabo.kt", link.path)
        assertEquals(42..42, link.lines)
    }

    @Test
    fun `a GitHub range anchor names the lines from first to last`() {
        assertEquals(42..50, parsed("src/Clabo.kt#L42-L50").lines)
        assertEquals(7..7, parsed("src/Clabo.kt#L7-L7").lines)
    }

    @Test
    fun `the percent-encoding markdown puts on a link target is undone`() {
        assertEquals("src/my file.kt", parsed("src/my%20file.kt").path)
        assertEquals("src/my file ü.kt", parsed("src/my%20file%20%C3%BC.kt#L3").path)
    }

    @Test
    fun `characters markdown passes through raw stay as written`() {
        val route = parsed("app/[id]/page.tsx#L3")
        assertEquals("app/[id]/page.tsx", route.path)
        assertEquals(3..3, route.lines)
        assertEquals("c++/x.cc", parsed("c++/x.cc").path)
        assertEquals("src/🙂.kt", parsed("src/🙂.kt").path)
        assertEquals("src/{a|b}\"<x>.kt", parsed("src/{a|b}\"<x>.kt").path)
    }

    @Test
    fun `a percent sign that starts no escape is literal`() {
        assertEquals("src/100%.kt", parsed("src/100%.kt").path)
        assertEquals("src/%zz.kt", parsed("src/%zz.kt").path)
    }

    @Test
    fun `an unencoded link reads back as it is written`() {
        for (target in listOf("src/Clabo.kt", "src/Clabo.kt#L42", "src/Clabo.kt#L42-L50")) {
            assertEquals(target, parsed(target).toString())
        }
    }

    @Test
    fun `anything else is a teaching refusal that shows the format`() {
        val refused = listOf(
            "",
            "src/a.kt#",
            "src/a.kt#42",
            "src/a.kt#L0",
            "src/a.kt#L50-L42",
            "src/a.kt#L4-50",
            "src/a.kt#section",
            "#L4",
        )
        for (target in refused) {
            val refusal = assertIs<Parsed.Invalid>(CodeLink.parse(target), target)
            assertTrue("src/Main.kt#L42" in refusal.reason, refusal.reason)
        }
    }

    @Test
    fun `a target that could reach outside the project is refused for what it is`() {
        val refusedFor = mapOf(
            "/etc/passwd" to "the path is absolute",
            "%2Fetc%2Fpasswd" to "the path is absolute",
            "../outside.kt" to "climbs out of the project",
            "src/../../outside.kt" to "climbs out of the project",
            "src/%2e%2e/%2E%2E/x.kt" to "climbs out of the project",
            "..%5C..%5Cx.kt" to "uses '\\'",
            "src\\a.kt" to "uses '\\'",
            "https://example.com/a.kt" to "a scheme or drive",
            "file:/src/a.kt" to "a scheme or drive",
            "mg:3d4770ad" to "a scheme or drive",
            "C:/Windows/win.ini" to "a scheme or drive",
            "C%3A/x.kt" to "a scheme or drive",
            "src/a%00.kt" to "control character",
            "src/a%1F.kt" to "control character",
            "src/a%7F.kt" to "control character",
            "src/a.kt?plain=1#L4" to "'?' query",
        )
        for ((target, problem) in refusedFor) {
            val refusal = assertIs<Parsed.Invalid>(CodeLink.parse(target), target)
            assertTrue(problem in refusal.reason, "$target: ${refusal.reason}")
        }
    }
}
