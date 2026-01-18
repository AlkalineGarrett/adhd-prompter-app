package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Result of a selection action that modifies the text.
 */
data class SelectionActionResult(
    val newValue: TextFieldValue,
    val copiedText: String? = null
)

/**
 * Utilities for text selection operations like copy, cut, delete, etc.
 */
object SelectionActions {

    /**
     * Gets the currently selected text.
     */
    fun getSelectedText(value: TextFieldValue): String {
        return value.text.substring(value.selection.min, value.selection.max)
    }

    /**
     * Copies the selected text and returns it.
     */
    fun copy(value: TextFieldValue): String {
        return getSelectedText(value)
    }

    /**
     * Cuts the selected text, returning the new value and the cut text.
     * Also removes any empty line left behind after the cut.
     */
    fun cut(value: TextFieldValue): SelectionActionResult {
        val selectedText = getSelectedText(value)
        val newText = value.text.removeRange(value.selection.min, value.selection.max)
        var result = TextFieldValue(newText, TextRange(value.selection.min))

        // Remove empty line if one was left behind
        removeEmptyLineAfterDeletion(result)?.let { result = it }

        return SelectionActionResult(result, selectedText)
    }

    /**
     * Deletes the selected text.
     * Also removes any empty line left behind after the deletion.
     */
    fun delete(value: TextFieldValue): TextFieldValue {
        val newText = value.text.removeRange(value.selection.min, value.selection.max)
        var result = TextFieldValue(newText, TextRange(value.selection.min))

        // Remove empty line if one was left behind
        removeEmptyLineAfterDeletion(result)?.let { result = it }

        return result
    }

    /**
     * Selects all text.
     */
    fun selectAll(value: TextFieldValue): TextFieldValue {
        return value.copy(selection = TextRange(0, value.text.length))
    }

    /**
     * Collapses the selection to a cursor at the end of the selection.
     */
    fun unselect(value: TextFieldValue): TextFieldValue {
        return value.copy(selection = TextRange(value.selection.max))
    }

    /**
     * Inserts text at the cursor position (or replaces selected text).
     */
    fun insertText(value: TextFieldValue, textToInsert: String): TextFieldValue {
        val selStart = value.selection.min
        val selEnd = value.selection.max
        val newText = value.text.substring(0, selStart) + textToInsert + value.text.substring(selEnd)
        val newCursor = selStart + textToInsert.length
        return TextFieldValue(newText, TextRange(newCursor))
    }
}
