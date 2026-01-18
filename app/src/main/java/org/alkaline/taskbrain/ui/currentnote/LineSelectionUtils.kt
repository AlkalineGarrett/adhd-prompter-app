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

    // Find line boundaries for the selection (or current line if collapsed)
    val firstLineStart = text.lastIndexOf('\n', selStart - 1) + 1
    val lastLineEnd = if (oldSelection.collapsed) {
        text.indexOf('\n', selStart).let { if (it == -1) text.length else it }
    } else {
        text.indexOf('\n', selEnd - 1).let { if (it == -1) text.length else it }
    }

    // Get all lines in selection
    val beforeSelection = text.substring(0, firstLineStart)
    val selectedLines = text.substring(firstLineStart, lastLineEnd)
    val afterSelection = text.substring(lastLineEnd)

    // Add tab to beginning of each line
    val lineCount = selectedLines.count { it == '\n' } + 1
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
        val newSelStart = firstLineStart
        val newSelEnd = firstLineStart + indentedLines.length
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

    // Find line boundaries for the selection (or current line if collapsed)
    val firstLineStart = text.lastIndexOf('\n', selStart - 1) + 1
    val lastLineEnd = if (oldSelection.collapsed) {
        text.indexOf('\n', selStart).let { if (it == -1) text.length else it }
    } else {
        text.indexOf('\n', selEnd - 1).let { if (it == -1) text.length else it }
    }

    // Get all lines in selection
    val beforeSelection = text.substring(0, firstLineStart)
    val selectedLines = text.substring(firstLineStart, lastLineEnd)
    val afterSelection = text.substring(lastLineEnd)

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
        val tabsRemovedBeforeCursor = if (text.getOrNull(firstLineStart) == '\t' && selStart > firstLineStart) 1 else 0
        val newCursor = (selStart - tabsRemovedBeforeCursor).coerceAtLeast(firstLineStart)
        TextFieldValue(newText, TextRange(newCursor))
    } else {
        // Update selection to cover unindented lines
        val newSelStart = firstLineStart
        val newSelEnd = firstLineStart + unindentedLines.length
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

    // Find if selection starts at beginning of a line
    val isAtLineStart = selStart == 0 || text.getOrNull(selStart - 1) == '\n'

    // Find if selection ends at end of a line (before newline or at text end)
    val isAtLineEnd = selEnd == text.length || text.getOrNull(selEnd) == '\n'

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

private const val BULLET_PREFIX = "• "
private const val CHECKBOX_UNCHECKED_PREFIX = "☐ "
private const val CHECKBOX_CHECKED_PREFIX = "☑ "

/**
 * Toggles bullet prefix on selected lines (or current line if selection is collapsed).
 * If some lines don't have bullets, adds bullets to those lines.
 * Only removes all bullets if every line already has a bullet.
 */
fun toggleBulletOnCurrentLine(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val selection = value.selection
    val selStart = selection.min
    val selEnd = selection.max

    // Find line boundaries for the selection (or current line if collapsed)
    val firstLineStart = text.lastIndexOf('\n', selStart - 1) + 1
    val lastLineEnd = if (selection.collapsed) {
        text.indexOf('\n', selStart).let { if (it == -1) text.length else it }
    } else {
        text.indexOf('\n', selEnd - 1).let { if (it == -1) text.length else it }
    }

    // Get all lines in selection
    val beforeSelection = text.substring(0, firstLineStart)
    val selectedLines = text.substring(firstLineStart, lastLineEnd)
    val afterSelection = text.substring(lastLineEnd)

    val lines = selectedLines.split('\n')

    // Check if all lines already have bullets
    val allHaveBullets = lines.all { line ->
        line.trimStart('\t').startsWith(BULLET_PREFIX)
    }

    // Process each line
    val processedLines = lines.map { line ->
        val indentation = line.takeWhile { it == '\t' }
        val lineContent = line.removePrefix(indentation)

        if (allHaveBullets) {
            // Remove bullets from all lines
            indentation + lineContent.removePrefix(BULLET_PREFIX)
        } else {
            // Add bullets to lines that don't have them
            when {
                lineContent.startsWith(BULLET_PREFIX) -> line // Already has bullet, keep as-is
                lineContent.startsWith(CHECKBOX_UNCHECKED_PREFIX) -> {
                    indentation + BULLET_PREFIX + lineContent.removePrefix(CHECKBOX_UNCHECKED_PREFIX)
                }
                lineContent.startsWith(CHECKBOX_CHECKED_PREFIX) -> {
                    indentation + BULLET_PREFIX + lineContent.removePrefix(CHECKBOX_CHECKED_PREFIX)
                }
                else -> indentation + BULLET_PREFIX + lineContent
            }
        }
    }.joinToString("\n")

    val newText = beforeSelection + processedLines + afterSelection

    return if (selection.collapsed) {
        // For collapsed selection, adjust cursor based on first line's change
        val firstLineContent = lines.first().trimStart('\t')
        val firstLineAdjustment = if (allHaveBullets) {
            -BULLET_PREFIX.length
        } else {
            when {
                firstLineContent.startsWith(BULLET_PREFIX) -> 0
                firstLineContent.startsWith(CHECKBOX_UNCHECKED_PREFIX) -> BULLET_PREFIX.length - CHECKBOX_UNCHECKED_PREFIX.length
                firstLineContent.startsWith(CHECKBOX_CHECKED_PREFIX) -> BULLET_PREFIX.length - CHECKBOX_CHECKED_PREFIX.length
                else -> BULLET_PREFIX.length
            }
        }
        val newCursor = (selStart + firstLineAdjustment).coerceAtLeast(firstLineStart)
        TextFieldValue(newText, TextRange(newCursor))
    } else {
        // Expand selection to cover all processed lines
        TextFieldValue(newText, TextRange(firstLineStart, firstLineStart + processedLines.length))
    }
}

/**
 * Toggles checkbox prefix on selected lines (or current line if selection is collapsed).
 * If some lines don't have checkboxes, adds checkboxes to those lines.
 * Only removes all checkboxes if every line already has a checkbox (checked or unchecked).
 */
fun toggleCheckboxOnCurrentLine(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val selection = value.selection
    val selStart = selection.min
    val selEnd = selection.max

    // Find line boundaries for the selection (or current line if collapsed)
    val firstLineStart = text.lastIndexOf('\n', selStart - 1) + 1
    val lastLineEnd = if (selection.collapsed) {
        text.indexOf('\n', selStart).let { if (it == -1) text.length else it }
    } else {
        text.indexOf('\n', selEnd - 1).let { if (it == -1) text.length else it }
    }

    // Get all lines in selection
    val beforeSelection = text.substring(0, firstLineStart)
    val selectedLines = text.substring(firstLineStart, lastLineEnd)
    val afterSelection = text.substring(lastLineEnd)

    val lines = selectedLines.split('\n')

    // Check if all lines already have checkboxes (checked or unchecked)
    val allHaveCheckboxes = lines.all { line ->
        val content = line.trimStart('\t')
        content.startsWith(CHECKBOX_UNCHECKED_PREFIX) || content.startsWith(CHECKBOX_CHECKED_PREFIX)
    }

    // Process each line
    val processedLines = lines.map { line ->
        val indentation = line.takeWhile { it == '\t' }
        val lineContent = line.removePrefix(indentation)

        if (allHaveCheckboxes) {
            // Remove checkboxes from all lines
            when {
                lineContent.startsWith(CHECKBOX_UNCHECKED_PREFIX) -> {
                    indentation + lineContent.removePrefix(CHECKBOX_UNCHECKED_PREFIX)
                }
                lineContent.startsWith(CHECKBOX_CHECKED_PREFIX) -> {
                    indentation + lineContent.removePrefix(CHECKBOX_CHECKED_PREFIX)
                }
                else -> line
            }
        } else {
            // Add checkboxes to lines that don't have them
            when {
                lineContent.startsWith(CHECKBOX_UNCHECKED_PREFIX) -> line // Already has checkbox, keep as-is
                lineContent.startsWith(CHECKBOX_CHECKED_PREFIX) -> line // Already has checkbox, keep as-is
                lineContent.startsWith(BULLET_PREFIX) -> {
                    indentation + CHECKBOX_UNCHECKED_PREFIX + lineContent.removePrefix(BULLET_PREFIX)
                }
                else -> indentation + CHECKBOX_UNCHECKED_PREFIX + lineContent
            }
        }
    }.joinToString("\n")

    val newText = beforeSelection + processedLines + afterSelection

    return if (selection.collapsed) {
        // For collapsed selection, adjust cursor based on first line's change
        val firstLineContent = lines.first().trimStart('\t')
        val firstLineAdjustment = if (allHaveCheckboxes) {
            when {
                firstLineContent.startsWith(CHECKBOX_UNCHECKED_PREFIX) -> -CHECKBOX_UNCHECKED_PREFIX.length
                firstLineContent.startsWith(CHECKBOX_CHECKED_PREFIX) -> -CHECKBOX_CHECKED_PREFIX.length
                else -> 0
            }
        } else {
            when {
                firstLineContent.startsWith(CHECKBOX_UNCHECKED_PREFIX) -> 0
                firstLineContent.startsWith(CHECKBOX_CHECKED_PREFIX) -> 0
                firstLineContent.startsWith(BULLET_PREFIX) -> CHECKBOX_UNCHECKED_PREFIX.length - BULLET_PREFIX.length
                else -> CHECKBOX_UNCHECKED_PREFIX.length
            }
        }
        val newCursor = (selStart + firstLineAdjustment).coerceAtLeast(firstLineStart)
        TextFieldValue(newText, TextRange(newCursor))
    } else {
        // Expand selection to cover all processed lines
        TextFieldValue(newText, TextRange(firstLineStart, firstLineStart + processedLines.length))
    }
}

