package org.alkaline.taskbrain.ui.currentnote.rendering

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.alkaline.taskbrain.dsl.DirectiveFinder
import org.alkaline.taskbrain.dsl.DirectiveResult
import org.alkaline.taskbrain.ui.currentnote.EditorConfig
import org.alkaline.taskbrain.ui.currentnote.EditorState
import org.alkaline.taskbrain.ui.currentnote.gestures.findLineIndexAtY
import org.alkaline.taskbrain.ui.currentnote.gestures.LineLayoutInfo

// Layout constants for directive edit row gaps
private val DirectiveEditRowGapHeight = 40.dp
private val DefaultLineHeight = 24.dp

// =============================================================================
// Gutter Gesture Handling
// =============================================================================

/**
 * Callbacks for gutter gesture events.
 */
internal class GutterGestureCallbacks(
    val onLineSelected: (Int) -> Unit,
    val onLineDragStart: (Int) -> Unit,
    val onLineDragUpdate: (Int) -> Unit,
    val onLineDragEnd: () -> Unit
)

/**
 * Tracks state for a gutter drag gesture.
 */
private class GutterGestureTracker(
    private val startLineIndex: Int,
    private val callbacks: GutterGestureCallbacks
) {
    private var isDragging = false
    private var currentLineIndex = startLineIndex

    fun start() {
        callbacks.onLineDragStart(startLineIndex)
    }

    fun onLineChanged(newLineIndex: Int) {
        if (newLineIndex != currentLineIndex) {
            isDragging = true
            currentLineIndex = newLineIndex
            callbacks.onLineDragUpdate(currentLineIndex)
        }
    }

    fun complete() {
        if (!isDragging) {
            callbacks.onLineSelected(startLineIndex)
        }
        callbacks.onLineDragEnd()
    }
}

/**
 * Modifier that handles gutter tap and drag gestures.
 */
internal fun Modifier.gutterPointerInput(
    lineLayouts: List<LineLayoutInfo>,
    lineCount: Int,
    defaultLineHeight: Float,
    callbacks: GutterGestureCallbacks
): Modifier = this.pointerInput(lineLayouts, lineCount) {
    awaitEachGesture {
        val tracker = awaitGutterGestureStart(lineLayouts, lineCount, defaultLineHeight, callbacks)
            ?: return@awaitEachGesture

        tracker.start()
        trackGutterDrag(tracker, lineLayouts, lineCount, defaultLineHeight)
        tracker.complete()
    }
}

private suspend fun AwaitPointerEventScope.awaitGutterGestureStart(
    lineLayouts: List<LineLayoutInfo>,
    lineCount: Int,
    defaultLineHeight: Float,
    callbacks: GutterGestureCallbacks
): GutterGestureTracker? {
    val down = awaitFirstDown()
    val maxLineIndex = (lineCount - 1).coerceAtLeast(0)
    val startLineIndex = findLineIndexAtY(down.position.y, lineLayouts, maxLineIndex, defaultLineHeight)

    if (startLineIndex !in 0 until lineCount) {
        return null
    }

    return GutterGestureTracker(startLineIndex, callbacks)
}

private suspend fun AwaitPointerEventScope.trackGutterDrag(
    tracker: GutterGestureTracker,
    lineLayouts: List<LineLayoutInfo>,
    lineCount: Int,
    defaultLineHeight: Float
) {
    val maxLineIndex = (lineCount - 1).coerceAtLeast(0)

    do {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: break

        if (change.pressed) {
            val newLineIndex = findLineIndexAtY(
                change.position.y,
                lineLayouts,
                maxLineIndex,
                defaultLineHeight
            )
            tracker.onLineChanged(newLineIndex)
            change.consume()
        }
    } while (event.changes.any { it.pressed })
}

// =============================================================================
// Selection Detection
// =============================================================================

/**
 * Checks if a line is within the current selection.
 */
internal fun isLineInSelection(lineIndex: Int, state: EditorState): Boolean {
    if (!state.hasSelection) return false

    val lineStart = state.getLineStartOffset(lineIndex)
    val lineEnd = lineStart + (state.lines.getOrNull(lineIndex)?.text?.length ?: 0)

    val selMin = state.selection.min
    val selMax = state.selection.max

    // Line is selected if any part of it overlaps with the selection
    return if (lineStart == lineEnd) {
        // Empty line: selected if selection spans across this position
        lineStart >= selMin && lineStart < selMax
    } else {
        // Non-empty line: selected if any part overlaps
        lineEnd > selMin && lineStart < selMax
    }
}

// =============================================================================
// Display Composables
// =============================================================================

/**
 * A composable gutter that appears to the left of the editor.
 * Allows line selection by tapping or dragging on line boxes.
 * Each box height matches the corresponding line's height (including wrapped lines).
 */
@Composable
internal fun LineGutter(
    lineLayouts: List<LineLayoutInfo>,
    state: EditorState,
    directiveResults: Map<String, DirectiveResult> = emptyMap(),
    onLineSelected: (Int) -> Unit,
    onLineDragStart: (Int) -> Unit,
    onLineDragUpdate: (Int) -> Unit,
    onLineDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val gutterWidthPx = with(density) { EditorConfig.GutterWidth.toPx() }
    val defaultLineHeight = with(density) { DefaultLineHeight.toPx() }

    val callbacks = GutterGestureCallbacks(
        onLineSelected = onLineSelected,
        onLineDragStart = onLineDragStart,
        onLineDragUpdate = onLineDragUpdate,
        onLineDragEnd = onLineDragEnd
    )

    Column(
        modifier = modifier
            .width(EditorConfig.GutterWidth)
            .gutterPointerInput(lineLayouts, state.lines.size, defaultLineHeight, callbacks)
    ) {
        GutterContent(
            lineCount = state.lines.size,
            lineLayouts = lineLayouts,
            state = state,
            directiveResults = directiveResults,
            defaultLineHeight = defaultLineHeight,
            gutterWidthPx = gutterWidthPx,
            density = density
        )
    }
}

@Composable
private fun GutterContent(
    lineCount: Int,
    lineLayouts: List<LineLayoutInfo>,
    state: EditorState,
    directiveResults: Map<String, DirectiveResult>,
    defaultLineHeight: Float,
    gutterWidthPx: Float,
    density: androidx.compose.ui.unit.Density
) {
    repeat(lineCount) { index ->
        val layoutInfo = lineLayouts.getOrNull(index)
        val lineHeight = layoutInfo?.height?.takeIf { it > 0f } ?: defaultLineHeight
        val isSelected = isLineInSelection(index, state)

        GutterBox(
            height = with(density) { lineHeight.toDp() },
            width = EditorConfig.GutterWidth,
            isSelected = isSelected,
            gutterWidthPx = gutterWidthPx
        )

        // Add gaps for expanded directive edit rows on this line
        val lineContent = state.lines.getOrNull(index)?.content ?: ""
        val lineDirectives = DirectiveFinder.findDirectives(lineContent)
        for (found in lineDirectives) {
            val key = DirectiveFinder.directiveKey(index, found.startOffset)
            val result = directiveResults[key]
            if (result != null && !result.collapsed) {
                GutterGap(height = DirectiveEditRowGapHeight, width = EditorConfig.GutterWidth)
            }
        }
    }
}

/**
 * An empty gap in the gutter for directive edit rows.
 */
@Composable
private fun GutterGap(
    height: Dp,
    width: Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
    )
}

/**
 * A single gutter box for one logical line.
 */
@Composable
private fun GutterBox(
    height: Dp,
    width: Dp,
    isSelected: Boolean,
    gutterWidthPx: Float
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .drawBehind {
                drawRect(
                    color = if (isSelected) EditorConfig.GutterSelectionColor else EditorConfig.GutterBackgroundColor,
                    size = Size(gutterWidthPx, size.height)
                )
                drawLine(
                    color = EditorConfig.GutterLineColor,
                    start = Offset(0f, size.height),
                    end = Offset(gutterWidthPx, size.height),
                    strokeWidth = 1f
                )
            }
    )
}
