package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

private const val BULLET = "• "
private const val ASTERISK_SPACE = "* "

private const val CHECKBOX_UNCHECKED = "☐ "
private const val CHECKBOX_CHECKED = "☑ "
private const val BRACKETS_EMPTY = "[]"
private const val BRACKETS_CHECKED = "[x]"

// All line prefixes that trigger continuation on Enter
private val LINE_PREFIXES = listOf(BULLET, CHECKBOX_UNCHECKED, CHECKBOX_CHECKED)

/**
 * Transforms text to handle bullet list and checkbox formatting:
 *
 * Bullets:
 * - "* " at line start converts to "• "
 * - Enter after a bullet line adds a bullet to the new line
 * - Enter on an empty bullet line exits bullet mode
 * - Backspace at a bullet converts "• " back to "* "
 *
 * Checkboxes:
 * - "[]" at line start converts to "☐ " (unchecked)
 * - "[x]" at line start converts to "☑ " (checked)
 * - Enter after a checkbox line adds an unchecked checkbox to the new line
 * - Enter on an empty checkbox line exits checkbox mode
 * - Backspace at a checkbox converts back to "[]" or "[x]"
 */
fun transformBulletText(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    var text = newValue.text
    var cursor = newValue.selection.start
    var wasTransformed = false

    // Track cursor adjustments from conversions
    var cursorAdjustment = 0

    // Case 1a: "* " at line start -> "• "
    val bulletResult = convertAsteriskToBullet(text, cursor)
    if (bulletResult.first != text) wasTransformed = true
    text = bulletResult.first
    cursorAdjustment += bulletResult.second

    // Case 1b: "[]" at line start -> "☐ "
    val uncheckedResult = convertBracketsToCheckbox(text, cursor + cursorAdjustment, BRACKETS_EMPTY, CHECKBOX_UNCHECKED)
    if (uncheckedResult.first != text) wasTransformed = true
    text = uncheckedResult.first
    cursorAdjustment += uncheckedResult.second

    // Case 1c: "[x]" at line start -> "☑ "
    val checkedResult = convertBracketsToCheckbox(text, cursor + cursorAdjustment, BRACKETS_CHECKED, CHECKBOX_CHECKED)
    if (checkedResult.first != text) wasTransformed = true
    text = checkedResult.first
    cursorAdjustment += checkedResult.second

    cursor += cursorAdjustment

    // Case 2: Enter handling (add prefix or exit list mode)
    val isNewlineInserted = text.length == oldValue.text.length + 1 + cursorAdjustment &&
            cursor > 0 &&
            text.getOrNull(cursor - 1) == '\n'

    if (isNewlineInserted) {
        val result = handleEnterOnPrefixedLine(text, cursor)
        if (result.first != text) wasTransformed = true
        text = result.first
        cursor = result.second
    }

    // Case 3: Backspace handling
    val isCharDeleted = text.length == oldValue.text.length - 1

    if (isCharDeleted) {
        val result = handleBackspaceOnPrefix(text, cursor)
        if (result.first != text) wasTransformed = true
        text = result.first
        cursor = result.second
    }

    // If no transformation occurred, preserve the original selection (important for text selection)
    if (!wasTransformed && text == newValue.text) {
        return newValue
    }

    return TextFieldValue(text, TextRange(cursor))
}

private fun convertAsteriskToBullet(text: String, cursor: Int): Pair<String, Int> {
    // Match "* " at the start of the string or after a newline, replace all occurrences
    val newText = text.replace(Regex("(^|\\n)\\* ")) { match ->
        val prefix = if (match.value.startsWith("\n")) "\n" else ""
        "$prefix$BULLET"
    }

    // No cursor adjustment needed (same length: "* " -> "• ")
    return Pair(newText, 0)
}

private fun convertBracketsToCheckbox(
    text: String,
    cursor: Int,
    brackets: String,
    checkbox: String
): Pair<String, Int> {
    // Match brackets at the start of the string or after a newline
    val escapedBrackets = Regex.escape(brackets)
    val regex = Regex("(^|\\n)$escapedBrackets")

    // Count matches to calculate cursor adjustment
    val matchCount = regex.findAll(text).count()
    if (matchCount == 0) return Pair(text, 0)

    val newText = text.replace(regex) { match ->
        val prefix = if (match.value.startsWith("\n")) "\n" else ""
        "$prefix$checkbox"
    }

    // Cursor adjustment per match: "[]" (2 chars) -> "☐ " (2 chars) = 0
    // Cursor adjustment per match: "[x]" (3 chars) -> "☑ " (2 chars) = -1
    val adjustmentPerMatch = checkbox.length - brackets.length
    val totalAdjustment = adjustmentPerMatch * matchCount

    return Pair(newText, totalAdjustment)
}

private fun handleEnterOnPrefixedLine(text: String, cursor: Int): Pair<String, Int> {
    val newlinePos = cursor - 1
    val prevLineStart = if (newlinePos == 0) 0 else text.lastIndexOf('\n', newlinePos - 1) + 1
    val prevLine = text.substring(prevLineStart, newlinePos)

    // Find which prefix the previous line has
    val matchedPrefix = LINE_PREFIXES.find { prevLine.startsWith(it) || prevLine == it.trimEnd() }
        ?: return Pair(text, cursor)

    val isEmpty = prevLine == matchedPrefix.trimEnd() || prevLine == matchedPrefix

    return if (isEmpty) {
        // Empty prefixed line - exit list mode (remove prefix and newline)
        val newText = text.substring(0, prevLineStart) + text.substring(cursor)
        Pair(newText, prevLineStart)
    } else {
        // Line with content - add prefix to new line
        // For checkboxes, always add unchecked checkbox
        val newPrefix = if (matchedPrefix == CHECKBOX_CHECKED) CHECKBOX_UNCHECKED else matchedPrefix
        val newText = text.substring(0, cursor) + newPrefix + text.substring(cursor)
        Pair(newText, cursor + newPrefix.length)
    }
}

private fun handleBackspaceOnPrefix(text: String, cursor: Int): Pair<String, Int> {
    // Check for bullet without space
    val bulletResult = handleBackspaceOnBullet(text, cursor)
    if (bulletResult.first != text) {
        return bulletResult
    }

    // Check for checkbox without space
    return handleBackspaceOnCheckbox(text, cursor)
}

private fun handleBackspaceOnBullet(text: String, cursor: Int): Pair<String, Int> {
    // Look for "•" at line start without a following space (space was just deleted)
    val bulletNoSpaceRegex = Regex("(^|\\n)•(?! )")
    val match = bulletNoSpaceRegex.find(text) ?: return Pair(text, cursor)

    // Calculate position of the bullet character
    val bulletPos = if (match.value.startsWith("\n")) match.range.first + 1 else match.range.first

    // Replace "•" with "* " (restore the asterisk and space)
    val newText = text.substring(0, bulletPos) + ASTERISK_SPACE + text.substring(bulletPos + 1)

    // Adjust cursor (we added one character)
    return Pair(newText, cursor + 1)
}

private fun handleBackspaceOnCheckbox(text: String, cursor: Int): Pair<String, Int> {
    // Look for "☐" at line start without a following space
    val uncheckedNoSpaceRegex = Regex("(^|\\n)☐(?! )")
    val uncheckedMatch = uncheckedNoSpaceRegex.find(text)
    if (uncheckedMatch != null) {
        val checkboxPos = if (uncheckedMatch.value.startsWith("\n")) uncheckedMatch.range.first + 1 else uncheckedMatch.range.first
        // Replace "☐" with "[]" (restore brackets, no space added since [] doesn't have trailing space in source)
        val newText = text.substring(0, checkboxPos) + BRACKETS_EMPTY + text.substring(checkboxPos + 1)
        // Cursor adjustment: "☐" (1 char) -> "[]" (2 chars) = +1
        return Pair(newText, cursor + 1)
    }

    // Look for "☑" at line start without a following space
    val checkedNoSpaceRegex = Regex("(^|\\n)☑(?! )")
    val checkedMatch = checkedNoSpaceRegex.find(text)
    if (checkedMatch != null) {
        val checkboxPos = if (checkedMatch.value.startsWith("\n")) checkedMatch.range.first + 1 else checkedMatch.range.first
        // Replace "☑" with "[x]" (restore brackets)
        val newText = text.substring(0, checkboxPos) + BRACKETS_CHECKED + text.substring(checkboxPos + 1)
        // Cursor adjustment: "☑" (1 char) -> "[x]" (3 chars) = +2
        return Pair(newText, cursor + 2)
    }

    return Pair(text, cursor)
}

/**
 * Handles checkbox tap/toggle behavior.
 * If the cursor moved to a checkbox position without text change, toggle the checkbox.
 *
 * @return The transformed TextFieldValue if a toggle occurred, null otherwise
 */
fun handleCheckboxTap(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue? {
    // Only handle if text is unchanged but cursor moved (indicates tap, not typing)
    if (oldValue.text != newValue.text) return null
    if (oldValue.selection == newValue.selection) return null

    // Don't interfere with text selection:
    // - If new selection is a range, user is selecting text
    // - If old selection was a range, user is finishing/manipulating a selection
    if (!newValue.selection.collapsed) return null
    if (!oldValue.selection.collapsed) return null

    val text = newValue.text
    val cursor = newValue.selection.start

    // Find the start of the current line
    val lineStart = if (cursor == 0) 0 else text.lastIndexOf('\n', cursor - 1) + 1

    // Check if this line starts with a checkbox and cursor is on it (position 0 or 1 of checkbox)
    val cursorOffsetInLine = cursor - lineStart

    // Cursor must be at position 0 or 1 (on the checkbox character or right after it)
    if (cursorOffsetInLine > 1) return null

    val lineText = text.substring(lineStart)

    return when {
        lineText.startsWith(CHECKBOX_UNCHECKED) -> {
            // Toggle unchecked -> checked
            val newText = text.substring(0, lineStart) + CHECKBOX_CHECKED + text.substring(lineStart + CHECKBOX_UNCHECKED.length)
            // Move cursor after the checkbox
            TextFieldValue(newText, TextRange(lineStart + CHECKBOX_CHECKED.length))
        }
        lineText.startsWith(CHECKBOX_CHECKED) -> {
            // Toggle checked -> unchecked
            val newText = text.substring(0, lineStart) + CHECKBOX_UNCHECKED + text.substring(lineStart + CHECKBOX_CHECKED.length)
            // Move cursor after the checkbox
            TextFieldValue(newText, TextRange(lineStart + CHECKBOX_UNCHECKED.length))
        }
        else -> null
    }
}
