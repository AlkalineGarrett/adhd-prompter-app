package org.alkaline.taskbrain.ui.currentnote.selection

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineSelectionUtilsTest {

    // ==================== isSpaceReplacingSelection ====================

    @Test
    fun `isSpaceReplacingSelection returns true when space replaces selected text`() {
        val oldValue = TextFieldValue("Hello World", TextRange(6, 11)) // "World" selected
        val newValue = TextFieldValue("Hello  ", TextRange(7)) // "Hello " + " " = "Hello  "
        assertTrue(isSpaceReplacingSelection(oldValue, newValue))
    }

    @Test
    fun `isSpaceReplacingSelection returns false when selection is collapsed`() {
        val oldValue = TextFieldValue("Hello World", TextRange(5))
        val newValue = TextFieldValue("Hello  World", TextRange(6))
        assertFalse(isSpaceReplacingSelection(oldValue, newValue))
    }

    @Test
    fun `isSpaceReplacingSelection returns false when selection replaced with other text`() {
        val oldValue = TextFieldValue("Hello World", TextRange(6, 11)) // "World" selected
        val newValue = TextFieldValue("Hello there", TextRange(11)) // replaced with "there"
        assertFalse(isSpaceReplacingSelection(oldValue, newValue))
    }

    @Test
    fun `isSpaceReplacingSelection returns true when entire text selected and replaced with space`() {
        val oldValue = TextFieldValue("Hello", TextRange(0, 5))
        val newValue = TextFieldValue(" ", TextRange(1))
        assertTrue(isSpaceReplacingSelection(oldValue, newValue))
    }

    @Test
    fun `isSpaceReplacingSelection returns false when selection deleted without space`() {
        val oldValue = TextFieldValue("Hello World", TextRange(6, 11))
        val newValue = TextFieldValue("Hello ", TextRange(5)) // wrong cursor position
        assertFalse(isSpaceReplacingSelection(oldValue, newValue))
    }

    // ==================== handleSelectionIndent ====================

    @Test
    fun `handleSelectionIndent adds tab to single selected line`() {
        val oldValue = TextFieldValue("Hello", TextRange(0, 5))
        val result = handleSelectionIndent(oldValue)
        assertEquals("\tHello", result.text)
        assertEquals(TextRange(0, 6), result.selection)
    }

    @Test
    fun `handleSelectionIndent adds tab to multiple selected lines`() {
        val oldValue = TextFieldValue("Line1\nLine2\nLine3", TextRange(0, 17))
        val result = handleSelectionIndent(oldValue)
        assertEquals("\tLine1\n\tLine2\n\tLine3", result.text)
        assertEquals(TextRange(0, 20), result.selection)
    }

    @Test
    fun `handleSelectionIndent indents current line when selection is collapsed`() {
        val oldValue = TextFieldValue("Hello", TextRange(3))
        val result = handleSelectionIndent(oldValue)
        assertEquals("\tHello", result.text)
        assertEquals(4, result.selection.start) // cursor moved by 1
    }

    @Test
    fun `handleSelectionIndent indents partial line selection`() {
        val oldValue = TextFieldValue("Hello World", TextRange(3, 8)) // "lo Wo" selected
        val result = handleSelectionIndent(oldValue)
        assertEquals("\tHello World", result.text)
        // Selection should cover the indented line
        assertEquals(TextRange(0, 12), result.selection)
    }

    @Test
    fun `handleSelectionIndent indents lines in middle of text`() {
        val oldValue = TextFieldValue("Line1\nLine2\nLine3\nLine4", TextRange(6, 17))
        val result = handleSelectionIndent(oldValue)
        assertEquals("Line1\n\tLine2\n\tLine3\nLine4", result.text)
    }

    @Test
    fun `handleSelectionIndent preserves existing tabs`() {
        val oldValue = TextFieldValue("\tLine1\n\tLine2", TextRange(0, 13))
        val result = handleSelectionIndent(oldValue)
        assertEquals("\t\tLine1\n\t\tLine2", result.text)
    }

    @Test
    fun `handleSelectionIndent works with bullet lines`() {
        val oldValue = TextFieldValue("• Item1\n• Item2", TextRange(0, 15))
        val result = handleSelectionIndent(oldValue)
        assertEquals("\t• Item1\n\t• Item2", result.text)
    }

    // ==================== handleSelectionUnindent ====================

    @Test
    fun `handleSelectionUnindent removes tab from single selected line`() {
        val oldValue = TextFieldValue("\tHello", TextRange(0, 6))
        val result = handleSelectionUnindent(oldValue)
        assertNotNull(result)
        assertEquals("Hello", result!!.text)
        assertEquals(TextRange(0, 5), result.selection)
    }

    @Test
    fun `handleSelectionUnindent removes tab from multiple selected lines`() {
        val oldValue = TextFieldValue("\tLine1\n\tLine2\n\tLine3", TextRange(0, 20))
        val result = handleSelectionUnindent(oldValue)
        assertNotNull(result)
        assertEquals("Line1\nLine2\nLine3", result!!.text)
        assertEquals(TextRange(0, 17), result.selection)
    }

    @Test
    fun `handleSelectionUnindent unindents current line when selection is collapsed`() {
        val oldValue = TextFieldValue("\tHello", TextRange(3))
        val result = handleSelectionUnindent(oldValue)
        assertNotNull(result)
        assertEquals("Hello", result!!.text)
        assertEquals(2, result.selection.start) // cursor moved back by 1
    }

    @Test
    fun `handleSelectionUnindent returns null when lines have no tabs`() {
        val oldValue = TextFieldValue("Hello\nWorld", TextRange(0, 11))
        val result = handleSelectionUnindent(oldValue)
        assertNull(result)
    }

    @Test
    fun `handleSelectionUnindent removes only one tab per line`() {
        val oldValue = TextFieldValue("\t\tLine1\n\t\tLine2", TextRange(0, 15))
        val result = handleSelectionUnindent(oldValue)
        assertNotNull(result)
        assertEquals("\tLine1\n\tLine2", result!!.text)
    }

    @Test
    fun `handleSelectionUnindent removes tab from lines that have it only`() {
        val oldValue = TextFieldValue("\tLine1\nLine2\n\tLine3", TextRange(0, 19))
        val result = handleSelectionUnindent(oldValue)
        assertNotNull(result)
        assertEquals("Line1\nLine2\nLine3", result!!.text)
    }

    @Test
    fun `handleSelectionUnindent works with bullet lines`() {
        val oldValue = TextFieldValue("\t• Item1\n\t• Item2", TextRange(0, 17))
        val result = handleSelectionUnindent(oldValue)
        assertNotNull(result)
        assertEquals("• Item1\n• Item2", result!!.text)
    }

    // ==================== handleFullLineDelete ====================

    @Test
    fun `handleFullLineDelete removes first line and its newline`() {
        val oldValue = TextFieldValue("Line1\nLine2", TextRange(0, 5)) // "Line1" selected
        val newValue = TextFieldValue("\nLine2", TextRange(0))
        val result = handleFullLineDelete(oldValue, newValue)
        assertNotNull(result)
        assertEquals("Line2", result!!.text)
        assertEquals(0, result.selection.start)
    }

    @Test
    fun `handleFullLineDelete removes middle line and its newline`() {
        val oldValue = TextFieldValue("Line1\nLine2\nLine3", TextRange(6, 11)) // "Line2" selected
        val newValue = TextFieldValue("Line1\n\nLine3", TextRange(6))
        val result = handleFullLineDelete(oldValue, newValue)
        assertNotNull(result)
        assertEquals("Line1\nLine3", result!!.text)
        assertEquals(6, result.selection.start)
    }

    @Test
    fun `handleFullLineDelete removes last line and its leading newline`() {
        val oldValue = TextFieldValue("Line1\nLine2", TextRange(6, 11)) // "Line2" selected
        val newValue = TextFieldValue("Line1\n", TextRange(6))
        val result = handleFullLineDelete(oldValue, newValue)
        assertNotNull(result)
        assertEquals("Line1", result!!.text)
        assertEquals(5, result.selection.start)
    }

    @Test
    fun `handleFullLineDelete returns null when selection is collapsed`() {
        val oldValue = TextFieldValue("Line1\nLine2", TextRange(3))
        val newValue = TextFieldValue("Lin1\nLine2", TextRange(3))
        val result = handleFullLineDelete(oldValue, newValue)
        assertNull(result)
    }

    @Test
    fun `handleFullLineDelete returns null when selection not at line start`() {
        val oldValue = TextFieldValue("Line1\nLine2", TextRange(2, 5)) // "ne1" selected, not at line start
        val newValue = TextFieldValue("Li\nLine2", TextRange(2))
        val result = handleFullLineDelete(oldValue, newValue)
        assertNull(result)
    }

    @Test
    fun `handleFullLineDelete returns null when selection not at line end`() {
        val oldValue = TextFieldValue("Line1\nLine2", TextRange(0, 3)) // "Lin" selected, not at line end
        val newValue = TextFieldValue("e1\nLine2", TextRange(0))
        val result = handleFullLineDelete(oldValue, newValue)
        assertNull(result)
    }

    @Test
    fun `handleFullLineDelete removes multiple selected lines`() {
        val oldValue = TextFieldValue("Line1\nLine2\nLine3\nLine4", TextRange(6, 17)) // "Line2\nLine3" selected
        val newValue = TextFieldValue("Line1\n\nLine4", TextRange(6))
        val result = handleFullLineDelete(oldValue, newValue)
        assertNotNull(result)
        assertEquals("Line1\nLine4", result!!.text)
        assertEquals(6, result.selection.start)
    }

    @Test
    fun `handleFullLineDelete returns null for only line in document`() {
        val oldValue = TextFieldValue("Hello", TextRange(0, 5))
        val newValue = TextFieldValue("", TextRange(0))
        val result = handleFullLineDelete(oldValue, newValue)
        assertNull(result)
    }

    @Test
    fun `handleFullLineDelete returns null when text replaced not deleted`() {
        val oldValue = TextFieldValue("Line1\nLine2", TextRange(0, 5))
        val newValue = TextFieldValue("New\nLine2", TextRange(3)) // replaced with "New"
        val result = handleFullLineDelete(oldValue, newValue)
        assertNull(result)
    }

    // ==================== getLineSelection ====================

    @Test
    fun `getLineSelection returns correct range for first line`() {
        val text = "Line1\nLine2\nLine3"
        val result = getLineSelection(text, 0)
        assertEquals(TextRange(0, 5), result)
    }

    @Test
    fun `getLineSelection returns correct range for middle line`() {
        val text = "Line1\nLine2\nLine3"
        val result = getLineSelection(text, 1)
        assertEquals(TextRange(6, 11), result)
    }

    @Test
    fun `getLineSelection returns correct range for last line`() {
        val text = "Line1\nLine2\nLine3"
        val result = getLineSelection(text, 2)
        assertEquals(TextRange(12, 17), result)
    }

    @Test
    fun `getLineSelection returns text length for invalid positive index`() {
        val text = "Line1\nLine2"
        val result = getLineSelection(text, 5)
        assertEquals(TextRange(text.length), result)
    }

    @Test
    fun `getLineSelection returns text length for negative index`() {
        val text = "Line1\nLine2"
        val result = getLineSelection(text, -1)
        assertEquals(TextRange(text.length), result)
    }

    @Test
    fun `getLineSelection works with single line text`() {
        val text = "Hello World"
        val result = getLineSelection(text, 0)
        assertEquals(TextRange(0, 11), result)
    }

    @Test
    fun `getLineSelection works with empty lines`() {
        val text = "Line1\n\nLine3"
        val result = getLineSelection(text, 1)
        assertEquals(TextRange(6, 6), result) // Empty line
    }

    // ==================== getMultiLineSelection ====================

    @Test
    fun `getMultiLineSelection returns correct range for lines 0 to 1`() {
        val text = "Line1\nLine2\nLine3"
        val result = getMultiLineSelection(text, 0, 1)
        // Includes the newline after Line2
        assertEquals(TextRange(0, 12), result)
    }

    @Test
    fun `getMultiLineSelection returns correct range for lines 1 to 2`() {
        val text = "Line1\nLine2\nLine3"
        val result = getMultiLineSelection(text, 1, 2)
        assertEquals(TextRange(6, 17), result)
    }

    @Test
    fun `getMultiLineSelection returns correct range for all lines`() {
        val text = "Line1\nLine2\nLine3"
        val result = getMultiLineSelection(text, 0, 2)
        assertEquals(TextRange(0, 17), result)
    }

    @Test
    fun `getMultiLineSelection returns full text range for invalid negative start`() {
        val text = "Line1\nLine2"
        val result = getMultiLineSelection(text, -1, 1)
        assertEquals(TextRange(0, text.length), result)
    }

    @Test
    fun `getMultiLineSelection returns full text range for invalid end index`() {
        val text = "Line1\nLine2"
        val result = getMultiLineSelection(text, 0, 5)
        assertEquals(TextRange(0, text.length), result)
    }

    @Test
    fun `getMultiLineSelection works for single line selection`() {
        val text = "Line1\nLine2\nLine3"
        val result = getMultiLineSelection(text, 1, 1)
        // Includes the newline after Line2
        assertEquals(TextRange(6, 12), result)
    }

    @Test
    fun `getMultiLineSelection includes empty lines`() {
        val text = "Line1\n\nLine3"
        val result = getMultiLineSelection(text, 0, 2)
        assertEquals(TextRange(0, 12), result)
    }

    // ==================== Double-space unindent behavior ====================
    // These tests verify the chained unindent logic used for double-space:
    // First unindent undoes the indent, second unindent actually unindents from original

    @Test
    fun `chained unindent on double-indented text removes two tabs`() {
        // Simulates: original had 1 tab, first space added another, double-space should remove both
        val afterFirstIndent = TextFieldValue("\t\tHello", TextRange(0, 7))

        // First unindent (undoes the indent we just did)
        val firstUnindent = handleSelectionUnindent(afterFirstIndent)
        assertNotNull(firstUnindent)
        assertEquals("\tHello", firstUnindent!!.text)

        // Second unindent (actually unindents from original)
        val secondUnindent = handleSelectionUnindent(firstUnindent)
        assertNotNull(secondUnindent)
        assertEquals("Hello", secondUnindent!!.text)
    }

    @Test
    fun `chained unindent on single-indented text removes one tab then returns null`() {
        // Simulates: original had no tabs, first space added one, double-space removes it
        val afterFirstIndent = TextFieldValue("\tHello", TextRange(0, 6))

        // First unindent (undoes the indent)
        val firstUnindent = handleSelectionUnindent(afterFirstIndent)
        assertNotNull(firstUnindent)
        assertEquals("Hello", firstUnindent!!.text)

        // Second unindent returns null (nothing to unindent)
        val secondUnindent = handleSelectionUnindent(firstUnindent)
        assertNull(secondUnindent)
    }

    @Test
    fun `chained unindent on non-indented text returns null immediately`() {
        // Simulates: text has no tabs, can't unindent
        val noIndent = TextFieldValue("Hello", TextRange(0, 5))

        val result = handleSelectionUnindent(noIndent)
        assertNull(result)
    }

    @Test
    fun `chained unindent on multi-line with mixed indentation`() {
        // Line1 has 2 tabs, Line2 has 1 tab
        val text = TextFieldValue("\t\tLine1\n\tLine2", TextRange(0, 14))

        // First unindent
        val firstUnindent = handleSelectionUnindent(text)
        assertNotNull(firstUnindent)
        assertEquals("\tLine1\nLine2", firstUnindent!!.text)

        // Second unindent - Line1 still has a tab, Line2 has none
        val secondUnindent = handleSelectionUnindent(firstUnindent)
        assertNotNull(secondUnindent)
        assertEquals("Line1\nLine2", secondUnindent!!.text)
    }

    @Test
    fun `chained unindent preserves selection across both operations`() {
        val text = TextFieldValue("\t\tLine1\n\t\tLine2", TextRange(0, 15))

        val firstUnindent = handleSelectionUnindent(text)
        assertNotNull(firstUnindent)
        assertEquals(TextRange(0, 13), firstUnindent!!.selection)

        val secondUnindent = handleSelectionUnindent(firstUnindent)
        assertNotNull(secondUnindent)
        assertEquals(TextRange(0, 11), secondUnindent!!.selection)
    }

    // ==================== Indent then unindent round-trip ====================

    @Test
    fun `indent followed by unindent returns to original text`() {
        val original = TextFieldValue("Hello\nWorld", TextRange(0, 11))

        val indented = handleSelectionIndent(original)
        assertEquals("\tHello\n\tWorld", indented.text)

        val unindented = handleSelectionUnindent(indented)
        assertNotNull(unindented)
        assertEquals("Hello\nWorld", unindented!!.text)
    }

    @Test
    fun `double indent followed by double unindent returns to original`() {
        val original = TextFieldValue("Hello", TextRange(0, 5))

        val indent1 = handleSelectionIndent(original)
        val indent2 = handleSelectionIndent(indent1)
        assertEquals("\t\tHello", indent2.text)

        val unindent1 = handleSelectionUnindent(indent2)
        val unindent2 = handleSelectionUnindent(unindent1!!)
        assertEquals("Hello", unindent2!!.text)
    }

    // ==================== Edge cases for indent/unindent ====================

    @Test
    fun `handleSelectionIndent works with empty line in selection`() {
        val text = TextFieldValue("Line1\n\nLine3", TextRange(0, 12))
        val result = handleSelectionIndent(text)
        assertEquals("\tLine1\n\t\n\tLine3", result.text)
    }

    @Test
    fun `handleSelectionUnindent works with empty indented line`() {
        val text = TextFieldValue("\tLine1\n\t\n\tLine3", TextRange(0, 15))
        val result = handleSelectionUnindent(text)
        assertNotNull(result)
        assertEquals("Line1\n\nLine3", result!!.text)
    }

    @Test
    fun `handleSelectionIndent on already indented partial selection indents full lines`() {
        // Select only part of an already indented line
        val text = TextFieldValue("\tHello World", TextRange(3, 8)) // "llo W" selected
        val result = handleSelectionIndent(text)
        assertEquals("\t\tHello World", result.text)
        // Selection should cover the full line now
        assertEquals(TextRange(0, 13), result.selection)
    }

    @Test
    fun `handleSelectionUnindent with selection not starting at line beginning`() {
        // Select part of an indented line - should still unindent the full line
        val text = TextFieldValue("\tHello World", TextRange(5, 10)) // "o Wor" selected
        val result = handleSelectionUnindent(text)
        assertNotNull(result)
        assertEquals("Hello World", result!!.text)
    }

    // ==================== toggleBulletOnCurrentLine ====================

    @Test
    fun `toggleBulletOnCurrentLine adds bullet to plain line`() {
        val value = TextFieldValue("Hello", TextRange(3))
        val result = toggleBulletOnCurrentLine(value)
        assertEquals("• Hello", result.text)
        assertEquals(5, result.selection.start) // cursor moved by prefix length
    }

    @Test
    fun `toggleBulletOnCurrentLine removes bullet from bullet line`() {
        val value = TextFieldValue("• Hello", TextRange(5))
        val result = toggleBulletOnCurrentLine(value)
        assertEquals("Hello", result.text)
        assertEquals(3, result.selection.start) // cursor moved back
    }

    @Test
    fun `toggleBulletOnCurrentLine replaces checkbox with bullet`() {
        val value = TextFieldValue("☐ Hello", TextRange(5))
        val result = toggleBulletOnCurrentLine(value)
        assertEquals("• Hello", result.text)
    }

    @Test
    fun `toggleBulletOnCurrentLine replaces checked checkbox with bullet`() {
        val value = TextFieldValue("☑ Hello", TextRange(5))
        val result = toggleBulletOnCurrentLine(value)
        assertEquals("• Hello", result.text)
    }

    @Test
    fun `toggleBulletOnCurrentLine preserves indentation`() {
        val value = TextFieldValue("\t\tHello", TextRange(4))
        val result = toggleBulletOnCurrentLine(value)
        assertEquals("\t\t• Hello", result.text)
    }

    @Test
    fun `toggleBulletOnCurrentLine works on specific line in multi-line text`() {
        val value = TextFieldValue("Line1\nLine2\nLine3", TextRange(8)) // cursor in "Line2"
        val result = toggleBulletOnCurrentLine(value)
        assertEquals("Line1\n• Line2\nLine3", result.text)
    }

    @Test
    fun `toggleBulletOnCurrentLine adds bullets to multiple selected lines`() {
        val value = TextFieldValue("Line1\nLine2\nLine3", TextRange(0, 17))
        val result = toggleBulletOnCurrentLine(value)
        assertEquals("• Line1\n• Line2\n• Line3", result.text)
        assertEquals(TextRange(0, 23), result.selection)
    }

    @Test
    fun `toggleBulletOnCurrentLine removes bullets from multiple selected lines`() {
        val value = TextFieldValue("• Line1\n• Line2\n• Line3", TextRange(0, 23))
        val result = toggleBulletOnCurrentLine(value)
        assertEquals("Line1\nLine2\nLine3", result.text)
        assertEquals(TextRange(0, 17), result.selection)
    }

    @Test
    fun `toggleBulletOnCurrentLine handles mixed prefixes by adding bullets to non-bullet lines`() {
        // Mix of bullet, checkbox, and plain lines - not all have bullets
        val value = TextFieldValue("• Bullet\n☐ Checkbox\nPlain", TextRange(0, 25))
        val result = toggleBulletOnCurrentLine(value)
        // Keep existing bullet, convert checkbox and plain to bullets
        assertEquals("• Bullet\n• Checkbox\n• Plain", result.text)
    }

    @Test
    fun `toggleBulletOnCurrentLine keeps existing bullets when adding to other lines`() {
        val value = TextFieldValue("• Already\nPlain\n• Also", TextRange(0, 21))
        val result = toggleBulletOnCurrentLine(value)
        // Keep existing bullets, add to plain line
        assertEquals("• Already\n• Plain\n• Also", result.text)
    }

    @Test
    fun `toggleBulletOnCurrentLine on partial selection affects full lines`() {
        val value = TextFieldValue("Line1\nLine2\nLine3", TextRange(3, 14)) // partial selection across lines
        val result = toggleBulletOnCurrentLine(value)
        assertEquals("• Line1\n• Line2\n• Line3", result.text)
    }

    // ==================== toggleCheckboxOnCurrentLine ====================

    @Test
    fun `toggleCheckboxOnCurrentLine adds checkbox to plain line`() {
        val value = TextFieldValue("Task", TextRange(2))
        val result = toggleCheckboxOnCurrentLine(value)
        assertEquals("☐ Task", result.text)
        assertEquals(4, result.selection.start)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine removes unchecked checkbox`() {
        val value = TextFieldValue("☐ Task", TextRange(4))
        val result = toggleCheckboxOnCurrentLine(value)
        assertEquals("Task", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine removes checked checkbox`() {
        val value = TextFieldValue("☑ Done", TextRange(4))
        val result = toggleCheckboxOnCurrentLine(value)
        assertEquals("Done", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine replaces bullet with checkbox`() {
        val value = TextFieldValue("• Item", TextRange(4))
        val result = toggleCheckboxOnCurrentLine(value)
        assertEquals("☐ Item", result.text)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine preserves indentation`() {
        val value = TextFieldValue("\tTask", TextRange(3))
        val result = toggleCheckboxOnCurrentLine(value)
        assertEquals("\t☐ Task", result.text)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine works on specific line in multi-line text`() {
        val value = TextFieldValue("Line1\nLine2\nLine3", TextRange(8))
        val result = toggleCheckboxOnCurrentLine(value)
        assertEquals("Line1\n☐ Line2\nLine3", result.text)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine adds checkboxes to multiple selected lines`() {
        val value = TextFieldValue("Task1\nTask2\nTask3", TextRange(0, 17))
        val result = toggleCheckboxOnCurrentLine(value)
        assertEquals("☐ Task1\n☐ Task2\n☐ Task3", result.text)
        assertEquals(TextRange(0, 23), result.selection)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine removes checkboxes from multiple selected lines`() {
        val value = TextFieldValue("☐ Task1\n☐ Task2\n☐ Task3", TextRange(0, 23))
        val result = toggleCheckboxOnCurrentLine(value)
        assertEquals("Task1\nTask2\nTask3", result.text)
        assertEquals(TextRange(0, 17), result.selection)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine handles mixed prefixes by adding checkboxes to non-checkbox lines`() {
        // Mix of checkbox, bullet, and plain lines - not all have checkboxes
        val value = TextFieldValue("☐ Checkbox\n• Bullet\nPlain", TextRange(0, 25))
        val result = toggleCheckboxOnCurrentLine(value)
        // Keep existing checkbox, convert bullet and plain to checkboxes
        assertEquals("☐ Checkbox\n☐ Bullet\n☐ Plain", result.text)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine keeps existing checkboxes when adding to other lines`() {
        val value = TextFieldValue("☐ Already\nPlain\n☑ Done", TextRange(0, 22))
        val result = toggleCheckboxOnCurrentLine(value)
        // Keep existing checkboxes (both checked and unchecked), add to plain line
        assertEquals("☐ Already\n☐ Plain\n☑ Done", result.text)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine removes all when all have checkboxes`() {
        // Mix of checked and unchecked - all have checkboxes
        val value = TextFieldValue("☐ Task1\n☑ Task2\n☐ Task3", TextRange(0, 23))
        val result = toggleCheckboxOnCurrentLine(value)
        // All have checkboxes, so remove all
        assertEquals("Task1\nTask2\nTask3", result.text)
    }

    @Test
    fun `toggleCheckboxOnCurrentLine on partial selection affects full lines`() {
        val value = TextFieldValue("Task1\nTask2\nTask3", TextRange(3, 14)) // partial selection across lines
        val result = toggleCheckboxOnCurrentLine(value)
        assertEquals("☐ Task1\n☐ Task2\n☐ Task3", result.text)
    }

    // ==================== removeEmptyLineAfterDeletion ====================

    @Test
    fun `removeEmptyLineAfterDeletion removes empty line when cursor at empty line start`() {
        // "Line1\n\nLine2" with cursor at position 6 (start of empty line)
        val value = TextFieldValue("Line1\n\nLine2", TextRange(6))
        val result = removeEmptyLineAfterDeletion(value)
        assertNotNull(result)
        assertEquals("Line1\nLine2", result!!.text)
        assertEquals(5, result.selection.start)
    }

    @Test
    fun `removeEmptyLineAfterDeletion returns null when cursor not at line start`() {
        val value = TextFieldValue("Line1\nLine2", TextRange(8))
        val result = removeEmptyLineAfterDeletion(value)
        assertNull(result)
    }

    @Test
    fun `removeEmptyLineAfterDeletion returns null when line not empty`() {
        val value = TextFieldValue("Line1\nLine2", TextRange(6)) // cursor at start of "Line2"
        val result = removeEmptyLineAfterDeletion(value)
        assertNull(result)
    }

    @Test
    fun `removeEmptyLineAfterDeletion returns null when selection not collapsed`() {
        val value = TextFieldValue("Line1\n\nLine2", TextRange(6, 7))
        val result = removeEmptyLineAfterDeletion(value)
        assertNull(result)
    }

    @Test
    fun `removeEmptyLineAfterDeletion returns null at text start`() {
        val value = TextFieldValue("\nLine2", TextRange(0))
        val result = removeEmptyLineAfterDeletion(value)
        assertNull(result)
    }

    @Test
    fun `removeEmptyLineAfterDeletion returns null at text end`() {
        val value = TextFieldValue("Line1\n", TextRange(6))
        val result = removeEmptyLineAfterDeletion(value)
        assertNull(result)
    }

    @Test
    fun `removeEmptyLineAfterDeletion handles multiple consecutive empty lines`() {
        // "Line1\n\n\nLine2" with cursor at position 7 (second empty line)
        val value = TextFieldValue("Line1\n\n\nLine2", TextRange(7))
        val result = removeEmptyLineAfterDeletion(value)
        assertNotNull(result)
        assertEquals("Line1\n\nLine2", result!!.text)
        assertEquals(6, result.selection.start)
    }

}
