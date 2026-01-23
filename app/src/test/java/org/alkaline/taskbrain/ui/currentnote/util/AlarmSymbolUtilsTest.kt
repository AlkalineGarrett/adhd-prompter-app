package org.alkaline.taskbrain.ui.currentnote.util

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class AlarmSymbolUtilsTest {

    // region insertAlarmSymbolAtLineEnd tests

    @Test
    fun `insertAlarmSymbolAtLineEnd inserts symbol at end of single line`() {
        val value = TextFieldValue("Hello world", TextRange(5))

        val result = AlarmSymbolUtils.insertAlarmSymbolAtLineEnd(value)

        assertEquals("Hello world ⏰", result.text)
        assertEquals(5, result.selection.start)
    }

    @Test
    fun `insertAlarmSymbolAtLineEnd inserts symbol at end of current line in multiline text`() {
        val value = TextFieldValue("First line\nSecond line\nThird line", TextRange(15)) // cursor in "Second"

        val result = AlarmSymbolUtils.insertAlarmSymbolAtLineEnd(value)

        assertEquals("First line\nSecond line ⏰\nThird line", result.text)
        assertEquals(15, result.selection.start)
    }

    @Test
    fun `insertAlarmSymbolAtLineEnd adds space before symbol if line ends with text`() {
        val value = TextFieldValue("Test", TextRange(4))

        val result = AlarmSymbolUtils.insertAlarmSymbolAtLineEnd(value)

        assertEquals("Test ⏰", result.text)
    }

    @Test
    fun `insertAlarmSymbolAtLineEnd does not add extra space if line ends with space`() {
        val value = TextFieldValue("Test ", TextRange(5))

        val result = AlarmSymbolUtils.insertAlarmSymbolAtLineEnd(value)

        assertEquals("Test ⏰", result.text)
    }

    @Test
    fun `insertAlarmSymbolAtLineEnd handles empty line`() {
        val value = TextFieldValue("", TextRange(0))

        val result = AlarmSymbolUtils.insertAlarmSymbolAtLineEnd(value)

        assertEquals("⏰", result.text)
    }

    @Test
    fun `insertAlarmSymbolAtLineEnd preserves cursor position`() {
        val value = TextFieldValue("Hello world", TextRange(3)) // cursor after "Hel"

        val result = AlarmSymbolUtils.insertAlarmSymbolAtLineEnd(value)

        assertEquals(3, result.selection.start)
    }

    // endregion

    // region isAlarmSymbol tests

    @Test
    fun `isAlarmSymbol returns true for alarm symbol`() {
        val text = "Test ⏰ line"

        assertTrue(AlarmSymbolUtils.isAlarmSymbol(text, 5))
    }

    @Test
    fun `isAlarmSymbol returns false for regular character`() {
        val text = "Test ⏰ line"

        assertFalse(AlarmSymbolUtils.isAlarmSymbol(text, 0))
        assertFalse(AlarmSymbolUtils.isAlarmSymbol(text, 3))
    }

    @Test
    fun `isAlarmSymbol returns false for out of bounds`() {
        val text = "Test"

        assertFalse(AlarmSymbolUtils.isAlarmSymbol(text, -1))
        assertFalse(AlarmSymbolUtils.isAlarmSymbol(text, 10))
    }

    // endregion

    // region findAllAlarmSymbols tests

    @Test
    fun `findAllAlarmSymbols returns empty list when no symbols`() {
        val text = "No alarm symbols here"

        val result = AlarmSymbolUtils.findAllAlarmSymbols(text)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findAllAlarmSymbols returns single position`() {
        val text = "Test ⏰"

        val result = AlarmSymbolUtils.findAllAlarmSymbols(text)

        assertEquals(1, result.size)
        assertEquals(5, result[0])
    }

    @Test
    fun `findAllAlarmSymbols returns multiple positions`() {
        val text = "First ⏰\nSecond ⏰"

        val result = AlarmSymbolUtils.findAllAlarmSymbols(text)

        assertEquals(2, result.size)
        assertEquals(6, result[0])
        assertEquals(15, result[1])
    }

    // endregion

    // region getAlarmSymbolInfo tests

    @Test
    fun `getAlarmSymbolInfo returns null for non-symbol position`() {
        val text = "Test ⏰ line"

        val result = AlarmSymbolUtils.getAlarmSymbolInfo(text, 0)

        assertNull(result)
    }

    @Test
    fun `getAlarmSymbolInfo returns correct info for symbol on first line`() {
        val text = "Test ⏰ line"

        val result = AlarmSymbolUtils.getAlarmSymbolInfo(text, 5)

        assertNotNull(result)
        assertEquals(5, result!!.charOffset)
        assertEquals(0, result.lineIndex)
        assertEquals(0, result.symbolIndexOnLine)
    }

    @Test
    fun `getAlarmSymbolInfo returns correct info for symbol on second line`() {
        val text = "First line\nSecond ⏰ line"

        val result = AlarmSymbolUtils.getAlarmSymbolInfo(text, 18)

        assertNotNull(result)
        assertEquals(18, result!!.charOffset)
        assertEquals(1, result.lineIndex)
        assertEquals(0, result.symbolIndexOnLine)
    }

    @Test
    fun `getAlarmSymbolInfo returns correct symbol index for multiple symbols on same line`() {
        val text = "First ⏰ Second ⏰"

        val result1 = AlarmSymbolUtils.getAlarmSymbolInfo(text, 6)
        val result2 = AlarmSymbolUtils.getAlarmSymbolInfo(text, 15)

        assertNotNull(result1)
        assertEquals(0, result1!!.symbolIndexOnLine)

        assertNotNull(result2)
        assertEquals(1, result2!!.symbolIndexOnLine)
    }

    // endregion

    // region removeAlarmSymbol tests

    @Test
    fun `removeAlarmSymbol removes symbol and preceding space`() {
        val value = TextFieldValue("Test ⏰", TextRange(0))

        val result = AlarmSymbolUtils.removeAlarmSymbol(value, 5)

        assertEquals("Test", result.text)
    }

    @Test
    fun `removeAlarmSymbol removes only symbol when no preceding space`() {
        val value = TextFieldValue("Test⏰", TextRange(0))

        val result = AlarmSymbolUtils.removeAlarmSymbol(value, 4)

        assertEquals("Test", result.text)
    }

    @Test
    fun `removeAlarmSymbol returns unchanged value for non-symbol position`() {
        val value = TextFieldValue("Test ⏰", TextRange(0))

        val result = AlarmSymbolUtils.removeAlarmSymbol(value, 0)

        assertEquals("Test ⏰", result.text)
    }

    @Test
    fun `removeAlarmSymbol adjusts cursor position when after removal point`() {
        val value = TextFieldValue("Test ⏰ more text", TextRange(12))

        val result = AlarmSymbolUtils.removeAlarmSymbol(value, 5)

        assertEquals("Test more text", result.text)
        assertEquals(10, result.selection.start) // cursor moved back by 2 (space + symbol)
    }

    // endregion
}
