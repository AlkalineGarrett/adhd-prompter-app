package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
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
}
