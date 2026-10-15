package dev.marginalis.plugin.ui

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import dev.marginalis.plugin.testing.MarginalisTestCase
import com.intellij.util.ui.UIUtil
import dev.marginalis.core.CommentThread

class WalkthroughNavigatorTest : MarginalisTestCase() {

    private val sample = (1..12).joinToString("\n") { "val line$it = $it" }

    override fun setUp() {
        super.setUp()
        myFixture.configureByText("Sample.kt", sample)
    }

    private fun step(line: Int?, file: String = "Sample.kt") =
        CommentThread(file, line, anchorText = line?.let { "val line${it + 1} = ${it + 1}" })

    private val caretLine get() = myFixture.editor.caretModel.logicalPosition.line

    fun testStepInTheSameFileMovesTheCaretToIt() {
        assertTrue(WalkthroughNavigator.navigateWithin(myFixture.editor, project, step(7)))
        assertEquals(7, caretLine)
    }

    fun testStepInAnotherFileIsDeclined() {
        myFixture.addFileToProject("Other.kt", "val other = 1\nval more = 2\n")
        assertFalse(WalkthroughNavigator.navigateWithin(myFixture.editor, project, step(1, file = "Other.kt")))
        assertEquals(0, caretLine)
    }

    fun testReleasedEditorIsDeclined() {
        val editor = EditorFactory.getInstance().createEditor(myFixture.editor.document, project)
        EditorFactory.getInstance().releaseEditor(editor)
        assertFalse(WalkthroughNavigator.navigateWithin(editor, project, step(7)))
    }

    fun testStepInsideFoldedCodeIsUnfolded() {
        val document = myFixture.editor.document
        val folding = myFixture.editor.foldingModel
        folding.runBatchFoldingOperation {
            folding.addFoldRegion(document.getLineStartOffset(3), document.getLineEndOffset(9), "...")!!.isExpanded = false
        }

        WalkthroughNavigator.navigateWithin(myFixture.editor, project, step(6))

        assertFalse(folding.isOffsetCollapsed(myFixture.editor.caretModel.offset))
    }

    fun testBackReturnsToThePreviousStep() {
        WalkthroughNavigator.navigateWithin(myFixture.editor, project, step(2))
        WalkthroughNavigator.navigateWithin(myFixture.editor, project, step(9))

        IdeDocumentHistory.getInstance(project).back()

        assertEquals(2, caretLine)
    }

    fun testFileLevelThreadStaysInTheEditor() {
        assertTrue(WalkthroughNavigator.navigateWithin(myFixture.editor, project, step(null)))
        assertEquals(0, caretLine)
    }

    fun testStepOpensItsThreadInTheSameEditor() {
        val target = step(7)

        WalkthroughNavigator.navigateWithin(myFixture.editor, project, target)

        val panel = UIUtil.findComponentOfType(myFixture.editor.contentComponent, ThreadPanel::class.java)
        assertNotNull(panel)
    }
}
