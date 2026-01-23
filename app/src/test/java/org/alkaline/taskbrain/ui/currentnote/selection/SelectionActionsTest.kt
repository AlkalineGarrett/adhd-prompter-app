package org.alkaline.taskbrain.ui.currentnote.selection

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SelectionActionsTest {

    // ==================== getSelectedText ====================

    @Test
    fun `getSelectedText returns selected portion`() {
        val value = TextFieldValue("Hello World", TextRange(0, 5))
        assertEquals("Hello", SelectionActions.getSelectedText(value))
    }

    @Test
    fun `getSelectedText returns empty for collapsed selection`() {
        val value = TextFieldValue("Hello World", TextRange(5, 5))
        assertEquals("", SelectionActions.getSelectedText(value))
    }

    // ==================== copy ====================

    @Test
    fun `copy returns selected text`() {
        val value = TextFieldValue("Hello World", TextRange(6, 11))
        assertEquals("World", SelectionActions.copy(value))
    }

    // ==================== cut ====================

    @Test
    fun `cut removes selected text and returns it`() {
        val value = TextFieldValue("Hello World", TextRange(0, 6))
        val result = SelectionActions.cut(value)
        assertEquals("World", result.newValue.text)
        assertEquals("Hello ", result.copiedText)
        assertEquals(0, result.newValue.selection.start)
    }

    @Test
    fun `cut places cursor at selection start`() {
        val value = TextFieldValue("Hello World", TextRange(6, 11))
        val result = SelectionActions.cut(value)
        assertEquals("Hello ", result.newValue.text)
        assertEquals(6, result.newValue.selection.start)
    }

    // ==================== delete ====================

    @Test
    fun `delete removes selected text`() {
        val value = TextFieldValue("Hello World", TextRange(5, 11))
        val result = SelectionActions.delete(value)
        assertEquals("Hello", result.text)
        assertEquals(5, result.selection.start)
    }

    @Test
    fun `delete places cursor at selection start`() {
        val value = TextFieldValue("Hello World", TextRange(0, 6))
        val result = SelectionActions.delete(value)
        assertEquals("World", result.text)
        assertEquals(0, result.selection.start)
    }

    // ==================== selectAll ====================

    @Test
    fun `selectAll selects entire text`() {
        val value = TextFieldValue("Hello World", TextRange(5, 5))
        val result = SelectionActions.selectAll(value)
        assertEquals(0, result.selection.start)
        assertEquals(11, result.selection.end)
    }

    @Test
    fun `selectAll preserves text`() {
        val value = TextFieldValue("Hello World", TextRange(0, 0))
        val result = SelectionActions.selectAll(value)
        assertEquals("Hello World", result.text)
    }

    // ==================== unselect ====================

    @Test
    fun `unselect collapses selection to end`() {
        val value = TextFieldValue("Hello World", TextRange(0, 5))
        val result = SelectionActions.unselect(value)
        assertEquals(5, result.selection.start)
        assertEquals(5, result.selection.end)
    }

    @Test
    fun `unselect preserves text`() {
        val value = TextFieldValue("Hello World", TextRange(0, 5))
        val result = SelectionActions.unselect(value)
        assertEquals("Hello World", result.text)
    }

    // ==================== insertText ====================

    @Test
    fun `insertText inserts at cursor position`() {
        val value = TextFieldValue("Hello World", TextRange(5, 5))
        val result = SelectionActions.insertText(value, " Beautiful")
        assertEquals("Hello Beautiful World", result.text)
        assertEquals(15, result.selection.start) // After inserted text
    }

    @Test
    fun `insertText replaces selection`() {
        val value = TextFieldValue("Hello World", TextRange(6, 11))
        val result = SelectionActions.insertText(value, "Universe")
        assertEquals("Hello Universe", result.text)
        assertEquals(14, result.selection.start)
    }

    @Test
    fun `insertText at beginning`() {
        val value = TextFieldValue("World", TextRange(0, 0))
        val result = SelectionActions.insertText(value, "Hello ")
        assertEquals("Hello World", result.text)
        assertEquals(6, result.selection.start)
    }

    @Test
    fun `insertText at end`() {
        val value = TextFieldValue("Hello", TextRange(5, 5))
        val result = SelectionActions.insertText(value, " World")
        assertEquals("Hello World", result.text)
        assertEquals(11, result.selection.start)
    }
}
