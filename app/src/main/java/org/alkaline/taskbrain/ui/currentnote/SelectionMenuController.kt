package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Controller for managing text selection state and context menu display.
 * Extracts selection tracking logic from UI components.
 */
class SelectionMenuController {
    var previousSelection by mutableStateOf(TextRange.Zero)
        private set

    var skipNextRestore by mutableStateOf(false)

    var showContextMenu by mutableStateOf(false)
        private set

    var contextMenuOffset by mutableStateOf(Offset.Zero)
        private set

    /**
     * Syncs previousSelection when cursor moves (collapsed → collapsed).
     * Should be called when selection changes.
     * Returns true if sync was performed.
     */
    fun syncCursorPosition(currentSelection: TextRange): Boolean {
        if (currentSelection.collapsed && previousSelection.collapsed &&
            previousSelection != currentSelection) {
            previousSelection = currentSelection
            return true
        }
        return false
    }

    /**
     * Result of processing a selection change.
     */
    sealed class SelectionChangeResult {
        /** No special handling needed, just update previousSelection */
        data object PassThrough : SelectionChangeResult()

        /** Wait for finger up, then potentially show menu */
        data object WaitForGesture : SelectionChangeResult()

        /** Restore the previous selection (tap-in-selection) */
        data class RestoreSelection(val selectionToRestore: TextRange) : SelectionChangeResult()

        /** Skip this change (e.g., intentional unselect) */
        data object Skip : SelectionChangeResult()
    }

    /**
     * Analyzes a selection change and determines what action to take.
     * Call this when textFieldValue.selection changes.
     */
    fun analyzeSelectionChange(currentSelection: TextRange): SelectionChangeResult {
        val prevSel = previousSelection

        // Case 1 & 2: New or changed selection (non-collapsed)
        val isNewOrChangedSelection = !currentSelection.collapsed &&
            (prevSel.collapsed || currentSelection != prevSel)

        if (isNewOrChangedSelection) {
            return SelectionChangeResult.WaitForGesture
        }

        // Case 3: Selection just collapsed - check for tap-in-selection
        if (currentSelection.collapsed && !prevSel.collapsed) {
            if (skipNextRestore) {
                skipNextRestore = false
                previousSelection = currentSelection
                return SelectionChangeResult.Skip
            }

            val cursorPos = currentSelection.start
            if (cursorPos >= prevSel.min && cursorPos <= prevSel.max) {
                // Tap was inside the selection - restore it
                return SelectionChangeResult.RestoreSelection(prevSel)
            }
        }

        return SelectionChangeResult.PassThrough
    }

    /**
     * Waits for a gesture to complete (finger up) using the provided flow.
     * Returns true if we should show the menu after the gesture.
     */
    suspend fun waitForGestureComplete(
        isFingerDownFlow: StateFlow<Boolean>?,
        currentSelectionProvider: () -> TextRange
    ): Boolean {
        if (isFingerDownFlow == null) {
            return false
        }

        // If finger is already up, wait for next down->up cycle
        if (!isFingerDownFlow.value) {
            isFingerDownFlow.first { it }  // Wait for finger down
        }

        // Wait for finger to lift
        isFingerDownFlow.first { !it }

        // Check if we still have a selection
        return !currentSelectionProvider().collapsed
    }

    /**
     * Calculates and sets the context menu position based on selection.
     */
    fun calculateMenuPosition(textLayoutResult: TextLayoutResult?, selectionStart: Int) {
        textLayoutResult?.let { layout ->
            val startLine = layout.getLineForOffset(selectionStart)
            val lineRight = layout.getLineRight(startLine)
            val lineTop = layout.getLineTop(startLine)
            contextMenuOffset = Offset(lineRight + 16f, lineTop)
        }
    }

    /**
     * Shows the context menu at the calculated position.
     */
    fun showMenu() {
        showContextMenu = true
    }

    /**
     * Hides the context menu.
     */
    fun hideMenu() {
        showContextMenu = false
    }

    /**
     * Updates the previous selection to the current value.
     */
    fun updatePreviousSelection(currentSelection: TextRange) {
        previousSelection = currentSelection
    }

    /**
     * Marks that the next restore should be skipped (for intentional unselect/delete actions).
     */
    fun markSkipNextRestore() {
        skipNextRestore = true
    }
}

/**
 * Creates and remembers a SelectionMenuController instance.
 */
@Composable
fun rememberSelectionMenuController(): SelectionMenuController {
    return remember { SelectionMenuController() }
}
