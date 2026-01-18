package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

private const val BULLET = "• "
private const val ASTERISK_SPACE = "* "

/**
 * Transforms text to handle bullet list formatting:
 * - "* " at line start converts to "• "
 * - Enter after a bullet line adds a bullet to the new line
 * - Enter on an empty bullet line exits bullet mode
 * - Backspace at a bullet converts "• " back to "* "
 */
fun transformBulletText(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    var text = newValue.text
    var cursor = newValue.selection.start

    // Case 1: "* " at line start -> "• "
    val asteriskConverted = convertAsteriskToBullet(text)
    if (asteriskConverted != text) {
        text = asteriskConverted
    }

    // Case 2: Enter handling (add bullet or exit bullet mode)
    val isNewlineInserted = text.length == oldValue.text.length + 1 &&
            cursor > 0 &&
            text.getOrNull(cursor - 1) == '\n'

    if (isNewlineInserted) {
        val result = handleEnterOnBulletLine(text, cursor)
        text = result.first
        cursor = result.second
    }

    // Case 3: Backspace at bullet -> convert "• " back to "* "
    val isCharDeleted = text.length == oldValue.text.length - 1

    if (isCharDeleted) {
        val result = handleBackspaceOnBullet(text, cursor)
        text = result.first
        cursor = result.second
    }

    return TextFieldValue(text, TextRange(cursor))
}

private fun convertAsteriskToBullet(text: String): String {
    // Match "* " at the start of the string or after a newline
    return text.replace(Regex("(^|\\n)\\* ")) { match ->
        val prefix = if (match.value.startsWith("\n")) "\n" else ""
        "$prefix$BULLET"
    }
}

private fun handleEnterOnBulletLine(text: String, cursor: Int): Pair<String, Int> {
    val newlinePos = cursor - 1
    val prevLineStart = if (newlinePos == 0) 0 else text.lastIndexOf('\n', newlinePos - 1) + 1
    val prevLine = text.substring(prevLineStart, newlinePos)

    return when {
        // Empty bullet line - exit bullet mode (remove bullet and newline)
        prevLine == BULLET.trimEnd() || prevLine == BULLET -> {
            val newText = text.substring(0, prevLineStart) + text.substring(cursor)
            Pair(newText, prevLineStart)
        }
        // Bullet line with content - add bullet to new line
        prevLine.startsWith(BULLET) -> {
            val newText = text.substring(0, cursor) + BULLET + text.substring(cursor)
            Pair(newText, cursor + BULLET.length)
        }
        // Not a bullet line - no change
        else -> Pair(text, cursor)
    }
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
