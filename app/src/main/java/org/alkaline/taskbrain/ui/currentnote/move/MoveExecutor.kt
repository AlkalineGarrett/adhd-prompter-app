package org.alkaline.taskbrain.ui.currentnote.move

import org.alkaline.taskbrain.ui.currentnote.LineState
import org.alkaline.taskbrain.ui.currentnote.selection.EditorSelection
import org.alkaline.taskbrain.ui.currentnote.selection.SelectionCoordinates

/**
 * Result of a move calculation.
 *
 * @param newLines The new text content for each line after the move
 * @param newFocusedLineIndex The new focused line index after the move
 * @param newSelection The new selection after the move, or null if there was no selection
 * @param newRange The new range of the moved lines
 */
data class MoveResult(
    val newLines: List<String>,
    val newFocusedLineIndex: Int,
    val newSelection: EditorSelection?,
    val newRange: IntRange
)

/**
 * Utility functions for executing line move operations.
 * Contains pure calculation functions for determining the result of a move.
 */
object MoveExecutor {

    /**
     * Calculates the result of moving lines from sourceRange to targetIndex.
     * Returns null if the move is invalid or a no-op.
     *
     * This is a pure function that calculates the result without modifying any state.
     * The caller is responsible for applying the result.
     *
     * @param lines The current lines
     * @param sourceRange The range of lines to move
     * @param targetIndex The target position for the moved lines
     * @param focusedLineIndex The currently focused line index
     * @param selection The current selection, or null if no selection
     * @return The result of the move, or null if the move is invalid
     */
    fun calculateMove(
        lines: List<LineState>,
        sourceRange: IntRange,
        targetIndex: Int,
        focusedLineIndex: Int,
        selection: EditorSelection?
    ): MoveResult? {
        // Validate parameters
        if (sourceRange.first < 0 || sourceRange.last >= lines.size) return null
        if (targetIndex < 0 || targetIndex > lines.size) return null
        // No-op if target is within or adjacent to source
        if (targetIndex >= sourceRange.first && targetIndex <= sourceRange.last + 1) return null

        // Capture selection info BEFORE modifying
        val selStartLine: Int
        val selStartLocal: Int
        val selEndLine: Int
        val selEndLocal: Int
        if (selection != null && selection.hasSelection) {
            val (sl, slo) = SelectionCoordinates.getLineAndLocalOffset(lines, selection.start)
            val (el, elo) = SelectionCoordinates.getLineAndLocalOffset(lines, selection.end)
            selStartLine = sl
            selStartLocal = slo
            selEndLine = el
            selEndLocal = elo
        } else {
            selStartLine = -1
            selStartLocal = 0
            selEndLine = -1
            selEndLocal = 0
        }

        // Extract lines to move (as text)
        val linesToMove = sourceRange.map { lines[it].text }
        val moveCount = linesToMove.size

        // Build new lines list
        val newLines = mutableListOf<String>()

        // Add lines before the source range
        for (i in 0 until sourceRange.first) {
            newLines.add(lines[i].text)
        }
        // Skip source range (they're being moved)
        for (i in (sourceRange.last + 1) until lines.size) {
            newLines.add(lines[i].text)
        }

        // Calculate adjusted target (accounting for removed lines)
        val adjustedTarget = if (targetIndex > sourceRange.first) {
            targetIndex - moveCount
        } else {
            targetIndex
        }

        // Insert moved lines at adjusted target
        for (i in linesToMove.indices) {
            newLines.add(adjustedTarget + i, linesToMove[i])
        }

        // Calculate the new range of moved lines
        val newRange = adjustedTarget until (adjustedTarget + moveCount)

        // Adjust focused line index
        val newFocusedLineIndex = when {
            focusedLineIndex in sourceRange -> {
                // Focus was in moved block - follow it
                val offsetInBlock = focusedLineIndex - sourceRange.first
                adjustedTarget + offsetInBlock
            }
            targetIndex <= focusedLineIndex && sourceRange.first > focusedLineIndex -> {
                // Moved lines inserted before focus, after their original position
                focusedLineIndex + moveCount
            }
            sourceRange.first <= focusedLineIndex && targetIndex > focusedLineIndex -> {
                // Moved lines removed before focus, inserted after
                focusedLineIndex - moveCount
            }
            else -> focusedLineIndex
        }

        // Calculate new selection
        val newSelection: EditorSelection? = if (selection != null && selection.hasSelection) {
            val newStartLine = adjustLineIndexForMove(selStartLine, sourceRange, adjustedTarget, moveCount)

            // If selection ends at offset 0 of a line, it conceptually means "end of previous line".
            // We need to adjust the previous line (which may have moved) and then reference the next line.
            val newEndLine: Int
            val newEndLocal: Int
            if (selEndLocal == 0 && selEndLine > 0) {
                val adjustedPrevLine = adjustLineIndexForMove(selEndLine - 1, sourceRange, adjustedTarget, moveCount)
                newEndLine = adjustedPrevLine + 1
                newEndLocal = 0
            } else {
                newEndLine = adjustLineIndexForMove(selEndLine, sourceRange, adjustedTarget, moveCount)
                newEndLocal = selEndLocal
            }

            // Convert back to global offsets using the new lines list
            val tempLines = newLines.map { LineState(it) }
            val newSelStart = SelectionCoordinates.getLineStartOffset(tempLines, newStartLine) +
                selStartLocal.coerceAtMost(tempLines.getOrNull(newStartLine)?.text?.length ?: 0)
            val newSelEnd = SelectionCoordinates.getLineStartOffset(tempLines, newEndLine) +
                newEndLocal.coerceAtMost(tempLines.getOrNull(newEndLine)?.text?.length ?: 0)

            EditorSelection(newSelStart, newSelEnd)
        } else {
            null
        }

        return MoveResult(
            newLines = newLines,
            newFocusedLineIndex = newFocusedLineIndex,
            newSelection = newSelection,
            newRange = newRange
        )
    }

    /**
     * Helper to adjust a line index after a move operation.
     */
    fun adjustLineIndexForMove(
        lineIndex: Int,
        sourceRange: IntRange,
        targetIndex: Int,
        moveCount: Int
    ): Int {
        return when {
            lineIndex in sourceRange -> {
                // Line was in moved block - calculate new position
                val offsetInBlock = lineIndex - sourceRange.first
                targetIndex + offsetInBlock
            }
            targetIndex <= lineIndex && sourceRange.first > lineIndex -> {
                // Moved lines inserted before this line, from after it
                lineIndex + moveCount
            }
            sourceRange.first <= lineIndex && targetIndex > lineIndex -> {
                // Moved lines removed before this line, inserted after it
                lineIndex - moveCount
            }
            else -> lineIndex
        }
    }
}
