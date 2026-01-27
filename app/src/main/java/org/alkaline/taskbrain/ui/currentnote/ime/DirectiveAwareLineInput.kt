package org.alkaline.taskbrain.ui.currentnote.ime

import android.view.View
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.alkaline.taskbrain.dsl.directives.DirectiveResult
import org.alkaline.taskbrain.dsl.directives.DirectiveSegment
import org.alkaline.taskbrain.dsl.directives.DirectiveSegmenter
import org.alkaline.taskbrain.dsl.directives.DisplayTextResult
import org.alkaline.taskbrain.ui.currentnote.EditorController

// Directive box colors
private val DirectiveErrorColor = Color(0xFFF44336)    // Red
private val DirectiveWarningColor = Color(0xFFFF9800)  // Orange
private val DirectiveSuccessColor = Color(0xFF4CAF50)  // Green

// Dashed box drawing parameters
private object DirectiveBoxStyle {
    val strokeWidth = 1.dp
    val dashLength = 4.dp
    val gapLength = 2.dp
    val cornerRadius = 3.dp
    val padding = 2.dp
}

// Cursor drawing
private val CursorColor = Color.Black
private val CursorWidth = 2.dp

// Empty directive placeholder
private val EmptyDirectiveTapWidth = 16.dp

/**
 * A directive-aware text input that allows editing around directives.
 *
 * Key features:
 * - Shows computed directive results inline (replacing source text visually)
 * - Directive results are displayed in dashed boxes and are tappable
 * - Non-directive text is fully editable
 * - Cursor position maps correctly between source and display
 */
@Composable
internal fun DirectiveAwareLineInput(
    lineIndex: Int,
    content: String,
    contentCursor: Int,
    controller: EditorController,
    isFocused: Boolean,
    hasExternalSelection: Boolean,
    textStyle: TextStyle,
    focusRequester: FocusRequester,
    directiveResults: Map<String, DirectiveResult>,
    onFocusChanged: (Boolean) -> Unit,
    onTextLayoutResult: (TextLayoutResult) -> Unit,
    onDirectiveTap: (directiveKey: String, sourceText: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hostView = LocalView.current
    val imeState = remember(lineIndex, controller) {
        LineImeState(lineIndex, controller)
    }

    LaunchedEffect(content, contentCursor) {
        if (!imeState.isInBatchEdit) {
            imeState.syncFromController()
        }
    }

    // Build display text with directive results
    val displayResult = remember(content, lineIndex, directiveResults) {
        DirectiveSegmenter.buildDisplayText(content, lineIndex, directiveResults)
    }

    // Map source cursor to display cursor
    val displayCursor = remember(contentCursor, displayResult) {
        mapSourceToDisplayCursor(contentCursor, displayResult)
    }

    val interactionSource = remember { MutableInteractionSource() }
    var textLayoutResultState: TextLayoutResult? by remember { mutableStateOf(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    // Check if cursor is inside a directive (shouldn't show cursor there)
    val cursorInDirective = remember(contentCursor, displayResult) {
        displayResult.directiveDisplayRanges.any { range ->
            contentCursor > range.sourceRange.first && contentCursor < range.sourceRange.last + 1
        }
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState -> onFocusChanged(focusState.isFocused) }
            .lineImeConnection(imeState, contentCursor, hostView)
            .focusable(interactionSource = interactionSource)
    ) {
        if (displayResult.directiveDisplayRanges.isEmpty()) {
            // No directives - render as simple text
            BasicText(
                text = content,
                style = textStyle,
                onTextLayout = { layoutResult ->
                    textLayoutResultState = layoutResult
                    onTextLayoutResult(layoutResult)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .drawCursor(
                        isFocused = isFocused,
                        hasExternalSelection = hasExternalSelection,
                        cursorPosition = contentCursor,
                        textLength = content.length,
                        textLayoutResult = textLayoutResultState,
                        cursorAlpha = cursorAlpha
                    )
            )
        } else {
            // Has directives - render with overlay boxes
            DirectiveOverlayText(
                displayResult = displayResult,
                textStyle = textStyle,
                isFocused = isFocused,
                hasExternalSelection = hasExternalSelection,
                displayCursor = displayCursor,
                cursorInDirective = cursorInDirective,
                cursorAlpha = cursorAlpha,
                onDirectiveTap = onDirectiveTap,
                onTextLayout = { layoutResult ->
                    textLayoutResultState = layoutResult
                    onTextLayoutResult(layoutResult)
                },
                onTapAtSourcePosition = { sourcePosition ->
                    controller.setCursor(lineIndex, sourcePosition)
                }
            )
        }
    }
}

/**
 * Renders display text with directive boxes as overlays.
 * Uses a single BasicText for correct cursor positioning.
 */
@Composable
private fun DirectiveOverlayText(
    displayResult: DisplayTextResult,
    textStyle: TextStyle,
    isFocused: Boolean,
    hasExternalSelection: Boolean,
    displayCursor: Int,
    cursorInDirective: Boolean,
    cursorAlpha: Float,
    onDirectiveTap: (directiveKey: String, sourceText: String) -> Unit,
    onTextLayout: (TextLayoutResult) -> Unit,
    onTapAtSourcePosition: (Int) -> Unit
) {
    var textLayoutResult: TextLayoutResult? by remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Render the full display text
        BasicText(
            text = displayResult.displayText,
            style = textStyle,
            onTextLayout = { layout ->
                textLayoutResult = layout
                onTextLayout(layout)
            },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(displayResult) {
                    detectTapGestures { offset ->
                        textLayoutResult?.let { layout ->
                            // Get the display position from tap coordinates
                            val displayPosition = layout.getOffsetForPosition(offset)
                            // Map to source position and notify
                            val sourcePosition = mapDisplayToSourceCursor(displayPosition, displayResult)
                            onTapAtSourcePosition(sourcePosition)
                        }
                    }
                }
                .drawWithContent {
                    drawContent()

                    // Guard against stale layout from previous render
                    val layout = textLayoutResult?.takeIf {
                        it.layoutInput.text.length == displayResult.displayText.length
                    } ?: return@drawWithContent

                    // Draw cursor
                    if (isFocused && !hasExternalSelection && !cursorInDirective) {
                        val cursorPos = displayCursor.coerceIn(0, displayResult.displayText.length)
                        val cursorRect = try {
                            layout.getCursorRect(cursorPos)
                        } catch (e: Exception) {
                            Rect(0f, 0f, CursorWidth.toPx(), layout.size.height.toFloat())
                        }
                        drawLine(
                            color = CursorColor.copy(alpha = cursorAlpha),
                            start = Offset(cursorRect.left, cursorRect.top),
                            end = Offset(cursorRect.left, cursorRect.bottom),
                            strokeWidth = CursorWidth.toPx()
                        )
                    }

                    // Draw dashed boxes around directive portions
                    for (range in displayResult.directiveDisplayRanges) {
                        val boxColor = when {
                            range.hasError -> DirectiveErrorColor
                            range.hasWarning -> DirectiveWarningColor
                            else -> DirectiveSuccessColor
                        }
                        val startOffset = range.displayRange.first.coerceIn(0, displayResult.displayText.length)
                        val endOffset = (range.displayRange.last + 1).coerceIn(0, displayResult.displayText.length)
                        val padding = DirectiveBoxStyle.padding.toPx()

                        if (startOffset < endOffset) {
                            // Non-empty display text - draw box around the text
                            val path = layout.getPathForRange(startOffset, endOffset)
                            val bounds = path.getBounds()

                            drawRoundRect(
                                color = boxColor,
                                topLeft = Offset(bounds.left - padding, bounds.top - padding),
                                size = Size(bounds.width + padding * 2, bounds.height + padding * 2),
                                cornerRadius = CornerRadius(DirectiveBoxStyle.cornerRadius.toPx()),
                                style = Stroke(
                                    width = DirectiveBoxStyle.strokeWidth.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(DirectiveBoxStyle.dashLength.toPx(), DirectiveBoxStyle.gapLength.toPx())
                                    )
                                )
                            )
                        } else if (range.displayText.isEmpty()) {
                            // Empty display text - draw a vertical dashed line placeholder
                            val cursorRect = try {
                                layout.getCursorRect(startOffset.coerceIn(0, displayResult.displayText.length))
                            } catch (e: Exception) {
                                Rect(0f, 0f, 2f, layout.size.height.toFloat())
                            }

                            // Draw vertical dashed line
                            drawLine(
                                color = boxColor,
                                start = Offset(cursorRect.left, cursorRect.top + padding),
                                end = Offset(cursorRect.left, cursorRect.bottom - padding),
                                strokeWidth = DirectiveBoxStyle.strokeWidth.toPx() * 1.5f,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(DirectiveBoxStyle.dashLength.toPx() * 0.75f, DirectiveBoxStyle.gapLength.toPx())
                                )
                            )
                        }
                    }
                }
        )

        // Invisible tap targets for directives - wrapped in matchParentSize to not affect layout
        Box(modifier = Modifier.matchParentSize()) {
            val layout = textLayoutResult?.takeIf {
                it.layoutInput.text.length == displayResult.displayText.length
            } ?: return@Box

            for (range in displayResult.directiveDisplayRanges) {
                val startOffset = range.displayRange.first.coerceIn(0, displayResult.displayText.length)
                val endOffset = (range.displayRange.last + 1).coerceIn(0, displayResult.displayText.length)
                val padding = DirectiveBoxStyle.padding

                if (startOffset < endOffset) {
                    // Non-empty - use text bounds
                    val path = layout.getPathForRange(startOffset, endOffset)
                    val bounds = path.getBounds()

                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(LocalDensity.current) { (bounds.left - padding.toPx()).toDp() },
                                y = with(LocalDensity.current) { (bounds.top - padding.toPx()).toDp() }
                            )
                            .size(
                                width = with(LocalDensity.current) { (bounds.width + padding.toPx() * 2).toDp() },
                                height = with(LocalDensity.current) { (bounds.height + padding.toPx() * 2).toDp() }
                            )
                            .clickable {
                                onDirectiveTap(range.key, range.sourceText)
                            }
                    )
                } else if (range.displayText.isEmpty()) {
                    // Empty - create tap target at cursor position
                    val cursorRect = try {
                        layout.getCursorRect(startOffset.coerceIn(0, displayResult.displayText.length))
                    } catch (e: Exception) {
                        Rect(0f, 0f, 2f, layout.size.height.toFloat())
                    }

                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(LocalDensity.current) { (cursorRect.left - EmptyDirectiveTapWidth.toPx() / 2).toDp() },
                                y = with(LocalDensity.current) { cursorRect.top.toDp() }
                            )
                            .size(
                                width = EmptyDirectiveTapWidth,
                                height = with(LocalDensity.current) { (cursorRect.bottom - cursorRect.top).toDp() }
                            )
                            .clickable {
                                onDirectiveTap(range.key, range.sourceText)
                            }
                    )
                }
            }
        }
    }
}

/**
 * Modifier that draws a cursor at the specified position.
 */
private fun Modifier.drawCursor(
    isFocused: Boolean,
    hasExternalSelection: Boolean,
    cursorPosition: Int,
    textLength: Int,
    textLayoutResult: TextLayoutResult?,
    cursorAlpha: Float
): Modifier = this.drawWithContent {
    drawContent()
    if (isFocused && !hasExternalSelection && textLayoutResult != null) {
        val cursorPos = cursorPosition.coerceIn(0, textLength)
        val cursorRect = try {
            textLayoutResult.getCursorRect(cursorPos)
        } catch (e: Exception) {
            Rect(0f, 0f, CursorWidth.toPx(), textLayoutResult.size.height.toFloat())
        }
        drawLine(
            color = CursorColor.copy(alpha = cursorAlpha),
            start = Offset(cursorRect.left, cursorRect.top),
            end = Offset(cursorRect.left, cursorRect.bottom),
            strokeWidth = CursorWidth.toPx()
        )
    }
}

/**
 * Maps a cursor position from source text to display text.
 */
private fun mapSourceToDisplayCursor(sourceCursor: Int, displayResult: DisplayTextResult): Int {
    if (displayResult.directiveDisplayRanges.isEmpty()) {
        return sourceCursor
    }

    var displayCursor = sourceCursor
    var adjustment = 0

    for (range in displayResult.directiveDisplayRanges) {
        if (sourceCursor <= range.sourceRange.first) {
            // Cursor is before this directive
            break
        } else if (sourceCursor > range.sourceRange.last) {
            // Cursor is after this directive - adjust for the length difference
            val sourceLength = range.sourceRange.last - range.sourceRange.first + 1
            val displayLength = range.displayRange.last - range.displayRange.first + 1
            adjustment += displayLength - sourceLength
        } else {
            // Cursor is inside the directive - place at end of directive display
            return range.displayRange.last + 1
        }
    }

    return (sourceCursor + adjustment).coerceAtLeast(0)
}

/**
 * Maps a cursor position from display text to source text.
 * This is the reverse of mapSourceToDisplayCursor.
 */
private fun mapDisplayToSourceCursor(displayCursor: Int, displayResult: DisplayTextResult): Int {
    if (displayResult.directiveDisplayRanges.isEmpty()) {
        return displayCursor
    }

    var sourceCursor = displayCursor

    for (range in displayResult.directiveDisplayRanges) {
        if (displayCursor <= range.displayRange.first) {
            // Cursor is before this directive - no adjustment needed
            break
        } else if (displayCursor > range.displayRange.last) {
            // Cursor is after this directive - adjust for the length difference
            val sourceLength = range.sourceRange.last - range.sourceRange.first + 1
            val displayLength = range.displayRange.last - range.displayRange.first + 1
            sourceCursor += sourceLength - displayLength
        } else {
            // Cursor is inside the directive display - map to end of source directive
            return range.sourceRange.last + 1
        }
    }

    return sourceCursor.coerceAtLeast(0)
}

