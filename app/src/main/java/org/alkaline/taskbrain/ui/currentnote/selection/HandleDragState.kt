package org.alkaline.taskbrain.ui.currentnote.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import org.alkaline.taskbrain.dsl.DirectiveResult
import org.alkaline.taskbrain.ui.currentnote.EditorState
import org.alkaline.taskbrain.ui.currentnote.gestures.LineLayoutInfo
import org.alkaline.taskbrain.ui.currentnote.gestures.positionToGlobalOffset

/**
 * Tracks drag state for a single selection handle.
 */
private class SingleHandleDragState {
    var accumulator by mutableStateOf(Offset.Zero)
        private set
    var startPosition by mutableStateOf<HandlePosition?>(null)
        private set
    var fixedOffset by mutableIntStateOf(0)
        private set

    val isActive: Boolean get() = startPosition != null

    fun initialize(position: HandlePosition?, fixedSelectionOffset: Int) {
        startPosition = position
        accumulator = Offset.Zero
        fixedOffset = fixedSelectionOffset
    }

    fun addDelta(delta: Offset) {
        accumulator += delta
    }

    fun reset() {
        startPosition = null
        accumulator = Offset.Zero
    }

    fun calculateNewScreenPos(): Offset? {
        val pos = startPosition ?: return null
        return Offset(
            pos.offset.x + accumulator.x,
            pos.offset.y + accumulator.y
        )
    }
}

/**
 * Manages the state for selection handle dragging.
 *
 * Handles the accumulated delta approach for smooth handle dragging:
 * - Tracks the original position when drag starts
 * - Accumulates delta movements during drag
 * - Calculates new position from original + accumulated delta
 * - Keeps the opposite end of selection fixed during drag
 */
class HandleDragState {
    private val startHandle = SingleHandleDragState()
    private val endHandle = SingleHandleDragState()

    /**
     * Updates selection based on handle drag delta.
     *
     * @param isStartHandle Whether dragging the start handle (true) or end handle (false)
     * @param dragDelta The delta movement from the drag gesture
     * @param state The editor state to update selection on
     * @param lineLayouts Layout info for position calculations
     * @param directiveResults Map of directive results for display/source coordinate mapping
     */
    internal fun updateSelectionFromDrag(
        isStartHandle: Boolean,
        dragDelta: Offset,
        state: EditorState,
        lineLayouts: List<LineLayoutInfo>,
        directiveResults: Map<String, DirectiveResult> = emptyMap()
    ) {
        if (!state.hasSelection) return

        val handle = if (isStartHandle) startHandle else endHandle

        // Initialize drag state if needed
        if (!handle.isActive) {
            val selectionOffset = if (isStartHandle) state.selection.start else state.selection.end
            val fixedOffset = if (isStartHandle) state.selection.end else state.selection.start
            handle.initialize(
                calculateHandlePosition(selectionOffset, state, lineLayouts, directiveResults = directiveResults),
                fixedOffset
            )
        }

        // Accumulate and calculate new position
        handle.addDelta(dragDelta)
        val newScreenPos = handle.calculateNewScreenPos() ?: return

        val newGlobalOffset = positionToGlobalOffset(newScreenPos, state, lineLayouts, directiveResults)

        // Update selection: dragged handle moves, other end stays fixed
        if (isStartHandle) {
            state.setSelection(newGlobalOffset, handle.fixedOffset)
        } else {
            state.setSelection(handle.fixedOffset, newGlobalOffset)
        }
    }

    /**
     * Resets drag state when drag ends.
     *
     * @param isStartHandle Whether to reset start handle (true) or end handle (false)
     */
    fun resetDragState(isStartHandle: Boolean) {
        if (isStartHandle) {
            startHandle.reset()
        } else {
            endHandle.reset()
        }
    }
}

/**
 * Creates and remembers a HandleDragState instance.
 */
@Composable
fun rememberHandleDragState(): HandleDragState {
    return remember { HandleDragState() }
}
