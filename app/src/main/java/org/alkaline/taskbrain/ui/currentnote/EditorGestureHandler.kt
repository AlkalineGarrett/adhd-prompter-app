package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tracks layout information for a line, used for hit testing during selection.
 */
internal data class LineLayoutInfo(
    val lineIndex: Int,
    val yOffset: Float,
    val height: Float,
    val textLayoutResult: TextLayoutResult?,
    val prefixWidthPx: Float = 0f
)

// =============================================================================
// Position Conversion Utilities
// =============================================================================

/**
 * Finds which line index contains the given Y position.
 * Returns the line index, or 0 if no lines exist.
 *
 * @param y The Y position to find the line for
 * @param lineLayouts Layout information for each line
 * @param maxLineIndex Maximum valid line index (used for clamping)
 * @param defaultLineHeight Optional fallback line height for estimation when layouts are invalid
 */
internal fun findLineIndexAtY(
    y: Float,
    lineLayouts: List<LineLayoutInfo>,
    maxLineIndex: Int,
    defaultLineHeight: Float = 0f
): Int {
    if (maxLineIndex < 0) return 0

    // First pass: find exact match
    for (i in lineLayouts.indices) {
        val layout = lineLayouts[i]
        if (layout.height <= 0f) continue

        if (y >= layout.yOffset && y < layout.yOffset + layout.height) {
            return i.coerceIn(0, maxLineIndex)
        }
    }

    // Second pass: find closest line above the position
    var closestLine = 0
    for (i in lineLayouts.indices) {
        val layout = lineLayouts[i]
        if (layout.height <= 0f) continue
        if (y >= layout.yOffset) {
            closestLine = i
        }
    }

    // Check if position is beyond all lines
    val lastValidLayout = lineLayouts.lastOrNull { it.height > 0f }
    if (lastValidLayout != null && y >= lastValidLayout.yOffset + lastValidLayout.height) {
        return lastValidLayout.lineIndex.coerceIn(0, maxLineIndex)
    }

    // Fallback: estimate based on average line height or provided default
    val validLayouts = lineLayouts.filter { it.height > 0f }
    val fallbackHeight = if (validLayouts.isNotEmpty()) {
        validLayouts.map { it.height }.average().toFloat()
    } else {
        defaultLineHeight
    }

    if (fallbackHeight > 0f) {
        val estimated = (y / fallbackHeight).toInt().coerceIn(0, maxLineIndex)
        return estimated
    }

    return closestLine.coerceIn(0, maxLineIndex)
}

/**
 * Converts a screen position to a global character offset in the editor text.
 */
internal fun positionToGlobalOffset(
    position: Offset,
    state: EditorState,
    lineLayouts: List<LineLayoutInfo>
): Int {
    if (state.lines.isEmpty()) return 0

    val lineIndex = findLineIndexAtY(position.y, lineLayouts, state.lines.lastIndex)
    val lineState = state.lines.getOrNull(lineIndex) ?: return 0
    val layoutInfo = lineLayouts.getOrNull(lineIndex)

    val contentOffset = getCharacterOffsetInContent(
        position = position,
        lineYOffset = layoutInfo?.yOffset ?: 0f,
        prefixLength = lineState.prefix.length,
        contentLength = lineState.content.length,
        textLayout = layoutInfo?.textLayoutResult
    )

    return state.getLineStartOffset(lineIndex) + lineState.prefix.length + contentOffset
}

/**
 * Calculates the character offset within a line's content based on screen X position.
 */
private fun getCharacterOffsetInContent(
    position: Offset,
    lineYOffset: Float,
    prefixLength: Int,
    contentLength: Int,
    textLayout: TextLayoutResult?
): Int {
    val prefixWidthPx = prefixLength * EditorConfig.EstimatedCharWidthPx
    val localX = (position.x - prefixWidthPx).coerceAtLeast(0f)
    val localY = (position.y - lineYOffset).coerceAtLeast(0f)

    if (textLayout != null) {
        return try {
            textLayout.getOffsetForPosition(Offset(localX, localY))
        } catch (e: Exception) {
            estimateCharacterOffset(localX, textLayout, contentLength)
        }
    }

    return (localX / EditorConfig.EstimatedCharWidthPx).toInt().coerceIn(0, contentLength)
}

private fun estimateCharacterOffset(
    localX: Float,
    textLayout: TextLayoutResult,
    contentLength: Int
): Int {
    val avgCharWidth = if (textLayout.size.width > 0 && contentLength > 0) {
        textLayout.size.width.toFloat() / contentLength
    } else {
        EditorConfig.EstimatedCharWidthPx
    }
    return (localX / avgCharWidth).toInt().coerceIn(0, contentLength)
}

// =============================================================================
// Word Boundary Detection
// =============================================================================

/**
 * Finds word boundaries at a given character offset in the text.
 * Returns a pair of (wordStart, wordEnd).
 *
 * Word characters are letters and digits. Punctuation is excluded from word selection
 * to match standard text editor behavior.
 *
 * For empty lines or positions with only whitespace/punctuation, includes at least
 * the newline character if available.
 */
internal fun findWordBoundaries(text: String, offset: Int): Pair<Int, Int> {
    if (text.isEmpty()) {
        return 0 to 0
    }

    // Clamp offset to valid range
    val clampedOffset = offset.coerceIn(0, text.length)

    // Check if we're on a word character (letter or digit)
    val isOnWordChar = clampedOffset < text.length && text[clampedOffset].isLetterOrDigit()
    val isPrevWordChar = clampedOffset > 0 && text[clampedOffset - 1].isLetterOrDigit()

    // Find word boundaries using letters and digits only
    var wordStart = clampedOffset
    var wordEnd = clampedOffset

    if (isOnWordChar || isPrevWordChar) {
        // Expand backward to find word start
        wordStart = clampedOffset
        while (wordStart > 0 && text[wordStart - 1].isLetterOrDigit()) {
            wordStart--
        }

        // Expand forward to find word end
        wordEnd = clampedOffset
        while (wordEnd < text.length && text[wordEnd].isLetterOrDigit()) {
            wordEnd++
        }
    }

    // If no word found, try to select at least a newline character
    if (wordStart == wordEnd) {
        if (clampedOffset < text.length && text[clampedOffset] == '\n') {
            wordEnd = clampedOffset + 1
        } else if (clampedOffset > 0 && text[clampedOffset - 1] == '\n') {
            if (clampedOffset < text.length && text[clampedOffset] == '\n') {
                wordEnd = clampedOffset + 1
            }
        }
    }

    return wordStart to wordEnd
}

// =============================================================================
// Gesture Tracking
// =============================================================================

/**
 * Tracks state for a single gesture from pointer down to pointer up.
 */
private class GestureTracker(
    private val downPosition: Offset,
    private val state: EditorState,
    private val lineLayouts: List<LineLayoutInfo>,
    private val touchSlop: Float
) {
    var longPressTriggered = false
        private set
    var isScrollGesture = false
        private set
    var lastPosition = downPosition
        private set

    private var anchorStart = -1
    private var anchorEnd = -1
    private var totalDragDistance = 0f

    /**
     * Called when long press timer fires.
     * Selects the word at the down position.
     */
    fun onLongPressTriggered() {
        if (isScrollGesture) return

        longPressTriggered = true
        val globalOffset = positionToGlobalOffset(downPosition, state, lineLayouts)
        val (wordStart, wordEnd) = findWordBoundaries(state.text, globalOffset)
        anchorStart = wordStart
        anchorEnd = wordEnd
        state.setSelection(wordStart, wordEnd)
    }

    /**
     * Processes a pointer move event.
     * Returns true if the event should be consumed (prevent scrolling).
     */
    fun onPointerMove(position: Offset): Boolean {
        val dragDelta = position - lastPosition
        totalDragDistance += dragDelta.getDistance()
        lastPosition = position

        // Check if this becomes a scroll gesture
        if (!longPressTriggered && !isScrollGesture && totalDragDistance > touchSlop) {
            isScrollGesture = true
            return false
        }

        // In selection mode - update selection
        if (longPressTriggered && anchorStart >= 0) {
            updateDragSelection(position)
            return true // Consume to prevent scroll
        }

        return false
    }

    private fun updateDragSelection(position: Offset) {
        val globalOffset = positionToGlobalOffset(position, state, lineLayouts)
        val selStart = minOf(anchorStart, globalOffset)
        val selEnd = maxOf(anchorEnd, globalOffset)
        state.setSelection(selStart, selEnd)
    }

    /**
     * Handles gesture completion when pointer is released.
     */
    fun onGestureComplete(
        onCursorPositioned: (Int) -> Unit,
        onTapOnSelection: ((Offset) -> Unit)?,
        onSelectionCompleted: ((Offset) -> Unit)?
    ) {
        if (isScrollGesture) return

        if (longPressTriggered) {
            // Long press selection completed
            if (state.hasSelection) {
                onSelectionCompleted?.invoke(lastPosition)
            }
        } else {
            // Tap gesture
            handleTap(onCursorPositioned, onTapOnSelection)
        }
    }

    private fun handleTap(
        onCursorPositioned: (Int) -> Unit,
        onTapOnSelection: ((Offset) -> Unit)?
    ) {
        val globalOffset = positionToGlobalOffset(downPosition, state, lineLayouts)

        // Check if tap is within existing selection
        if (state.hasSelection && isOffsetInSelection(globalOffset)) {
            onTapOnSelection?.invoke(downPosition)
        } else {
            onCursorPositioned(globalOffset)
        }
    }

    private fun isOffsetInSelection(offset: Int): Boolean {
        return offset >= state.selection.min && offset <= state.selection.max
    }
}

// =============================================================================
// Modifier Extension
// =============================================================================

/**
 * Creates a Modifier that handles all pointer input for the editor.
 * Intercepts touch events to implement tap-to-position-cursor and long-press-to-select.
 *
 * Gesture handling strategy:
 * - Tap: Position cursor (or show context menu if tapping on active selection)
 * - Long press: Select word, then drag to extend
 * - Scroll (quick drag): Don't consume, let native scroll handle it smoothly
 */
internal fun Modifier.editorPointerInput(
    state: EditorState,
    lineLayouts: List<LineLayoutInfo>,
    longPressTimeoutMillis: Long,
    touchSlop: Float,
    scrollState: ScrollState?,
    onCursorPositioned: (Int) -> Unit,
    onTapOnSelection: ((Offset) -> Unit)? = null,
    onSelectionCompleted: ((Offset) -> Unit)? = null
): Modifier = this.pointerInput(scrollState) {
    coroutineScope {
        awaitPointerEventScope {
            while (true) {
                val tracker = awaitGestureStart(state, lineLayouts, touchSlop) ?: continue

                val longPressJob = launchLongPressDetection(longPressTimeoutMillis, tracker)

                trackPointerUntilRelease(tracker, longPressJob)

                tracker.onGestureComplete(onCursorPositioned, onTapOnSelection, onSelectionCompleted)
            }
        }
    }
}

/**
 * Waits for a pointer down event and creates a gesture tracker.
 */
private suspend fun AwaitPointerEventScope.awaitGestureStart(
    state: EditorState,
    lineLayouts: List<LineLayoutInfo>,
    touchSlop: Float
): GestureTracker? {
    val down = awaitPointerEvent(PointerEventPass.Initial)
    val downChange = down.changes.firstOrNull { it.pressed } ?: return null
    return GestureTracker(downChange.position, state, lineLayouts, touchSlop)
}

/**
 * Launches the long press detection coroutine.
 */
private fun kotlinx.coroutines.CoroutineScope.launchLongPressDetection(
    timeoutMillis: Long,
    tracker: GestureTracker
): Job = launch {
    delay(timeoutMillis)
    tracker.onLongPressTriggered()
}

/**
 * Tracks pointer movement until release.
 */
private suspend fun AwaitPointerEventScope.trackPointerUntilRelease(
    tracker: GestureTracker,
    longPressJob: Job
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull() ?: break

        if (!change.pressed) {
            longPressJob.cancel()
            break
        }

        val shouldConsume = tracker.onPointerMove(change.position)
        if (shouldConsume) {
            change.consume()
        }

        // Cancel long press if scrolling started
        if (tracker.isScrollGesture) {
            longPressJob.cancel()
        }
    }
}
