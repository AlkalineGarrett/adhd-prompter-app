package org.alkaline.taskbrain.ui.currentnote

import org.alkaline.taskbrain.ui.currentnote.util.LinePrefixes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for backspace and delete-forward behavior at line boundaries.
 */
class BackspaceDeleteTest {

    private val BULLET = LinePrefixes.BULLET           // "• "
    private val CHECKBOX = LinePrefixes.CHECKBOX_UNCHECKED // "☐ "

    private fun controllerWithLines(vararg texts: String): Pair<EditorController, EditorState> {
        val state = EditorState()
        state.lines.clear()
        texts.forEach { state.lines.add(LineState(it)) }
        state.focusedLineIndex = 0
        return EditorController(state) to state
    }

    // ==================== deleteBackward at beginning of line ====================

    @Test
    fun `backspace merges into previous line with content, stripping current prefix`() {
        val (ctrl, state) = controllerWithLines("First", "\t${BULLET}Second")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("\t${BULLET}Second", 0)

        ctrl.deleteBackward(1)

        assertEquals(1, state.lines.size)
        assertEquals("FirstSecond", state.lines[0].text)
        assertEquals(0, state.focusedLineIndex)
    }

    @Test
    fun `backspace places cursor at join point`() {
        val (ctrl, state) = controllerWithLines("Hello", "World")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("World", 0)

        ctrl.deleteBackward(1)

        assertEquals("HelloWorld", state.lines[0].text)
        assertEquals(5, state.lines[0].cursorPosition) // after "Hello"
    }

    @Test
    fun `backspace deletes empty previous line when current has content`() {
        val (ctrl, state) = controllerWithLines("", "\t${BULLET}Content")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("\t${BULLET}Content", 0)

        ctrl.deleteBackward(1)

        assertEquals(1, state.lines.size)
        assertEquals("\t${BULLET}Content", state.lines[0].text) // keeps current with prefix
        assertEquals(0, state.focusedLineIndex)
    }

    @Test
    fun `backspace deletes prefixed empty previous line when current has content`() {
        val (ctrl, state) = controllerWithLines("\t${BULLET}", "\t${BULLET}Content")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("\t${BULLET}Content", 0)

        ctrl.deleteBackward(1)

        assertEquals(1, state.lines.size)
        assertEquals("\t${BULLET}Content", state.lines[0].text)
        assertEquals(0, state.focusedLineIndex)
    }

    @Test
    fun `backspace deletes current when neither has content`() {
        val (ctrl, state) = controllerWithLines("\t${BULLET}", "\t${CHECKBOX}")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("\t${CHECKBOX}", 0)

        ctrl.deleteBackward(1)

        assertEquals(1, state.lines.size)
        assertEquals("\t${BULLET}", state.lines[0].text) // keeps previous's prefix
        assertEquals(0, state.focusedLineIndex)
    }

    @Test
    fun `backspace deletes current when both empty no prefix`() {
        val (ctrl, state) = controllerWithLines("", "")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("", 0)

        ctrl.deleteBackward(1)

        assertEquals(1, state.lines.size)
        assertEquals("", state.lines[0].text)
        assertEquals(0, state.focusedLineIndex)
    }

    @Test
    fun `backspace does nothing on first line`() {
        val (ctrl, state) = controllerWithLines("\t${BULLET}Hello")
        state.focusedLineIndex = 0
        state.lines[0].updateFull("\t${BULLET}Hello", 0)

        ctrl.deleteBackward(0)

        assertEquals(1, state.lines.size)
        assertEquals("\t${BULLET}Hello", state.lines[0].text)
    }

    @Test
    fun `backspace merges noteIds when deleting empty previous`() {
        val (ctrl, state) = controllerWithLines("", "Content")
        state.lines[0].noteIds = listOf("noteA")
        state.lines[1].noteIds = listOf("noteB")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("Content", 0)

        ctrl.deleteBackward(1)

        // Current line (with content) kept; noteIds merged with current first
        assertEquals(listOf("noteB", "noteA"), state.lines[0].noteIds)
    }

    @Test
    fun `backspace merges noteIds when deleting current empty`() {
        val (ctrl, state) = controllerWithLines("\t${BULLET}", "")
        state.lines[0].noteIds = listOf("noteA")
        state.lines[1].noteIds = listOf("noteB")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("", 0)

        ctrl.deleteBackward(1)

        // Previous line kept
        assertEquals(listOf("noteA", "noteB"), state.lines[0].noteIds)
    }

    @Test
    fun `backspace skips hidden lines to find merge target`() {
        val (ctrl, state) = controllerWithLines("First", "Hidden", "Third")
        ctrl.hiddenIndices = setOf(1)
        state.focusedLineIndex = 2
        state.lines[2].updateFull("Third", 0)

        ctrl.deleteBackward(2)

        assertEquals(2, state.lines.size)
        assertEquals("FirstThird", state.lines[0].text)
        assertEquals(5, state.lines[0].cursorPosition)
        assertEquals("Hidden", state.lines[1].text) // untouched
    }

    @Test
    fun `backspace does nothing when all previous lines are hidden`() {
        val (ctrl, state) = controllerWithLines("Hidden", "Current")
        ctrl.hiddenIndices = setOf(0)
        state.focusedLineIndex = 1
        state.lines[1].updateFull("Current", 0)

        ctrl.deleteBackward(1)

        assertEquals(2, state.lines.size) // nothing changed
    }

    @Test
    fun `backspace preserves previous prefix and strips current prefix`() {
        val (ctrl, state) = controllerWithLines("\t${BULLET}First", "\t${CHECKBOX}Second")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("\t${CHECKBOX}Second", 0)

        ctrl.deleteBackward(1)

        assertEquals(1, state.lines.size)
        assertEquals("\t${BULLET}FirstSecond", state.lines[0].text)
    }

    @Test
    fun `backspace merges noteIds when both have content`() {
        val (ctrl, state) = controllerWithLines("Previous", "Current")
        state.lines[0].noteIds = listOf("noteA")
        state.lines[1].noteIds = listOf("noteB")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("Current", 0)

        ctrl.deleteBackward(1)

        // mergeToPreviousLine: mergeNoteIds(previous, current)
        assertEquals(listOf("noteA", "noteB"), state.lines[0].noteIds)
    }

    @Test
    fun `backspace preserves remaining lines after merge`() {
        val (ctrl, state) = controllerWithLines("First", "Second", "Third")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("Second", 0)

        ctrl.deleteBackward(1)

        assertEquals(2, state.lines.size)
        assertEquals("FirstSecond", state.lines[0].text)
        assertEquals("Third", state.lines[1].text)
    }

    @Test
    fun `backspace keeps cursor position of surviving line when deleting empty previous`() {
        val (ctrl, state) = controllerWithLines("", "\t${BULLET}Content")
        state.focusedLineIndex = 1
        state.lines[1].updateFull("\t${BULLET}Content", 0)

        ctrl.deleteBackward(1)

        assertEquals("\t${BULLET}Content", state.lines[0].text)
        assertEquals(0, state.lines[0].cursorPosition)
    }

    // ==================== deleteBackward mid-line ====================

    @Test
    fun `backspace deletes character before cursor normally`() {
        val (ctrl, state) = controllerWithLines("Hello")
        state.focusedLineIndex = 0
        state.lines[0].updateFull("Hello", 3) // after "Hel"

        ctrl.deleteBackward(0)

        assertEquals("Helo", state.lines[0].text)
        assertEquals(2, state.lines[0].cursorPosition)
    }

    // ==================== deleteForward (mergeNextLine) ====================

    @Test
    fun `delete forward strips prefix of next line when merging`() {
        val (ctrl, state) = controllerWithLines("First", "\t${BULLET}Second")
        state.focusedLineIndex = 0
        state.lines[0].updateFull("First", 5) // cursor at end

        ctrl.deleteForward(0)

        assertEquals(1, state.lines.size)
        assertEquals("FirstSecond", state.lines[0].text)
    }

    @Test
    fun `delete forward removes empty next line`() {
        val (ctrl, state) = controllerWithLines("First", "")
        state.focusedLineIndex = 0
        state.lines[0].updateFull("First", 5)

        ctrl.deleteForward(0)

        assertEquals(1, state.lines.size)
        assertEquals("First", state.lines[0].text)
    }

    @Test
    fun `delete forward removes next line that has only prefix`() {
        val (ctrl, state) = controllerWithLines("First", "\t${BULLET}")
        state.focusedLineIndex = 0
        state.lines[0].updateFull("First", 5)

        ctrl.deleteForward(0)

        assertEquals(1, state.lines.size)
        assertEquals("First", state.lines[0].text)
    }

    @Test
    fun `delete forward preserves cursor position`() {
        val (ctrl, state) = controllerWithLines("Hello", "World")
        state.focusedLineIndex = 0
        state.lines[0].updateFull("Hello", 5)

        ctrl.deleteForward(0)

        assertEquals(5, state.lines[0].cursorPosition)
    }

    @Test
    fun `delete forward merges noteIds`() {
        val (ctrl, state) = controllerWithLines("LongerFirst", "Second")
        state.lines[0].noteIds = listOf("noteA")
        state.lines[1].noteIds = listOf("noteB")
        state.focusedLineIndex = 0
        state.lines[0].updateFull("LongerFirst", "LongerFirst".length)

        ctrl.deleteForward(0)

        // currentLine has more content, so its noteIds come first
        assertEquals(listOf("noteA", "noteB"), state.lines[0].noteIds)
    }

    @Test
    fun `delete forward strips prefix when both lines have prefixes`() {
        val (ctrl, state) = controllerWithLines("\t${BULLET}First", "\t${CHECKBOX}Second")
        state.focusedLineIndex = 0
        state.lines[0].updateFull("\t${BULLET}First", "\t${BULLET}First".length)

        ctrl.deleteForward(0)

        assertEquals(1, state.lines.size)
        assertEquals("\t${BULLET}FirstSecond", state.lines[0].text)
    }

    @Test
    fun `delete forward skips hidden lines to find merge target`() {
        val (ctrl, state) = controllerWithLines("First", "Hidden", "Third")
        ctrl.hiddenIndices = setOf(1)
        state.focusedLineIndex = 0
        state.lines[0].updateFull("First", 5)

        ctrl.deleteForward(0)

        assertEquals(2, state.lines.size)
        assertEquals("FirstThird", state.lines[0].text)
        assertEquals("Hidden", state.lines[1].text)
    }

    @Test
    fun `delete forward preserves remaining lines`() {
        val (ctrl, state) = controllerWithLines("First", "Second", "Third")
        state.focusedLineIndex = 0
        state.lines[0].updateFull("First", 5)

        ctrl.deleteForward(0)

        assertEquals(2, state.lines.size)
        assertEquals("FirstSecond", state.lines[0].text)
        assertEquals("Third", state.lines[1].text)
    }
}
