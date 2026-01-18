package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Checks if the change from oldValue to newValue represents a space replacing a selection.
 */
fun isSpaceReplacingSelection(oldValue: TextFieldValue, newValue: TextFieldValue): Boolean {
    val oldSelection = oldValue.selection
    if (oldSelection.collapsed) return false

    val selStart = oldSelection.min
    val selEnd = oldSelection.max

    val expectedText = oldValue.text.substring(0, selStart) + " " + oldValue.text.substring(selEnd)
    return newValue.text == expectedText
}

/**
 * Indents selected lines by adding a tab to the start of each line.
 * If selection is collapsed (cursor only), indents the current line.
 * Returns the new TextFieldValue with indented lines and updated selection.
 */
fun handleSelectionIndent(oldValue: TextFieldValue): TextFieldValue {
    val oldSelection = oldValue.selection
    val text = oldValue.text
    val selStart = oldSelection.min
    val selEnd = oldSelection.max

    // Get line boundaries using TextLineUtils
    val bounds = TextLineUtils.getSelectionLineBounds(text, selStart, selEnd)

    // Get all lines in selection
    val beforeSelection = text.substring(0, bounds.start)
    val selectedLines = text.substring(bounds.start, bounds.end)
    val afterSelection = text.substring(bounds.end)

    // Add tab to beginning of each line
    val indentedLines = selectedLines.split('\n').joinToString("\n") { line ->
        "\t$line"
    }

    val newText = beforeSelection + indentedLines + afterSelection

    // Calculate new selection/cursor position
    return if (oldSelection.collapsed) {
        // Move cursor by 1 (the added tab)
        TextFieldValue(newText, TextRange(selStart + 1))
    } else {
        // Expand selection to cover all indented lines
        val newSelStart = bounds.start
        val newSelEnd = bounds.start + indentedLines.length
        TextFieldValue(newText, TextRange(newSelStart, newSelEnd))
    }
}

/**
 * Handles unindent of selected lines (removes one tab from start of each line).
 * If selection is collapsed (cursor only), unindents the current line.
 * Returns the new TextFieldValue, or null if no tabs to remove.
 */
fun handleSelectionUnindent(oldValue: TextFieldValue): TextFieldValue? {
    val oldSelection = oldValue.selection
    val text = oldValue.text
    val selStart = oldSelection.min
    val selEnd = oldSelection.max

    // Get line boundaries using TextLineUtils
    val bounds = TextLineUtils.getSelectionLineBounds(text, selStart, selEnd)

    // Get all lines in selection
    val beforeSelection = text.substring(0, bounds.start)
    val selectedLines = text.substring(bounds.start, bounds.end)
    val afterSelection = text.substring(bounds.end)

    // Remove one tab from beginning of each line (if present)
    val unindentedLines = selectedLines.split('\n').joinToString("\n") { line ->
        if (line.startsWith("\t")) line.substring(1) else line
    }

    // Check if anything changed
    if (unindentedLines == selectedLines) return null

    val newText = beforeSelection + unindentedLines + afterSelection

    // Calculate new selection/cursor position
    return if (oldSelection.collapsed) {
        // Move cursor back by 1 if we removed a tab before the cursor
        val tabsRemovedBeforeCursor = if (text.getOrNull(bounds.start) == '\t' && selStart > bounds.start) 1 else 0
        val newCursor = (selStart - tabsRemovedBeforeCursor).coerceAtLeast(bounds.start)
        TextFieldValue(newText, TextRange(newCursor))
    } else {
        // Update selection to cover unindented lines
        val newSelStart = bounds.start
        val newSelEnd = bounds.start + unindentedLines.length
        TextFieldValue(newText, TextRange(newSelStart, newSelEnd))
    }
}

/**
 * Handles deletion when full line(s) are selected.
 * When the selection covers complete lines and the user deletes, also remove the newline.
 * Returns the new TextFieldValue if handled, null otherwise.
 */
fun handleFullLineDelete(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue? {
    val oldSelection = oldValue.selection
    if (oldSelection.collapsed) return null

    val deletedLength = oldSelection.length - (newValue.text.length - (oldValue.text.length - oldSelection.length))

    // Check if the selected text was deleted (not replaced with other text)
    if (deletedLength != oldSelection.length) return null

    // Check if the selection covers complete line(s)
    val text = oldValue.text
    val selStart = oldSelection.min
    val selEnd = oldSelection.max

    // Use TextLineUtils to check if selection is at line boundaries
    val isAtLineStart = TextLineUtils.isAtLineStart(text, selStart)
    val isAtLineEnd = TextLineUtils.isAtLineEnd(text, selEnd)

    if (!isAtLineStart || !isAtLineEnd) return null

    // This is a full line deletion - we need to also remove the trailing newline if present
    val hasTrailingNewline = selEnd < text.length && text[selEnd] == '\n'
    val hasLeadingNewline = selStart > 0 && text[selStart - 1] == '\n'

    val newText: String
    val newCursor: Int

    if (hasTrailingNewline) {
        // Remove the line content plus the trailing newline
        newText = text.substring(0, selStart) + text.substring(selEnd + 1)
        newCursor = selStart
    } else if (hasLeadingNewline && selStart > 0) {
        // Last line - remove the leading newline instead
        newText = text.substring(0, selStart - 1) + text.substring(selEnd)
        newCursor = selStart - 1
    } else {
        // Only line or already handled by normal deletion
        return null
    }

    return TextFieldValue(newText, TextRange(newCursor))
}

/**
 * Gets the text selection range for a single line by index.
 */
fun getLineSelection(text: String, lineIndex: Int): TextRange {
    val lines = text.split('\n')
    if (lineIndex < 0 || lineIndex >= lines.size) {
        return TextRange(text.length)
    }

    var startOffset = 0
    for (i in 0 until lineIndex) {
        startOffset += lines[i].length + 1 // +1 for newline
    }

    val endOffset = startOffset + lines[lineIndex].length
    return TextRange(startOffset, endOffset)
}

/**
 * Gets the text selection range spanning multiple lines.
 */
fun getMultiLineSelection(text: String, startLine: Int, endLine: Int): TextRange {
    val lines = text.split('\n')
    if (startLine < 0 || endLine >= lines.size) {
        return TextRange(0, text.length)
    }

    var startOffset = 0
    for (i in 0 until startLine) {
        startOffset += lines[i].length + 1
    }

    var endOffset = 0
    for (i in 0..endLine) {
        endOffset += lines[i].length + (if (i < lines.size - 1) 1 else 0)
    }

    return TextRange(startOffset, endOffset)
}

// Use constants from LinePrefixes
private val BULLET_PREFIX = LinePrefixes.BULLET
private val CHECKBOX_UNCHECKED_PREFIX = LinePrefixes.CHECKBOX_UNCHECKED
private val CHECKBOX_CHECKED_PREFIX = LinePrefixes.CHECKBOX_CHECKED

/**
 * Configuration for a prefix toggle operation.
 */
private data class ToggleConfig(
    val targetPrefix: String,
    val hasPrefix: (String) -> Boolean,
    val relatedPrefixes: List<String> = emptyList()
)

/**
 * Generic function to toggle a prefix on selected lines.
 * If some lines don't have the target prefix, adds it to those lines.
 * Only removes all prefixes if every line already has the target prefix (or related prefixes).
 */
private fun togglePrefixOnLines(
    value: TextFieldValue,
    config: ToggleConfig
): TextFieldValue {
    val text = value.text
    val selection = value.selection
    val selStart = selection.min
    val selEnd = selection.max

    val bounds = TextLineUtils.getSelectionLineBounds(text, selStart, selEnd)

    val beforeSelection = text.substring(0, bounds.start)
    val selectedLines = text.substring(bounds.start, bounds.end)
    val afterSelection = text.substring(bounds.end)

    val lines = selectedLines.split('\n')

    // Check if all lines already have the target prefix (or related prefixes)
    val allHavePrefix = lines.all { config.hasPrefix(it) }

    // Process each line
    val processedLines = lines.map { line ->
        val lineInfo = TextLineUtils.parseLine(line)

        if (allHavePrefix) {
            // Remove prefix from all lines
            lineInfo.indentation + lineInfo.content
        } else {
            // Add prefix to lines that don't have it
            if (config.hasPrefix(line)) {
                line // Already has target prefix, keep as-is
            } else {
                LinePrefixes.addPrefix(line, config.targetPrefix)
            }
        }
    }.joinToString("\n")

    val newText = beforeSelection + processedLines + afterSelection

    return if (selection.collapsed) {
        val firstLineInfo = TextLineUtils.parseLine(lines.first())
        val firstLineAdjustment = calculateCursorAdjustment(
            allHavePrefix,
            firstLineInfo.prefix,
            config.targetPrefix,
            config.relatedPrefixes
        )
        val newCursor = (selStart + firstLineAdjustment).coerceAtLeast(bounds.start)
        TextFieldValue(newText, TextRange(newCursor))
    } else {
        TextFieldValue(newText, TextRange(bounds.start, bounds.start + processedLines.length))
    }
}

/**
 * Calculates cursor adjustment when toggling prefixes.
 */
private fun calculateCursorAdjustment(
    wasRemoved: Boolean,
    existingPrefix: String?,
    targetPrefix: String,
    relatedPrefixes: List<String>
): Int {
    return if (wasRemoved) {
        // Prefix was removed
        -(existingPrefix?.length ?: 0)
    } else {
        // Prefix was added or replaced
        when (existingPrefix) {
            targetPrefix -> 0
            in relatedPrefixes -> 0
            null -> targetPrefix.length
            else -> targetPrefix.length - existingPrefix.length
        }
    }
}

/**
 * Toggles bullet prefix on selected lines (or current line if selection is collapsed).
 * If some lines don't have bullets, adds bullets to those lines.
 * Only removes all bullets if every line already has a bullet.
 */
fun toggleBulletOnCurrentLine(value: TextFieldValue): TextFieldValue {
    return togglePrefixOnLines(
        value,
        ToggleConfig(
            targetPrefix = BULLET_PREFIX,
            hasPrefix = { LinePrefixes.hasBullet(it) }
        )
    )
}

/**
 * Toggles checkbox prefix on selected lines (or current line if selection is collapsed).
 * If some lines don't have checkboxes, adds checkboxes to those lines.
 * Only removes all checkboxes if every line already has a checkbox (checked or unchecked).
 */
fun toggleCheckboxOnCurrentLine(value: TextFieldValue): TextFieldValue {
    return togglePrefixOnLines(
        value,
        ToggleConfig(
            targetPrefix = CHECKBOX_UNCHECKED_PREFIX,
            hasPrefix = { LinePrefixes.hasCheckbox(it) },
            relatedPrefixes = listOf(CHECKBOX_CHECKED_PREFIX)
        )
    )
}

/**
 * Removes an empty line after a deletion operation.
 * If the cursor is at the start of a line and that line is empty,
 * removes the empty line and keeps the cursor at the same position.
 * Returns the new TextFieldValue if an empty line was removed, null otherwise.
 */
fun removeEmptyLineAfterDeletion(value: TextFieldValue): TextFieldValue? {
    val text = value.text
    val cursor = value.selection.start

    // Only handle collapsed selections
    if (!value.selection.collapsed) return null

    // Check if cursor is at start of a line
    if (!TextLineUtils.isAtLineStart(text, cursor)) return null

    // Check if the current line is empty (cursor is right before newline or at end)
    if (cursor >= text.length) return null
    if (text[cursor] != '\n') return null

    // Check if there's a previous line (we need to remove the newline before cursor)
    if (cursor == 0) return null

    // Remove the newline before the cursor (which removes the empty line)
    val newText = text.substring(0, cursor - 1) + text.substring(cursor)
    val newCursor = cursor - 1

    return TextFieldValue(newText, TextRange(newCursor))
}

