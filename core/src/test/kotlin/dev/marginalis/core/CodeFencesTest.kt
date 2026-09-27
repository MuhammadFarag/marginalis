package dev.marginalis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeFencesTest {

    private fun String.code(fence: CodeFence) = substring(fence.codeStart, fence.codeEnd)

    @Test
    fun `a closed fence reports its language and exactly its code`() {
        val text = "Look:\n```kotlin\nval x = 1\nval y = 2\n```\nDone."
        val fence = CodeFences.find(text).single()
        assertEquals("kotlin", fence.language)
        assertEquals("val x = 1\nval y = 2", text.code(fence))
        assertTrue(fence.closed)
        assertEquals("```kotlin\nval x = 1\nval y = 2\n```", text.substring(fence.start, fence.end))
    }

    @Test
    fun `a fence still being typed runs to the end of the text`() {
        val text = "```python\ndef f():\n    ret"
        val fence = CodeFences.find(text).single()
        assertEquals("python", fence.language)
        assertEquals("def f():\n    ret", text.code(fence))
        assertEquals(false, fence.closed)
    }

    @Test
    fun `a fence opened a moment ago has a language and no code yet`() {
        val fence = CodeFences.find("```kotlin").single()
        assertEquals("kotlin", fence.language)
        assertEquals(fence.codeStart, fence.codeEnd)
    }

    @Test
    fun `language tags resolve to file extensions — aliases mapped, the rest lowercased`() {
        assertEquals("kt", CodeFences.extensionFor("kotlin"))
        assertEquals("sh", CodeFences.extensionFor("Bash"))
        assertEquals("py", CodeFences.extensionFor("python"))
        assertEquals("go", CodeFences.extensionFor("Go"))
    }

    @Test
    fun `an untagged fence has no language`() {
        assertNull(CodeFences.find("```\nplain\n```").single().language)
    }

    @Test
    fun `several fences come back in order, and prose between them is nobody's code`() {
        val text = "```js\na()\n```\nbetween\n```sql\nselect 1\n```"
        val fences = CodeFences.find(text)
        assertEquals(listOf("js", "sql"), fences.map { it.language })
        assertEquals(listOf("a()", "select 1"), fences.map { text.code(it) })
    }

    @Test
    fun `backticks in the middle of a line open nothing`() {
        assertTrue(CodeFences.find("wrap it in ```kotlin fences\nplease").isEmpty())
    }

    @Test
    fun `an empty closed fence has no code`() {
        val fence = CodeFences.find("```kotlin\n```").single()
        assertEquals(fence.codeStart, fence.codeEnd)
        assertTrue(fence.closed)
    }

    @Test
    fun `a newline after the opening line is not yet code`() {
        val text = "```kotlin\n"
        val fence = CodeFences.find(text).single()
        assertEquals(text.length, fence.codeStart)
        assertEquals(text.length, fence.codeEnd)
    }

    @Test
    fun `a tagged fence line inside an open fence is code, not a close`() {
        val text = "```markdown\n```kotlin\nx\n```"
        val fence = CodeFences.find(text).single()
        assertEquals("```kotlin\nx", text.code(fence))
    }

    @Test
    fun `a closing fence may trail spaces, and the fence ends before the prose that follows`() {
        val text = "```\na\n```  \nafter"
        val fence = CodeFences.find(text).single()
        assertEquals("```\na\n```  ", text.substring(fence.start, fence.end))
    }

    @Test
    fun `blank lines at the end of the code belong to the code`() {
        val text = "```\na\n\n\n```"
        assertEquals("a\n\n", text.code(CodeFences.find(text).single()))
    }
}
