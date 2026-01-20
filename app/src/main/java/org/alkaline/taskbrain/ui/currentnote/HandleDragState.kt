package org.alkaline.taskbrain.ui.currentnote

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

private const val TAG = "HandleDragState"

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
    // Start handle drag state
    private var startHandleDragAccumulator by mutableStateOf(Offset.Zero)
    private var startHandleDragStartPos by mutableStateOf<HandlePosition?>(null)
    private var startHandleFixedEnd by mutableIntStateOf(0)

    // End handle drag state
    private var endHandleDragAccumulator by mutableStateOf(Offset.Zero)
    private var endHandleDragStartPos by mutableStateOf<HandlePosition?>(null)
    private var endHandleFixedStart by mutableIntStateOf(0)

    /**
     * Updates selection based on handle drag delta.
     *
     * @param isStartHandle Whether dragging the start handle (true) or end handle (false)
     * @param dragDelta The delta movement from the drag gesture
     * @param state The editor state to update selection on
     * @param lineLayouts Layout info for position calculations
     */
    internal fun updateSelectionFromDrag(
        isStartHandle: Boolean,
        dragDelta: Offset,
        state: EditorState,
        lineLayouts: List<LineLayoutInfo>
    ) {
        if (!state.hasSelection) return

        Log.d(TAG, "updateSelectionFromDrag: isStartHandle=$isStartHandle, dragDelta=$dragDelta")

        if (isStartHandle) {
            updateStartHandleDrag(dragDelta, state, lineLayouts)
        } else {
            updateEndHandleDrag(dragDelta, state, lineLayouts)
        }
    }

    private fun updateStartHandleDrag(
        dragDelta: Offset,
        state: EditorState,
        lineLayouts: List<LineLayoutInfo>
    ) {
        // Initialize drag start state if needed
        if (startHandleDragStartPos == null) {
            startHandleDragStartPos = calculateHandlePosition(state.selection.start, state, lineLayouts)
            startHandleDragAccumulator = Offset.Zero
            startHandleFixedEnd = state.selection.end
            Log.d(TAG, "  Initialized start handle drag: startPos=${startHandleDragStartPos?.offset}, fixedEnd=$startHandleFixedEnd")
        }

        // Accumulate the delta
        startHandleDragAccumulator += dragDelta
        val startPos = startHandleDragStartPos ?: return

        // Calculate new position from original + accumulated delta
        val newScreenPos = Offset(
            startPos.offset.x + startHandleDragAccumulator.x,
            startPos.offset.y + startHandleDragAccumulator.y
        )
        Log.d(TAG, "  accumulated=$startHandleDragAccumulator, newScreenPos=$newScreenPos")

        val newGlobalOffset = positionToGlobalOffset(newScreenPos, state, lineLayouts)
        Log.d(TAG, "  newGlobalOffset=$newGlobalOffset, fixedEnd=$startHandleFixedEnd")
        state.setSelection(newGlobalOffset, startHandleFixedEnd)
    }

    private fun updateEndHandleDrag(
        dragDelta: Offset,
        state: EditorState,
        lineLayouts: List<LineLayoutInfo>
    ) {
        // Initialize drag start state if needed
        if (endHandleDragStartPos == null) {
            endHandleDragStartPos = calculateHandlePosition(state.selection.end, state, lineLayouts)
            endHandleDragAccumulator = Offset.Zero
            endHandleFixedStart = state.selection.start
            Log.d(TAG, "  Initialized end handle drag: startPos=${endHandleDragStartPos?.offset}, fixedStart=$endHandleFixedStart")
        }

        // Accumulate the delta
        endHandleDragAccumulator += dragDelta
        val startPos = endHandleDragStartPos ?: return

        // Calculate new position from original + accumulated delta
        val newScreenPos = Offset(
            startPos.offset.x + endHandleDragAccumulator.x,
            startPos.offset.y + endHandleDragAccumulator.y
        )
        Log.d(TAG, "  accumulated=$endHandleDragAccumulator, newScreenPos=$newScreenPos")

        val newGlobalOffset = positionToGlobalOffset(newScreenPos, state, lineLayouts)
        Log.d(TAG, "  newGlobalOffset=$newGlobalOffset, fixedStart=$endHandleFixedStart")
        state.setSelection(endHandleFixedStart, newGlobalOffset)
    }

    /**
     * Resets drag state when drag ends.
     *
     * @param isStartHandle Whether to reset start handle (true) or end handle (false)
     */
    fun resetDragState(isStartHandle: Boolean) {
        if (isStartHandle) {
            startHandleDragStartPos = null
            startHandleDragAccumulator = Offset.Zero
        } else {
            endHandleDragStartPos = null
            endHandleDragAccumulator = Offset.Zero
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
