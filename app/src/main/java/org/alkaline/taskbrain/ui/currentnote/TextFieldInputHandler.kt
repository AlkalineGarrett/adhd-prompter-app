package org.alkaline.taskbrain.ui.currentnote

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Handles text field input changes, including:
 * - Space-to-indent conversion when replacing selection
 * - Full line deletion handling
 * - Checkbox tap detection
 * - Bullet/checkbox text transformations
 * - Paste transformation for HTML lists
 */
object TextFieldInputHandler {

    /**
     * Result of processing a text field input change.
     */
    data class InputResult(
        val value: TextFieldValue,
        val handled: Boolean = true
    )

    /**
     * Processes a text field value change, applying all necessary transformations.
     *
     * @param context Android context for clipboard access
     * @param oldValue The previous TextFieldValue
     * @param newValue The new TextFieldValue from the text field
     * @param lastIndentTime Time of the last indent operation (for double-space unindent)
     * @param onIndentTimeUpdate Callback to update the last indent time
     * @return The processed InputResult
     */
    fun processValueChange(
        context: Context,
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
        lastIndentTime: Long,
        onIndentTimeUpdate: (Long) -> Unit
    ): InputResult {
        // Check if this is space with selection -> indent/unindent
        if (isSpaceReplacingSelection(oldValue, newValue)) {
            val result = handleSpaceIndent(oldValue, lastIndentTime, onIndentTimeUpdate)
            return InputResult(result, handled = true)
        }

        // Check if this is a deletion of fully selected line(s)
        val lineDeleteResult = handleFullLineDelete(oldValue, newValue)
        if (lineDeleteResult != null) {
            return InputResult(lineDeleteResult, handled = true)
        }

        // Check for checkbox tap
        val checkboxTapResult = handleCheckboxTap(oldValue, newValue)
        if (checkboxTapResult != null) {
            return InputResult(checkboxTapResult, handled = true)
        }

        // Apply bullet/checkbox text transformations
        var transformed = transformBulletText(oldValue, newValue)

        // Apply paste transformation for HTML lists
        transformed = handlePasteTransformation(context, oldValue, transformed)

        return InputResult(transformed, handled = true)
    }

    /**
     * Handles space key when there's a selection - converts to indent/unindent.
     */
    private fun handleSpaceIndent(
        oldValue: TextFieldValue,
        lastIndentTime: Long,
        onIndentTimeUpdate: (Long) -> Unit
    ): TextFieldValue {
        val currentTime = System.currentTimeMillis()
        val isDoubleSpace = currentTime - lastIndentTime < 250
        onIndentTimeUpdate(currentTime)

        return if (isDoubleSpace) {
            // Double-space: unindent from original state
            // First unindent undoes the indent we just did, second actually unindents
            val firstUnindent = handleSelectionUnindent(oldValue)
            if (firstUnindent != null) {
                handleSelectionUnindent(firstUnindent) ?: firstUnindent
            } else {
                // Can't unindent - keep current state
                oldValue
            }
        } else {
            // Single space - indent
            handleSelectionIndent(oldValue)
        }
    }
}
