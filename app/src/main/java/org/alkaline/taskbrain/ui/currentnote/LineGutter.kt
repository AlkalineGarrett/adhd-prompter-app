package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

val GutterWidth = 21.dp
private val GutterBackgroundColor = Color(0xFFE0E0E0) // Light gray
private val GutterLineColor = Color(0xFF9E9E9E) // Dark gray

/**
 * A composable gutter that appears to the left of the text field.
 * Allows line selection by tapping or dragging on line numbers.
 */
@Composable
fun LineGutter(
    textLayoutResult: TextLayoutResult?,
    scrollState: ScrollState,
    onLineSelected: (Int) -> Unit,
    onDragStart: (Int) -> Unit,
    onDragUpdate: (Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val lineCount = textLayoutResult?.lineCount ?: 1
    val lineHeight = textLayoutResult?.let {
        if (it.lineCount > 0) it.getLineBottom(0) - it.getLineTop(0) else 0f
    } ?: with(density) { 20.dp.toPx() }

    // Calculate total height based on text layout
    val totalHeight = textLayoutResult?.let {
        if (it.lineCount > 0) it.getLineBottom(it.lineCount - 1) else lineHeight
    } ?: lineHeight

    val gutterWidthPx = with(density) { GutterWidth.toPx() }

    Box(
        modifier = modifier
            .width(GutterWidth)
            .verticalScroll(scrollState)
            .drawBehind {
                // Draw background
                drawRect(
                    color = GutterBackgroundColor,
                    size = Size(gutterWidthPx, totalHeight + with(density) { 16.dp.toPx() })
                )

                // Draw horizontal lines between each row
                for (i in 0..lineCount) {
                    val y = if (textLayoutResult != null && i < textLayoutResult.lineCount) {
                        textLayoutResult.getLineTop(i)
                    } else if (textLayoutResult != null && i == textLayoutResult.lineCount) {
                        textLayoutResult.getLineBottom(i - 1)
                    } else {
                        i * lineHeight
                    }
                    // Add padding offset to match text field
                    val yWithPadding = y + with(density) { 8.dp.toPx() }
                    drawLine(
                        color = GutterLineColor,
                        start = Offset(0f, yWithPadding),
                        end = Offset(gutterWidthPx, yWithPadding),
                        strokeWidth = 1f
                    )
                }
            }
            .pointerInput(textLayoutResult) {
                val paddingPx = with(density) { 8.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startOffset = down.position
                    val startLineIndex = getLineIndexFromOffset(textLayoutResult, startOffset.y - paddingPx, lineHeight)

                    if (startLineIndex !in 0 until lineCount) {
                        return@awaitEachGesture
                    }

                    var isDragging = false
                    var currentLineIndex = startLineIndex

                    // Select the initial line immediately
                    onDragStart(startLineIndex)

                    // Track drag
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (change.pressed) {
                            val newLineIndex = getLineIndexFromOffset(
                                textLayoutResult,
                                change.position.y - paddingPx,
                                lineHeight
                            ).coerceIn(0, lineCount - 1)

                            if (newLineIndex != currentLineIndex) {
                                isDragging = true
                                currentLineIndex = newLineIndex
                                onDragUpdate(currentLineIndex)
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })

                    // Gesture ended
                    if (!isDragging) {
                        // It was a tap
                        onLineSelected(startLineIndex)
                    }
                    onDragEnd()
                }
            }
    ) {
        // Empty box with calculated height to enable scrolling
        Box(
            modifier = Modifier
                .width(GutterWidth)
                .height(with(density) { (totalHeight + 16.dp.toPx()).toDp() })
        )
    }
}

/**
 * Gets the line index from a Y offset in the gutter.
 */
fun getLineIndexFromOffset(
    textLayoutResult: TextLayoutResult?,
    yOffset: Float,
    defaultLineHeight: Float
): Int {
    if (textLayoutResult == null || textLayoutResult.lineCount == 0) {
        return (yOffset / defaultLineHeight).toInt()
    }

    // Binary search or linear search for the line
    for (i in 0 until textLayoutResult.lineCount) {
        val top = textLayoutResult.getLineTop(i)
        val bottom = textLayoutResult.getLineBottom(i)
        if (yOffset >= top && yOffset < bottom) {
            return i
        }
    }

    // If below all lines, return last line
    if (yOffset >= textLayoutResult.getLineBottom(textLayoutResult.lineCount - 1)) {
        return textLayoutResult.lineCount - 1
    }

    return 0
}
