package org.alkaline.taskbrain.ui.currentnote.util

import androidx.compose.ui.graphics.Color

/**
 * Describes a visual badge to overlay on a symbol character in the editor.
 *
 * Each badge specifies how it should be drawn relative to the symbol.
 * To add a new badge style for a future symbol, add a new subclass.
 */
sealed class SymbolBadge {
    /** No badge — symbol renders normally. */
    data object None : SymbolBadge()

    /** Badge character centered on the symbol. Grays out the symbol if [dimSymbol] is true. */
    data class Centered(
        val text: String,
        val color: Color,
        /** Font size as a fraction of the symbol height. */
        val sizeFraction: Float = 0.85f,
        val dimSymbol: Boolean = false
    ) : SymbolBadge()

    /** Badge character in the upper-right quadrant. Grays out the symbol if [dimSymbol] is true. */
    data class Corner(
        val text: String,
        val color: Color,
        /** Font size as a fraction of the symbol height. */
        val sizeFraction: Float = 0.65f,
        val dimSymbol: Boolean = true
    ) : SymbolBadge()
}

/**
 * A badge to draw on a specific occurrence of a symbol character on a line.
 *
 * @param symbol The character to find in the text (e.g. "⏰")
 * @param badge The badge to draw on that occurrence
 */
data class SymbolOverlay(
    val symbol: String,
    val badge: SymbolBadge
)
