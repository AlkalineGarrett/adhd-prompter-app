package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Utilities for managing alarm symbols in text.
 */
object AlarmSymbolUtils {

    const val ALARM_SYMBOL = "⏰"

    /**
     * Inserts an alarm symbol at the end of the line containing the cursor.
     * Preserves the cursor position.
     */
    fun insertAlarmSymbolAtLineEnd(textFieldValue: TextFieldValue): TextFieldValue {
        val text = textFieldValue.text
        val cursorPos = textFieldValue.selection.start
        val lineEnd = TextLineUtils.findLineEnd(text, cursorPos)

        // Check if there's already an alarm symbol at the end of this line
        val lineStart = TextLineUtils.findLineStart(text, cursorPos)
        val lineContent = text.substring(lineStart, lineEnd)

        // Add space before symbol if line doesn't end with whitespace and isn't empty
        val prefix = if (lineContent.isNotEmpty() && !lineContent.last().isWhitespace()) " " else ""
        val symbolToInsert = "$prefix$ALARM_SYMBOL"

        val newText = text.substring(0, lineEnd) + symbolToInsert + text.substring(lineEnd)

        // Adjust cursor position if it was after the insertion point
        val newCursorPos = if (cursorPos >= lineEnd) cursorPos + symbolToInsert.length else cursorPos

        return TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    }

    /**
     * Checks if the character at the given offset is an alarm symbol.
     */
    fun isAlarmSymbol(text: String, offset: Int): Boolean {
        return offset >= 0 && offset < text.length && text[offset].toString() == ALARM_SYMBOL
    }

    /**
     * Finds all alarm symbol positions in the text.
     * Returns a list of character offsets.
     */
    fun findAllAlarmSymbols(text: String): List<Int> {
        val positions = mutableListOf<Int>()
        var index = 0
        while (index < text.length) {
            if (text[index].toString() == ALARM_SYMBOL) {
                positions.add(index)
            }
            index++
        }
        return positions
    }

    /**
     * Gets the line index and symbol index within the line for a tapped alarm symbol.
     * Returns a pair of (lineIndex, symbolIndexOnLine) or null if not on a symbol.
     */
    fun getAlarmSymbolInfo(text: String, charOffset: Int): AlarmSymbolInfo? {
        if (!isAlarmSymbol(text, charOffset)) return null

        val lineIndex = TextLineUtils.getLineIndex(text, charOffset)
        val lineStart = TextLineUtils.findLineStart(text, charOffset)

        // Count which alarm symbol this is on the line (0-indexed)
        val textBeforeOnLine = text.substring(lineStart, charOffset)
        val symbolIndexOnLine = textBeforeOnLine.count { it.toString() == ALARM_SYMBOL }

        return AlarmSymbolInfo(
            charOffset = charOffset,
            lineIndex = lineIndex,
            symbolIndexOnLine = symbolIndexOnLine
        )
    }

    /**
     * Removes an alarm symbol at the specified position.
     * Also removes the preceding space if present.
     */
    fun removeAlarmSymbol(textFieldValue: TextFieldValue, symbolOffset: Int): TextFieldValue {
        val text = textFieldValue.text
        if (!isAlarmSymbol(text, symbolOffset)) return textFieldValue

        // Check if there's a space before the symbol
        val removeSpace = symbolOffset > 0 && text[symbolOffset - 1] == ' '
        val startOffset = if (removeSpace) symbolOffset - 1 else symbolOffset
        val endOffset = symbolOffset + 1

        val newText = text.substring(0, startOffset) + text.substring(endOffset)

        // Adjust cursor position
        val cursorPos = textFieldValue.selection.start
        val newCursorPos = when {
            cursorPos > endOffset -> cursorPos - (endOffset - startOffset)
            cursorPos > startOffset -> startOffset
            else -> cursorPos
        }

        return TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    }
}

/**
 * Information about a tapped alarm symbol.
 */
data class AlarmSymbolInfo(
    val charOffset: Int,
    val lineIndex: Int,
    val symbolIndexOnLine: Int  // Which alarm symbol on this line (0-indexed)
)
