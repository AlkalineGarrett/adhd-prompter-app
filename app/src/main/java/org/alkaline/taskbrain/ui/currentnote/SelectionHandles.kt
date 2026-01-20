package org.alkaline.taskbrain.ui.currentnote

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity

private const val TAG = "SelectionHandles"

/**
 * Data class representing the position of a selection handle.
 */
data class HandlePosition(
    val offset: Offset,
    val lineHeight: Float
)

/**
 * Draws a teardrop-shaped selection handle.
 *
 * The shape is a circle with a triangular point at the top, creating the
 * classic teardrop selection handle appearance.
 *
 * @param isStartHandle If true, the handle points to the left (for start of selection).
 *                      If false, points to the right (for end of selection).
 */
@Composable
fun SelectionHandle(
    isStartHandle: Boolean,
    modifier: Modifier = Modifier,
    color: Color = EditorConfig.HandleColor,
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    val handleSize = EditorConfig.HandleSize
    val density = LocalDensity.current
    val handleSizePx = with(density) { handleSize.toPx() }

    // Create the teardrop path once and remember it
    val teardropPath = remember(handleSizePx) {
        createTeardropPath(handleSizePx)
    }

    // Use Box for touch handling, Canvas for drawing
    // This separates the touch target from the visual rendering
    Box(
        modifier = modifier
            .size(handleSize)
            .then(
                if (onDrag != null) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { onDragEnd?.invoke() },
                            onDragCancel = { onDragEnd?.invoke() }
                        ) { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        }
                    }
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.size(handleSize)) {
            // Flip horizontally for start handle (points left)
            // End handle points right (no flip)
            if (isStartHandle) {
                scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2, size.height / 2)) {
                    drawPath(teardropPath, color)
                }
            } else {
                drawPath(teardropPath, color)
            }
        }
    }
}

/**
 * Creates a teardrop-shaped path.
 *
 * The shape consists of:
 * - A circle at the bottom
 * - A triangular point at the top that connects to the circle
 *
 * The point of the teardrop is at the top-left corner of the bounding box.
 */
private fun createTeardropPath(size: Float): Path {
    val radius = size / 2f
    val path = Path()

    // The circle center is at (radius, radius) - center of the canvas
    // The point of the teardrop should be at the top-left

    // Start at the point (top-left corner)
    path.moveTo(0f, 0f)

    // Line to the top of the circle (tangent point)
    path.lineTo(radius, 0f)

    // Arc around the circle (270 degrees, from top going clockwise)
    path.arcTo(
        rect = Rect(0f, 0f, size, size),
        startAngleDegrees = 270f,
        sweepAngleDegrees = 270f,
        forceMoveTo = false
    )

    // Line back to the point
    path.lineTo(0f, 0f)

    path.close()
    return path
}

/**
 * Renders selection handles at the start and end of a selection.
 *
 * @param startPosition Position of the selection start (cursor rect from TextLayoutResult)
 * @param endPosition Position of the selection end
 * @param onStartHandleDrag Called when start handle is dragged
 * @param onEndHandleDrag Called when end handle is dragged
 */
@Composable
fun SelectionHandles(
    startPosition: HandlePosition?,
    endPosition: HandlePosition?,
    onStartHandleDrag: ((Offset) -> Unit)? = null,
    onEndHandleDrag: ((Offset) -> Unit)? = null,
    onStartHandleDragEnd: (() -> Unit)? = null,
    onEndHandleDragEnd: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleSize = EditorConfig.HandleSize
    val handleSizePx = with(density) { handleSize.toPx() }

    Log.d(TAG, "SelectionHandles: startPosition=$startPosition, endPosition=$endPosition")
    Log.d(TAG, "  handleSizePx=$handleSizePx")

    Box(modifier = modifier) {
        // Start handle - positioned so the point touches the selection start
        startPosition?.let { pos ->
            // Use Dp offset (not lambda IntOffset) so hit testing works at the drawn position
            val offsetXDp = with(density) { (pos.offset.x - handleSizePx).toDp() }
            val offsetYDp = with(density) { (pos.offset.y + pos.lineHeight).toDp() }
            Log.d(TAG, "  START handle offset: x=$offsetXDp, y=$offsetYDp (from pos.offset=${pos.offset}, lineHeight=${pos.lineHeight})")

            SelectionHandle(
                isStartHandle = true,
                onDrag = onStartHandleDrag,
                onDragEnd = onStartHandleDragEnd,
                modifier = Modifier.offset(x = offsetXDp, y = offsetYDp)
            )
        }

        // End handle - positioned so the point touches the selection end
        endPosition?.let { pos ->
            // Use Dp offset (not lambda IntOffset) so hit testing works at the drawn position
            val offsetXDp = with(density) { pos.offset.x.toDp() }
            val offsetYDp = with(density) { (pos.offset.y + pos.lineHeight).toDp() }
            Log.d(TAG, "  END handle offset: x=$offsetXDp, y=$offsetYDp (from pos.offset=${pos.offset}, lineHeight=${pos.lineHeight})")

            SelectionHandle(
                isStartHandle = false,
                onDrag = onEndHandleDrag,
                onDragEnd = onEndHandleDragEnd,
                modifier = Modifier.offset(x = offsetXDp, y = offsetYDp)
            )
        }
    }
}

/**
 * Calculates the handle position for a given global offset in the editor.
 *
 * @param globalOffset The character offset in the full text
 * @param state The editor state
 * @param lineLayouts Layout information for each line
 * @param forEndHandle If true, handles positioned at line boundaries will stay at end of previous line
 * @return HandlePosition with screen coordinates, or null if position cannot be determined
 */
internal fun calculateHandlePosition(
    globalOffset: Int,
    state: EditorState,
    lineLayouts: List<LineLayoutInfo>,
    forEndHandle: Boolean = false
): HandlePosition? {
    if (state.lines.isEmpty()) return null

    var (lineIndex, localOffset) = state.getLineAndLocalOffset(globalOffset)

    // For end handles at the start of a line (position 0), show at end of previous line instead
    // This keeps the handle visually at the newline character rather than jumping to next line
    if (forEndHandle && localOffset == 0 && lineIndex > 0) {
        lineIndex -= 1
        val prevLine = state.lines[lineIndex]
        localOffset = prevLine.text.length
    }

    val lineState = state.lines.getOrNull(lineIndex) ?: return null
    val layoutInfo = lineLayouts.getOrNull(lineIndex) ?: return null
    val textLayout = layoutInfo.textLayoutResult ?: return null

    // Convert to content-local offset (offset within the content, not including prefix)
    val prefixLength = lineState.prefix.length
    val contentOffset = (localOffset - prefixLength).coerceIn(0, lineState.content.length)

    // Get cursor rect from the text layout
    val cursorRect = try {
        textLayout.getCursorRect(contentOffset)
    } catch (e: Exception) {
        Log.e(TAG, "getCursorRect failed for contentOffset=$contentOffset", e)
        return null
    }

    // Calculate screen position
    // The cursor rect is relative to the content area, we need to add:
    // - prefixWidthPx for X (to account for the prefix box)
    // - line's yOffset for Y
    val screenX = cursorRect.left + layoutInfo.prefixWidthPx
    val screenY = layoutInfo.yOffset + cursorRect.top
    val lineHeight = cursorRect.bottom - cursorRect.top

    Log.d(TAG, "calculateHandlePosition: globalOffset=$globalOffset, lineIndex=$lineIndex, localOffset=$localOffset, forEndHandle=$forEndHandle")
    Log.d(TAG, "  prefixLength=$prefixLength, contentOffset=$contentOffset, content='${lineState.content}'")
    Log.d(TAG, "  cursorRect=$cursorRect, prefixWidthPx=${layoutInfo.prefixWidthPx}")
    Log.d(TAG, "  layoutInfo: yOffset=${layoutInfo.yOffset}, height=${layoutInfo.height}")
    Log.d(TAG, "  result: screenX=$screenX, screenY=$screenY, lineHeight=$lineHeight")

    return HandlePosition(
        offset = Offset(screenX, screenY),
        lineHeight = lineHeight
    )
}
