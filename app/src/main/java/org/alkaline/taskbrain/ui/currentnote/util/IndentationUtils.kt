package org.alkaline.taskbrain.ui.currentnote.util

import org.alkaline.taskbrain.ui.currentnote.LineState

/**
 * Utility functions for calculating indentation levels and logical blocks.
 */
object IndentationUtils {

    /**
     * Gets the indentation level from a text string (number of leading tabs).
     * Empty strings have indent level 0.
     */
    fun getIndentLevel(text: String): Int {
        if (text.isEmpty()) return 0
        return text.takeWhile { it == '\t' }.length
    }

    /**
     * Gets the indentation level of a line by index (number of leading tabs).
     * Returns 0 if the line index is out of bounds or the line is empty.
     */
    fun getIndentLevel(lines: List<LineState>, lineIndex: Int): Int {
        val text = lines.getOrNull(lineIndex)?.text ?: return 0
        return getIndentLevel(text)
    }

    /**
     * Gets the logical block for a line: the line itself plus all deeper-indented children below it.
     * A logical block represents a parent line and all its nested children.
     */
    fun getLogicalBlock(lines: List<LineState>, startIndex: Int): IntRange {
        if (startIndex !in lines.indices) return startIndex..startIndex
        val startIndent = getIndentLevel(lines, startIndex)
        var endIndex = startIndex
        for (i in (startIndex + 1) until lines.size) {
            if (getIndentLevel(lines, i) <= startIndent) break
            endIndex = i
        }
        return startIndex..endIndex
    }
}
