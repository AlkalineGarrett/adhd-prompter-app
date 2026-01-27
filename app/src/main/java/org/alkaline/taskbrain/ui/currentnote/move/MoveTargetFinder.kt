package org.alkaline.taskbrain.ui.currentnote.move

import org.alkaline.taskbrain.ui.currentnote.LineState
import org.alkaline.taskbrain.ui.currentnote.selection.EditorSelection
import org.alkaline.taskbrain.ui.currentnote.selection.SelectionCoordinates
import org.alkaline.taskbrain.ui.currentnote.util.IndentationUtils

/**
 * Utility functions for finding move targets when reordering lines.
 * These are pure functions that determine where lines can be moved to.
 */
object MoveTargetFinder {

    /**
     * Finds the target position for moving a range up.
     * Returns null if at document boundary.
     */
    fun findMoveUpTarget(lines: List<LineState>, lineRange: IntRange): Int? {
        if (lineRange.first <= 0) return null
        val firstIndent = IndentationUtils.getIndentLevel(lines, lineRange.first)
        var target = lineRange.first - 1

        // Find the head of the previous group at same-or-less indent.
        // Skip over any deeper lines (children of a previous group).
        while (target > 0 && IndentationUtils.getIndentLevel(lines, target) > firstIndent) {
            target--
        }

        // target is now at the head of the previous group (same-or-less indent)
        return target
    }

    /**
     * Finds the target position for moving a range down.
     * Returns null if at document boundary.
     */
    fun findMoveDownTarget(lines: List<LineState>, lineRange: IntRange): Int? {
        if (lineRange.last >= lines.lastIndex) return null
        val firstIndent = IndentationUtils.getIndentLevel(lines, lineRange.first)
        var target = lineRange.last + 1
        val targetIndent = IndentationUtils.getIndentLevel(lines, target)

        // Find end of target's logical block (at same-or-less indent)
        if (targetIndent <= firstIndent) {
            // Target is at same/less indent - find end of its block
            while (target < lines.lastIndex && IndentationUtils.getIndentLevel(lines, target + 1) > targetIndent) {
                target++
            }
        }
        // Target position is after the block
        return target + 1
    }

    /**
     * Gets the move target for the current state.
     * Handles both selection and no-selection cases, including the special case
     * where selected lines span different indent levels.
     */
    fun getMoveTarget(
        lines: List<LineState>,
        hasSelection: Boolean,
        selection: EditorSelection,
        focusedLineIndex: Int,
        moveUp: Boolean
    ): Int? {
        val range = if (hasSelection) {
            SelectionCoordinates.getSelectedLineRange(lines, selection, focusedLineIndex)
        } else {
            IndentationUtils.getLogicalBlock(lines, focusedLineIndex)
        }

        if (hasSelection) {
            // Check if first selected line is the shallowest
            val shallowest = range.minOfOrNull { IndentationUtils.getIndentLevel(lines, it) } ?: 0
            val firstIndent = IndentationUtils.getIndentLevel(lines, range.first)
            if (firstIndent > shallowest) {
                // First line isn't shallowest - move one line only
                return if (moveUp) {
                    if (range.first > 0) range.first - 1 else null
                } else {
                    if (range.last < lines.lastIndex) range.last + 2 else null
                }
            }
        }
        return if (moveUp) findMoveUpTarget(lines, range) else findMoveDownTarget(lines, range)
    }

    /**
     * Checks if moving with the current selection would break parent-child relationships.
     * Returns true when:
     * 1. Selection excludes children of the first selected line (orphaning children below)
     * 2. First selected line isn't the shallowest in selection (selecting children without parent)
     */
    fun wouldOrphanChildren(
        lines: List<LineState>,
        hasSelection: Boolean,
        selection: EditorSelection,
        focusedLineIndex: Int
    ): Boolean {
        if (!hasSelection) return false
        val selectedRange = SelectionCoordinates.getSelectedLineRange(lines, selection, focusedLineIndex)

        // Check if first line isn't the shallowest (selecting children without their context)
        val shallowest = selectedRange.minOfOrNull { IndentationUtils.getIndentLevel(lines, it) } ?: 0
        val firstIndent = IndentationUtils.getIndentLevel(lines, selectedRange.first)
        if (firstIndent > shallowest) {
            return true
        }

        // Check if selection excludes children of the first line
        val logicalBlock = IndentationUtils.getLogicalBlock(lines, selectedRange.first)
        return selectedRange.last < logicalBlock.last
    }
}
