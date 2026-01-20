package org.alkaline.taskbrain.ui.currentnote

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private const val TAG = "EditorController"

/**
 * Centralized controller for all editor state modifications.
 *
 * This is the SINGLE CHANNEL through which all state changes flow.
 * Benefits:
 * - Single source of truth
 * - Predictable state transitions
 * - Easy debugging (all changes go through one place)
 * - Proper notification of changes
 *
 * Usage:
 * - ImeState forwards all IME events here
 * - UI components call controller methods instead of directly modifying state
 * - State changes automatically trigger recomposition via stateVersion
 */
class EditorController(
    private val state: EditorState
) {
    // =========================================================================
    // Text Input Operations
    // =========================================================================

    /**
     * Insert text at the current cursor position.
     * If there's a selection, replaces it.
     */
    fun insertText(lineIndex: Int, text: String) {
        Log.d(TAG, "insertText: lineIndex=$lineIndex, text='$text'")

        // Handle newline specially
        if (text.contains('\n')) {
            val parts = text.split('\n')
            parts.forEachIndexed { index, part ->
                if (index > 0) {
                    splitLine(lineIndex + index - 1)
                }
                if (part.isNotEmpty()) {
                    insertTextAtCursor(lineIndex + index, part)
                }
            }
            return
        }

        // If there's a selection, replace it
        if (state.hasSelection) {
            state.replaceSelection(text)
            return
        }

        // Normal insert at cursor
        insertTextAtCursor(lineIndex, text)
    }

    private fun insertTextAtCursor(lineIndex: Int, text: String) {
        val line = state.lines.getOrNull(lineIndex) ?: return
        val cursorInLine = line.cursorPosition
        val newText = line.text.substring(0, cursorInLine) + text + line.text.substring(cursorInLine)
        val newCursor = cursorInLine + text.length

        line.updateFull(newText, newCursor)
        state.requestFocusUpdate()
        state.notifyChange()
    }

    /**
     * Delete backward (backspace) from the current cursor position.
     */
    fun deleteBackward(lineIndex: Int) {
        Log.d(TAG, "deleteBackward: lineIndex=$lineIndex")

        // If there's a selection, delete it
        if (state.hasSelection) {
            state.deleteSelection()
            return
        }

        val line = state.lines.getOrNull(lineIndex) ?: return
        val cursor = line.cursorPosition

        // At start of line content (after prefix)
        if (cursor <= line.prefix.length) {
            // Try to remove prefix character first
            if (line.prefix.isNotEmpty()) {
                val newPrefix = line.prefix.dropLast(1)
                line.updateFull(newPrefix + line.content, newPrefix.length)
                state.requestFocusUpdate()
                state.notifyChange()
            } else if (lineIndex > 0) {
                // Merge with previous line
                mergeToPreviousLine(lineIndex)
            }
            return
        }

        // Normal delete within content
        val newText = line.text.substring(0, cursor - 1) + line.text.substring(cursor)
        line.updateFull(newText, cursor - 1)
        state.requestFocusUpdate()
        state.notifyChange()
    }

    /**
     * Delete forward from the current cursor position.
     */
    fun deleteForward(lineIndex: Int) {
        Log.d(TAG, "deleteForward: lineIndex=$lineIndex")

        if (state.hasSelection) {
            state.deleteSelection()
            return
        }

        val line = state.lines.getOrNull(lineIndex) ?: return
        val cursor = line.cursorPosition

        // At end of line - merge with next line
        if (cursor >= line.text.length) {
            if (lineIndex < state.lines.lastIndex) {
                mergeNextLine(lineIndex)
            }
            return
        }

        // Normal delete
        val newText = line.text.substring(0, cursor) + line.text.substring(cursor + 1)
        line.updateFull(newText, cursor)
        state.requestFocusUpdate()
        state.notifyChange()
    }

    // =========================================================================
    // Line Operations
    // =========================================================================

    /**
     * Gets the prefix for a new line based on the current line's prefix.
     * Converts checked checkboxes to unchecked.
     */
    private fun getNewLinePrefix(currentPrefix: String): String {
        return currentPrefix.replace(LinePrefixes.CHECKBOX_CHECKED, LinePrefixes.CHECKBOX_UNCHECKED)
    }

    /**
     * Creates a new line with prefix continuation and adds it after the specified line.
     * @param lineIndex Index of the current line
     * @param newLineContent Content for the new line (without prefix)
     * @param currentPrefix Prefix from the current line
     */
    private fun createNewLineWithPrefix(lineIndex: Int, newLineContent: String, currentPrefix: String) {
        val newLinePrefix = getNewLinePrefix(currentPrefix)
        val newLineText = newLinePrefix + newLineContent
        val newLineCursor = newLinePrefix.length

        state.lines.add(lineIndex + 1, LineState(newLineText, newLineCursor))
        state.focusedLineIndex = lineIndex + 1
        state.requestFocusUpdate()
        state.notifyChange()
    }

    /**
     * Split line at cursor (Enter key).
     * Continues the prefix (indentation + bullet/checkbox) on the new line.
     * Checked checkboxes become unchecked on the new line.
     */
    fun splitLine(lineIndex: Int) {
        Log.d(TAG, "splitLine: lineIndex=$lineIndex")

        state.clearSelection()
        val line = state.lines.getOrNull(lineIndex) ?: return
        val cursor = line.cursorPosition
        val prefix = line.prefix

        val beforeCursor = line.text.substring(0, cursor)
        val afterCursor = line.text.substring(cursor)

        // Update current line
        line.updateFull(beforeCursor, beforeCursor.length)

        // Create new line with prefix continuation (only if cursor was past the prefix)
        if (cursor >= prefix.length) {
            createNewLineWithPrefix(lineIndex, afterCursor, prefix)
        } else {
            // Cursor within prefix, don't continue prefix
            state.lines.add(lineIndex + 1, LineState(afterCursor, 0))
            state.focusedLineIndex = lineIndex + 1
            state.requestFocusUpdate()
            state.notifyChange()
        }
    }

    /**
     * Merge current line with previous line.
     */
    fun mergeToPreviousLine(lineIndex: Int) {
        Log.d(TAG, "mergeToPreviousLine: lineIndex=$lineIndex")

        if (lineIndex <= 0) return

        state.clearSelection()
        val currentLine = state.lines.getOrNull(lineIndex) ?: return
        val previousLine = state.lines.getOrNull(lineIndex - 1) ?: return

        val previousLength = previousLine.text.length
        previousLine.updateFull(previousLine.text + currentLine.text, previousLength)
        state.lines.removeAt(lineIndex)
        state.focusedLineIndex = lineIndex - 1
        state.requestFocusUpdate()
        state.notifyChange()
    }

    /**
     * Merge next line into current line.
     */
    fun mergeNextLine(lineIndex: Int) {
        Log.d(TAG, "mergeNextLine: lineIndex=$lineIndex")

        if (lineIndex >= state.lines.lastIndex) return

        state.clearSelection()
        val currentLine = state.lines.getOrNull(lineIndex) ?: return
        val nextLine = state.lines.getOrNull(lineIndex + 1) ?: return

        val currentLength = currentLine.text.length
        currentLine.updateFull(currentLine.text + nextLine.text, currentLength)
        state.lines.removeAt(lineIndex + 1)
        state.requestFocusUpdate()
        state.notifyChange()
    }

    // =========================================================================
    // Cursor Operations
    // =========================================================================

    /**
     * Set cursor position within a line.
     */
    fun setCursor(lineIndex: Int, position: Int) {
        Log.d(TAG, "setCursor: lineIndex=$lineIndex, position=$position")

        val line = state.lines.getOrNull(lineIndex) ?: return
        line.updateFull(line.text, position.coerceIn(0, line.text.length))
        state.focusedLineIndex = lineIndex
        state.clearSelection()
        state.requestFocusUpdate()
    }

    /**
     * Set cursor from global character offset.
     */
    fun setCursorFromGlobalOffset(globalOffset: Int) {
        Log.d(TAG, "setCursorFromGlobalOffset: globalOffset=$globalOffset")

        state.clearSelection()
        val (lineIndex, localOffset) = state.getLineAndLocalOffset(globalOffset)
        state.focusedLineIndex = lineIndex
        val line = state.lines.getOrNull(lineIndex) ?: return
        line.updateFull(line.text, localOffset)
        state.requestFocusUpdate()
    }

    // =========================================================================
    // Content Update (for composing text / IME sync)
    // =========================================================================

    /**
     * Update line content directly (used by IME for composing text).
     * This replaces the content portion while preserving the prefix.
     *
     * IMPORTANT: This is a direct content update from IME. Any selection should be
     * cleared first by the caller if needed. We don't try to be clever about
     * selection replacement here - that led to bugs.
     */
    fun updateLineContent(lineIndex: Int, newContent: String, contentCursor: Int) {
        Log.d(TAG, "updateLineContent: lineIndex=$lineIndex, content='$newContent', cursor=$contentCursor, " +
            "currentContent='${state.lines.getOrNull(lineIndex)?.content}'")

        val line = state.lines.getOrNull(lineIndex) ?: return

        // Check if this is a newline insertion
        if (newContent.contains('\n')) {
            state.clearSelection()
            val newlineIndex = newContent.indexOf('\n')
            val beforeNewline = newContent.substring(0, newlineIndex)
            val afterNewline = newContent.substring(newlineIndex + 1)

            // Update current line with content before newline
            line.updateContent(beforeNewline, beforeNewline.length)

            // Create new line with prefix continuation
            createNewLineWithPrefix(lineIndex, afterNewline, line.prefix)
            return
        }

        // Clear selection on any content update from IME
        // The old selection-replacement logic was causing bugs (like "Jg" → "Hg")
        // because it tried to extract inserted text and could corrupt content
        state.clearSelection()

        // Normal content update
        line.updateContent(newContent, contentCursor)
        state.notifyChange()
    }

    // =========================================================================
    // Focus Management
    // =========================================================================

    /**
     * Set focus to a specific line.
     */
    fun focusLine(lineIndex: Int) {
        if (lineIndex in state.lines.indices) {
            state.focusedLineIndex = lineIndex
            state.requestFocusUpdate()
        }
    }

    // =========================================================================
    // Selection Operations
    // =========================================================================

    /**
     * Check if there's an active selection.
     */
    fun hasSelection(): Boolean = state.hasSelection

    /**
     * Handles space key when there's a selection.
     * Single space indents, double-space (within 250ms) unindents.
     *
     * @return true if the space was handled (there was a selection), false otherwise
     */
    fun handleSpaceWithSelection(): Boolean {
        return state.handleSpaceWithSelection()
    }

    /**
     * Replace the current selection with text.
     * If there's no selection, this is a no-op.
     */
    fun replaceSelection(text: String) {
        if (state.hasSelection) {
            Log.d(TAG, "replaceSelection: text='$text'")
            state.replaceSelection(text)
        }
    }

    /**
     * Delete the current selection.
     */
    fun deleteSelection() {
        if (state.hasSelection) {
            Log.d(TAG, "deleteSelection")
            state.deleteSelection()
        }
    }

    // =========================================================================
    // Line Prefix Operations
    // =========================================================================

    /**
     * Toggle checkbox state on a specific line (checked ↔ unchecked).
     * Does not add or remove the checkbox, only toggles existing ones.
     */
    fun toggleCheckboxOnLine(lineIndex: Int) {
        Log.d(TAG, "toggleCheckboxOnLine: lineIndex=$lineIndex")
        val line = state.lines.getOrNull(lineIndex) ?: return
        line.toggleCheckboxState()
        state.requestFocusUpdate()
        state.notifyChange()
    }

    // =========================================================================
    // Read Access
    // =========================================================================

    /**
     * Get the current text of a line.
     */
    fun getLineText(lineIndex: Int): String = state.lines.getOrNull(lineIndex)?.text ?: ""

    /**
     * Get the content (without prefix) of a line.
     */
    fun getLineContent(lineIndex: Int): String = state.lines.getOrNull(lineIndex)?.content ?: ""

    /**
     * Get the cursor position within a line's content.
     */
    fun getContentCursor(lineIndex: Int): Int = state.lines.getOrNull(lineIndex)?.contentCursorPosition ?: 0

    /**
     * Get the full cursor position within a line.
     */
    fun getLineCursor(lineIndex: Int): Int = state.lines.getOrNull(lineIndex)?.cursorPosition ?: 0

    /**
     * Check if a line index is valid.
     */
    fun isValidLine(lineIndex: Int): Boolean = lineIndex in state.lines.indices
}

/**
 * Remember an EditorController for the given EditorState.
 */
@Composable
fun rememberEditorController(state: EditorState): EditorController {
    return remember(state) { EditorController(state) }
}
