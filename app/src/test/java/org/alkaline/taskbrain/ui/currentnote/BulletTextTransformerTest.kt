package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BulletTextTransformerTest {

    private fun transform(oldText: String, oldCursor: Int, newText: String, newCursor: Int): TextFieldValue {
        val oldValue = TextFieldValue(oldText, TextRange(oldCursor))
        val newValue = TextFieldValue(newText, TextRange(newCursor))
        return transformBulletText(oldValue, newValue)
    }

    // ==================== Case 1: "* " -> "• " ====================

    @Test
    fun `asterisk space at start converts to bullet`() {
        val result = transform("*", 1, "* ", 2)
        assertEquals("• ", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `asterisk space after newline converts to bullet`() {
        // "Hello\n*" = 7 chars, cursor at 7; "Hello\n* " = 8 chars, cursor at 8
        val result = transform("Hello\n*", 7, "Hello\n* ", 8)
        assertEquals("Hello\n• ", result.text)
        assertEquals(8, result.selection.start)
    }

    @Test
    fun `asterisk space in middle of line does not convert`() {
        val result = transform("Hello *", 7, "Hello * ", 8)
        assertEquals("Hello * ", result.text)
    }

    @Test
    fun `multiple asterisk spaces on different lines all convert`() {
        val result = transform("* Line1\n*", 9, "* Line1\n* ", 10)
        assertEquals("• Line1\n• ", result.text)
    }

    @Test
    fun `asterisk without space does not convert`() {
        val result = transform("", 0, "*", 1)
        assertEquals("*", result.text)
    }

    // ==================== Case 2: Enter adds bullet ====================

    @Test
    fun `enter after bullet line adds bullet to new line`() {
        val result = transform("• Hello", 7, "• Hello\n", 8)
        assertEquals("• Hello\n• ", result.text)
        assertEquals(10, result.selection.start)
    }

    @Test
    fun `enter in middle of bullet line adds bullet to new line`() {
        val result = transform("• Hello", 4, "• Hel\nlo", 6)
        assertEquals("• Hel\n• lo", result.text)
        assertEquals(8, result.selection.start)
    }

    @Test
    fun `enter after non-bullet line does not add bullet`() {
        val result = transform("Hello", 5, "Hello\n", 6)
        assertEquals("Hello\n", result.text)
        assertEquals(6, result.selection.start)
    }

    @Test
    fun `enter on empty bullet line exits bullet mode`() {
        val result = transform("• ", 2, "• \n", 3)
        assertEquals("", result.text)
        assertEquals(0, result.selection.start)
    }

    @Test
    fun `enter on empty bullet line with content before preserves previous content`() {
        val result = transform("• First\n• ", 10, "• First\n• \n", 11)
        assertEquals("• First\n", result.text)
        assertEquals(8, result.selection.start)
    }

    @Test
    fun `enter after multiple bullet lines continues list`() {
        val result = transform("• Line1\n• Line2", 15, "• Line1\n• Line2\n", 16)
        assertEquals("• Line1\n• Line2\n• ", result.text)
        assertEquals(18, result.selection.start)
    }

    // ==================== Case 3: Backspace converts bullet to asterisk ====================

    @Test
    fun `backspace deleting space after bullet converts to asterisk`() {
        val result = transform("• Hello", 2, "•Hello", 1)
        assertEquals("* Hello", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `backspace on bullet after newline converts to asterisk`() {
        val result = transform("Line1\n• Hello", 8, "Line1\n•Hello", 7)
        assertEquals("Line1\n* Hello", result.text)
        assertEquals(8, result.selection.start)
    }

    @Test
    fun `backspace on empty bullet converts to asterisk`() {
        val result = transform("• ", 2, "•", 1)
        assertEquals("* ", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `backspace in middle of text does not affect bullet`() {
        val result = transform("• Hello", 5, "• Helo", 4)
        assertEquals("• Helo", result.text)
        assertEquals(4, result.selection.start)
    }

    @Test
    fun `bullet with space intact is not converted`() {
        val result = transform("• Hello", 7, "• Hell", 6)
        assertEquals("• Hell", result.text)
    }

    // ==================== Edge cases ====================

    @Test
    fun `empty text stays empty`() {
        val result = transform("", 0, "", 0)
        assertEquals("", result.text)
    }

    @Test
    fun `typing normal text is unchanged`() {
        val result = transform("Hello", 5, "Hello World", 11)
        assertEquals("Hello World", result.text)
    }

    @Test
    fun `bullet character typed directly is not converted`() {
        val result = transform("", 0, "•", 1)
        assertEquals("•", result.text)
    }

    @Test
    fun `multiple bullets on separate lines preserved`() {
        val result = transform("• Line1\n• Line2", 15, "• Line1\n• Line2!", 16)
        assertEquals("• Line1\n• Line2!", result.text)
    }

    @Test
    fun `asterisk space conversion works on first of multiple lines`() {
        val result = transform("*\nLine2", 1, "* \nLine2", 2)
        assertEquals("• \nLine2", result.text)
    }

    @Test
    fun `text selection is preserved when no transformation occurs`() {
        // Simulates selecting text - selection should be preserved
        val oldValue = TextFieldValue("Hello World", TextRange(0))
        val newValue = TextFieldValue("Hello World", TextRange(0, 5)) // Selecting "Hello"
        val result = transformBulletText(oldValue, newValue)
        assertEquals("Hello World", result.text)
        assertEquals(TextRange(0, 5), result.selection) // Selection should be preserved
    }

    // ==================== Checkbox: "[]" -> "☐ " ====================

    @Test
    fun `empty brackets at start converts to unchecked checkbox`() {
        val result = transform("[", 1, "[]", 2)
        assertEquals("☐ ", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `empty brackets after newline converts to unchecked checkbox`() {
        val result = transform("Hello\n[", 7, "Hello\n[]", 8)
        assertEquals("Hello\n☐ ", result.text)
        assertEquals(8, result.selection.start)
    }

    @Test
    fun `empty brackets in middle of line does not convert`() {
        val result = transform("Hello [", 7, "Hello []", 8)
        assertEquals("Hello []", result.text)
    }

    // ==================== Checkbox: "[x]" -> "☑ " ====================

    @Test
    fun `checked brackets at start converts to checked checkbox`() {
        val result = transform("[x", 2, "[x]", 3)
        assertEquals("☑ ", result.text)
        // "[x]" (3 chars) -> "☑ " (2 chars), cursor moves from 3 to 2
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `checked brackets after newline converts to checked checkbox`() {
        val result = transform("Hello\n[x", 8, "Hello\n[x]", 9)
        assertEquals("Hello\n☑ ", result.text)
        assertEquals(8, result.selection.start)
    }

    @Test
    fun `checked brackets in middle of line does not convert`() {
        val result = transform("Hello [x", 8, "Hello [x]", 9)
        assertEquals("Hello [x]", result.text)
    }

    // ==================== Checkbox: Enter behavior ====================

    @Test
    fun `enter after unchecked checkbox line adds unchecked checkbox`() {
        val result = transform("☐ Task", 6, "☐ Task\n", 7)
        assertEquals("☐ Task\n☐ ", result.text)
        assertEquals(9, result.selection.start)
    }

    @Test
    fun `enter after checked checkbox line adds unchecked checkbox`() {
        val result = transform("☑ Done", 6, "☑ Done\n", 7)
        assertEquals("☑ Done\n☐ ", result.text)
        assertEquals(9, result.selection.start)
    }

    @Test
    fun `enter on empty unchecked checkbox exits checkbox mode`() {
        val result = transform("☐ ", 2, "☐ \n", 3)
        assertEquals("", result.text)
        assertEquals(0, result.selection.start)
    }

    @Test
    fun `enter on empty checked checkbox exits checkbox mode`() {
        val result = transform("☑ ", 2, "☑ \n", 3)
        assertEquals("", result.text)
        assertEquals(0, result.selection.start)
    }

    @Test
    fun `enter in middle of checkbox line adds checkbox to new line`() {
        val result = transform("☐ Task", 4, "☐ Ta\nsk", 5)
        assertEquals("☐ Ta\n☐ sk", result.text)
        assertEquals(7, result.selection.start)
    }

    // ==================== Checkbox: Backspace behavior ====================

    @Test
    fun `backspace on unchecked checkbox converts to empty brackets`() {
        val result = transform("☐ Task", 2, "☐Task", 1)
        assertEquals("[]Task", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `backspace on checked checkbox converts to checked brackets`() {
        val result = transform("☑ Done", 2, "☑Done", 1)
        assertEquals("[x]Done", result.text)
        assertEquals(3, result.selection.start)
    }

    @Test
    fun `backspace on empty unchecked checkbox converts to brackets`() {
        val result = transform("☐ ", 2, "☐", 1)
        assertEquals("[]", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `backspace on empty checked checkbox converts to brackets`() {
        val result = transform("☑ ", 2, "☑", 1)
        assertEquals("[x]", result.text)
        assertEquals(3, result.selection.start)
    }

    @Test
    fun `backspace in middle of checkbox line does not affect checkbox`() {
        val result = transform("☐ Task", 5, "☐ Tas", 4)
        assertEquals("☐ Tas", result.text)
        assertEquals(4, result.selection.start)
    }

    // ==================== Mixed bullets and checkboxes ====================

    @Test
    fun `bullet and checkbox on different lines both preserved`() {
        val result = transform("• Bullet\n☐ Check", 17, "• Bullet\n☐ Check!", 18)
        assertEquals("• Bullet\n☐ Check!", result.text)
    }

    @Test
    fun `enter after bullet does not add checkbox`() {
        val result = transform("• Item", 6, "• Item\n", 7)
        assertEquals("• Item\n• ", result.text)
        assertEquals(9, result.selection.start)
    }

    @Test
    fun `enter after checkbox does not add bullet`() {
        val result = transform("☐ Task", 6, "☐ Task\n", 7)
        assertEquals("☐ Task\n☐ ", result.text)
        assertEquals(9, result.selection.start)
    }

    // ==================== Checkbox tap to toggle ====================

    private fun tapCheckbox(text: String, oldCursor: Int, newCursor: Int): TextFieldValue? {
        val oldValue = TextFieldValue(text, TextRange(oldCursor))
        val newValue = TextFieldValue(text, TextRange(newCursor))
        return handleCheckboxTap(oldValue, newValue)
    }

    @Test
    fun `tap on unchecked checkbox toggles to checked`() {
        val result = tapCheckbox("☐ Task", 5, 0)
        assertNotNull(result)
        assertEquals("☑ Task", result!!.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `tap on checked checkbox toggles to unchecked`() {
        val result = tapCheckbox("☑ Done", 5, 0)
        assertNotNull(result)
        assertEquals("☐ Done", result!!.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `tap at position 1 of checkbox also toggles`() {
        val result = tapCheckbox("☐ Task", 5, 1)
        assertNotNull(result)
        assertEquals("☑ Task", result!!.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `tap after checkbox does not toggle`() {
        val result = tapCheckbox("☐ Task", 0, 3)
        assertNull(result)
    }

    @Test
    fun `tap on checkbox in second line toggles`() {
        val result = tapCheckbox("Line1\n☐ Task", 0, 6)
        assertNotNull(result)
        assertEquals("Line1\n☑ Task", result!!.text)
        assertEquals(8, result.selection.start)
    }

    @Test
    fun `tap on second checkbox toggles only that one`() {
        val result = tapCheckbox("☐ First\n☑ Second", 0, 8)
        assertNotNull(result)
        assertEquals("☐ First\n☐ Second", result!!.text)
        assertEquals(10, result.selection.start)
    }

    @Test
    fun `text change does not trigger toggle`() {
        // Simulates typing, not tapping
        val oldValue = TextFieldValue("☐ Tas", TextRange(5))
        val newValue = TextFieldValue("☐ Task", TextRange(6))
        val result = handleCheckboxTap(oldValue, newValue)
        assertNull(result)
    }

    @Test
    fun `same cursor position does not trigger toggle`() {
        val result = tapCheckbox("☐ Task", 0, 0)
        assertNull(result)
    }

    @Test
    fun `tap on bullet does not trigger checkbox toggle`() {
        val result = tapCheckbox("• Item", 5, 0)
        assertNull(result)
    }

    @Test
    fun `tap on regular text does not trigger toggle`() {
        val result = tapCheckbox("Hello", 3, 0)
        assertNull(result)
    }

    @Test
    fun `text selection does not trigger toggle`() {
        // Simulates selecting text (selection is a range, not collapsed)
        val oldValue = TextFieldValue("☐ Task", TextRange(5))
        val newValue = TextFieldValue("☐ Task", TextRange(0, 3)) // Selection from 0 to 3
        val result = handleCheckboxTap(oldValue, newValue)
        assertNull(result)
    }

    @Test
    fun `collapsing selection onto checkbox does not trigger toggle`() {
        // User had a selection and collapsed it onto a checkbox position
        val oldValue = TextFieldValue("☐ Task", TextRange(2, 5)) // Had selection
        val newValue = TextFieldValue("☐ Task", TextRange(0)) // Collapsed to checkbox
        val result = handleCheckboxTap(oldValue, newValue)
        assertNull(result)
    }

    @Test
    fun `dragging selection over checkbox does not trigger toggle`() {
        // User is dragging selection, passing through checkbox area
        val oldValue = TextFieldValue("☐ Task", TextRange(3, 6))
        val newValue = TextFieldValue("☐ Task", TextRange(0, 6)) // Extended selection to include checkbox
        val result = handleCheckboxTap(oldValue, newValue)
        assertNull(result)
    }

    // ==================== Indentation: Space to tab ====================

    @Test
    fun `space before bullet converts to tab`() {
        // Cursor at position 1, space was just typed before bullet
        val result = transform("• Item", 0, " • Item", 1)
        assertEquals("\t• Item", result.text)
        assertEquals(1, result.selection.start)
    }

    @Test
    fun `space before checkbox converts to tab`() {
        val result = transform("☐ Task", 0, " ☐ Task", 1)
        assertEquals("\t☐ Task", result.text)
        assertEquals(1, result.selection.start)
    }

    @Test
    fun `space after tab before bullet converts to tab`() {
        val result = transform("\t• Item", 1, "\t • Item", 2)
        assertEquals("\t\t• Item", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `space in middle of text does not convert to tab`() {
        val result = transform("• Item", 6, "• Item ", 7)
        assertEquals("• Item ", result.text)
        assertEquals(7, result.selection.start)
    }

    @Test
    fun `space before regular text does not convert to tab`() {
        val result = transform("Hello", 0, " Hello", 1)
        assertEquals(" Hello", result.text)
        assertEquals(1, result.selection.start)
    }

    // ==================== Indentation: Enter preserves indentation ====================

    @Test
    fun `enter after indented bullet preserves indentation`() {
        val result = transform("\t• Item", 7, "\t• Item\n", 8)
        assertEquals("\t• Item\n\t• ", result.text)
        assertEquals(11, result.selection.start)
    }

    @Test
    fun `enter after double indented bullet preserves indentation`() {
        val result = transform("\t\t• Item", 8, "\t\t• Item\n", 9)
        assertEquals("\t\t• Item\n\t\t• ", result.text)
        assertEquals(13, result.selection.start)
    }

    @Test
    fun `enter on empty indented bullet unindents`() {
        val result = transform("\t• ", 3, "\t• \n", 4)
        assertEquals("• ", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun `enter on empty double indented bullet unindents one level`() {
        val result = transform("\t\t• ", 4, "\t\t• \n", 5)
        assertEquals("\t• ", result.text)
        assertEquals(3, result.selection.start)
    }

    @Test
    fun `enter on empty non-indented bullet exits list`() {
        val result = transform("• ", 2, "• \n", 3)
        assertEquals("", result.text)
        assertEquals(0, result.selection.start)
    }

    // ==================== Indentation: Tab deletion (unindent) ====================

    @Test
    fun `backspace on tab before bullet removes tab`() {
        val result = transform("\t• Item", 1, "• Item", 0)
        assertEquals("• Item", result.text)
        assertEquals(0, result.selection.start)
    }

    @Test
    fun `backspace on first tab of double indented bullet removes one tab`() {
        val result = transform("\t\t• Item", 1, "\t• Item", 0)
        assertEquals("\t• Item", result.text)
        assertEquals(0, result.selection.start)
    }
}
