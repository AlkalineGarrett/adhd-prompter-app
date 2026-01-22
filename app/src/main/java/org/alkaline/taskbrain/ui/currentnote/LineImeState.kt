package org.alkaline.taskbrain.ui.currentnote

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * IME state for a single line that delegates all modifications to EditorController.
 *
 * This is a thin adapter between the Android IME and our EditorController.
 * It does NOT maintain its own copy of the text - instead it:
 * 1. Reads text from the controller when IME queries
 * 2. Forwards all modifications to the controller
 *
 * This ensures there's only ONE source of truth (EditorState via EditorController).
 */
class LineImeState(
    private val lineIndex: Int,
    private val controller: EditorController
) {
    // Composing region (temporary text during typing)
    var composingStart by mutableIntStateOf(-1)
        private set
    var composingEnd by mutableIntStateOf(-1)
        private set

    // IME-initiated selection (for spelling corrections, distinct from user selection)
    // This is NOT the same as user selection - it doesn't trigger indent-on-space behavior
    private var imeSelectionStart: Int = -1
    private var imeSelectionEnd: Int = -1

    // Cache for IME queries - updated from controller
    private var cachedContent: String = ""
    private var cachedCursor: Int = 0

    /**
     * Sync cache from controller. Call this before IME operations.
     */
    fun syncFromController() {
        cachedContent = controller.getLineContent(lineIndex)
        cachedCursor = controller.getContentCursor(lineIndex)
    }

    // Read access for IME
    val text: String get() = cachedContent
    val cursorPosition: Int get() = cachedCursor

    /**
     * Called when IME commits text (user finished typing a word).
     *
     * Per Android spec, commitText replaces the composing region with the committed text.
     * However, if the user presses Enter while composing, the IME might send "\n" as
     * commitText. In that case, we want to KEEP the composing text and add the newline.
     */
    fun commitText(commitText: String, newCursorOffset: Int) {
        // Always sync from controller first to ensure fresh data
        syncFromController()

        // Check for IME-initiated selection first (e.g., from spelling correction)
        // This bypasses the space-indent feature since it's not a user selection
        val hasImeSelection = imeSelectionStart >= 0 && imeSelectionEnd >= 0 && imeSelectionStart != imeSelectionEnd
        if (hasImeSelection) {
            val newContent = buildString {
                append(cachedContent.substring(0, imeSelectionStart.coerceIn(0, cachedContent.length)))
                append(commitText)
                append(cachedContent.substring(imeSelectionEnd.coerceIn(0, cachedContent.length)))
            }
            val newCursor = imeSelectionStart + commitText.length

            // Clear IME selection and composing
            imeSelectionStart = -1
            imeSelectionEnd = -1
            composingStart = -1
            composingEnd = -1

            controller.updateLineContent(lineIndex, newContent, newCursor)
            syncFromController()
            return
        }

        // If there's a user selection, handle specially
        if (controller.hasSelection()) {
            composingStart = -1
            composingEnd = -1

            // Space with selection: indent (or unindent on double-space)
            if (commitText == " ") {
                controller.handleSpaceWithSelection()
                syncFromController()
                return
            }

            // Other text: replace selection as normal (no undo - focus change handles it)
            controller.replaceSelectionNoUndo(commitText)
            syncFromController()
            return
        }

        val hasComposing = composingStart >= 0 && composingEnd >= 0

        // Special case: if committing a newline while composing, keep the composing text
        // and insert the newline at the end of the composing region
        if (hasComposing && commitText == "\n") {
            // Keep composing text, insert newline after it
            val newContent = buildString {
                append(cachedContent.substring(0, composingEnd.coerceIn(0, cachedContent.length)))
                append("\n")
                append(cachedContent.substring(composingEnd.coerceIn(0, cachedContent.length)))
            }
            val newCursor = composingEnd + 1

            composingStart = -1
            composingEnd = -1

            controller.updateLineContent(lineIndex, newContent, newCursor)
            syncFromController()
            return
        }

        // Standard case: replace composing region with committed text
        val baseContent = if (hasComposing) {
            cachedContent.substring(0, composingStart.coerceIn(0, cachedContent.length)) +
                cachedContent.substring(composingEnd.coerceIn(0, cachedContent.length))
        } else {
            cachedContent
        }
        val baseCursor = if (hasComposing) composingStart else cachedCursor

        // Build new content
        val newContent = buildString {
            append(baseContent.substring(0, baseCursor.coerceIn(0, baseContent.length)))
            append(commitText)
            append(baseContent.substring(baseCursor.coerceIn(0, baseContent.length)))
        }
        val newCursor = baseCursor + commitText.length

        // Clear composing state
        composingStart = -1
        composingEnd = -1

        // Update through controller
        controller.updateLineContent(lineIndex, newContent, newCursor)

        // Update cache
        syncFromController()
    }

    /**
     * Called when IME sets composing text (autocomplete suggestion).
     */
    fun setComposingText(composingText: String, newCursorOffset: Int) {
        // Sync first only if we don't have an active composing region
        // (if we do, we trust our tracked composing bounds)
        if (composingStart < 0) {
            syncFromController()
        }

        // If there's a selection, replace it with the composing text (no undo - focus change handles it)
        if (controller.hasSelection()) {
            controller.replaceSelectionNoUndo(composingText)
            syncFromController()
            // Set up composing region for the just-inserted text
            composingStart = cachedCursor - composingText.length
            composingEnd = cachedCursor
            return
        }

        val start = if (composingStart >= 0) composingStart else cachedCursor
        val end = if (composingEnd >= 0) composingEnd else cachedCursor

        val newContent = buildString {
            append(cachedContent.substring(0, start.coerceIn(0, cachedContent.length)))
            append(composingText)
            append(cachedContent.substring(end.coerceIn(0, cachedContent.length)))
        }

        composingStart = start
        composingEnd = start + composingText.length
        val newCursor = (composingEnd + newCursorOffset - 1).coerceIn(0, newContent.length)

        // Update through controller
        controller.updateLineContent(lineIndex, newContent, newCursor)

        // Update cache
        syncFromController()
    }

    /**
     * Called when IME finishes composition.
     */
    fun finishComposingText() {
        composingStart = -1
        composingEnd = -1
    }

    /**
     * Called when IME sets the composing region (e.g., for spelling correction).
     * This marks a range of existing text that will be replaced by the next commitText.
     */
    fun setComposingRegion(start: Int, end: Int) {
        syncFromController()
        composingStart = start.coerceIn(0, cachedContent.length)
        composingEnd = end.coerceIn(0, cachedContent.length)
    }

    /**
     * Called when IME deletes text around cursor.
     */
    fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        syncFromController()

        if (beforeLength == 0 && afterLength == 0) return

        // If there's a selection, delete it instead of surrounding text (no undo - focus change handles it)
        if (controller.hasSelection()) {
            controller.deleteSelectionNoUndo()
            syncFromController()
            return
        }

        // Backspace at start of content
        if (beforeLength > 0 && cachedCursor == 0) {
            controller.deleteBackward(lineIndex)
            syncFromController()
            return
        }

        // Delete forward at end
        if (afterLength > 0 && cachedCursor >= cachedContent.length) {
            controller.deleteForward(lineIndex)
            syncFromController()
            return
        }

        // Normal deletion within content
        val deleteStart = (cachedCursor - beforeLength).coerceAtLeast(0)
        val deleteEnd = (cachedCursor + afterLength).coerceAtMost(cachedContent.length)

        if (deleteStart < deleteEnd) {
            val newContent = cachedContent.removeRange(deleteStart, deleteEnd)
            composingStart = -1
            composingEnd = -1
            controller.updateLineContent(lineIndex, newContent, deleteStart)
            syncFromController()
        }
    }

    /**
     * Called when IME sets selection/cursor.
     * If start != end, this tracks a local IME selection for text replacement.
     * This is separate from user selection to avoid triggering indent-on-space behavior.
     */
    fun setSelection(start: Int, end: Int) {
        syncFromController()

        if (start == end) {
            // Just cursor positioning - convert to full line position and set cursor
            val prefix = controller.getLineText(lineIndex).let { text ->
                LineState.extractPrefix(text)
            }
            controller.setCursor(lineIndex, prefix.length + start.coerceIn(0, cachedContent.length))
            imeSelectionStart = -1
            imeSelectionEnd = -1
        } else {
            // IME is creating a selection for replacement (e.g., spelling correction)
            // Track locally - don't use editor selection to avoid indent-on-space
            imeSelectionStart = start.coerceIn(0, cachedContent.length)
            imeSelectionEnd = end.coerceIn(0, cachedContent.length)
        }
        syncFromController()
    }

    /**
     * Handle hardware key events (physical keyboard).
     */
    fun handleKeyEvent(keyEvent: KeyEvent): Boolean {
        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    deleteSurroundingText(1, 0)
                    return true
                }
                KeyEvent.KEYCODE_FORWARD_DEL -> {
                    deleteSurroundingText(0, 1)
                    return true
                }
            }
        }
        return false
    }
}
