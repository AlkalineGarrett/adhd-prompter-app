package org.alkaline.taskbrain.ui.currentnote.util

import org.junit.Assert.*
import org.junit.Test

class TappableSymbolTest {

    // region TappableSymbol.at tests

    @Test
    fun `at returns null for non-symbol position`() {
        val text = "Test ⏰ line"

        assertNull(TappableSymbol.at(text, 0))
        assertNull(TappableSymbol.at(text, 3))
        assertNull(TappableSymbol.at(text, 4)) // space before symbol
    }

    @Test
    fun `at returns ALARM for alarm symbol position`() {
        val text = "Test ⏰ line"

        assertEquals(TappableSymbol.ALARM, TappableSymbol.at(text, 5))
    }

    @Test
    fun `at returns ALARM for alarm at start of string`() {
        val text = "⏰ start"

        assertEquals(TappableSymbol.ALARM, TappableSymbol.at(text, 0))
    }

    @Test
    fun `at returns ALARM for alarm at end of string`() {
        val text = "end ⏰"

        assertEquals(TappableSymbol.ALARM, TappableSymbol.at(text, 4))
    }

    @Test
    fun `at returns null for negative offset`() {
        assertNull(TappableSymbol.at("Test ⏰", -1))
    }

    @Test
    fun `at returns null for offset at string length`() {
        val text = "Test"
        assertNull(TappableSymbol.at(text, 4))
    }

    @Test
    fun `at returns null for offset beyond string length`() {
        assertNull(TappableSymbol.at("Test", 10))
    }

    @Test
    fun `at returns null for empty string`() {
        assertNull(TappableSymbol.at("", 0))
    }

    @Test
    fun `at finds correct symbol among multiple on same line`() {
        val text = "⏰ middle ⏰" // offsets: 0=⏰, 1=' ', ..., 9=⏰

        assertEquals(TappableSymbol.ALARM, TappableSymbol.at(text, 0))
        assertEquals(TappableSymbol.ALARM, TappableSymbol.at(text, 9))
        assertNull(TappableSymbol.at(text, 5)) // 'd' in middle
    }

    // endregion

    // region TappableSymbol.containsAny tests

    @Test
    fun `containsAny returns true when text contains alarm symbol`() {
        assertTrue(TappableSymbol.containsAny("Test ⏰ line"))
    }

    @Test
    fun `containsAny returns true for alarm at start`() {
        assertTrue(TappableSymbol.containsAny("⏰"))
    }

    @Test
    fun `containsAny returns true for multiple alarm symbols`() {
        assertTrue(TappableSymbol.containsAny("⏰ and ⏰"))
    }

    @Test
    fun `containsAny returns false when text has no tappable symbols`() {
        assertFalse(TappableSymbol.containsAny("Plain text"))
    }

    @Test
    fun `containsAny returns false for empty string`() {
        assertFalse(TappableSymbol.containsAny(""))
    }

    @Test
    fun `containsAny returns false for other emoji`() {
        assertFalse(TappableSymbol.containsAny("Hello 😀 world"))
    }

    // endregion

    // region TappableSymbol enum properties

    @Test
    fun `ALARM has correct char`() {
        assertEquals("⏰", TappableSymbol.ALARM.char)
    }

    // endregion

    // region SymbolTapInfo tests

    @Test
    fun `SymbolTapInfo stores all fields correctly`() {
        val info = SymbolTapInfo(
            symbol = TappableSymbol.ALARM,
            charOffset = 5,
            lineIndex = 2,
            symbolIndexOnLine = 0
        )

        assertEquals(TappableSymbol.ALARM, info.symbol)
        assertEquals(5, info.charOffset)
        assertEquals(2, info.lineIndex)
        assertEquals(0, info.symbolIndexOnLine)
    }

    @Test
    fun `SymbolTapInfo equals works for identical values`() {
        val info1 = SymbolTapInfo(TappableSymbol.ALARM, 5, 2, 0)
        val info2 = SymbolTapInfo(TappableSymbol.ALARM, 5, 2, 0)

        assertEquals(info1, info2)
    }

    @Test
    fun `SymbolTapInfo equals distinguishes different symbol index`() {
        val info1 = SymbolTapInfo(TappableSymbol.ALARM, 5, 2, 0)
        val info2 = SymbolTapInfo(TappableSymbol.ALARM, 5, 2, 1)

        assertNotEquals(info1, info2)
    }

    // endregion
}
