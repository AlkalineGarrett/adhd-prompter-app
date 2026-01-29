package org.alkaline.taskbrain.dsl.ui

import androidx.compose.foundation.clickable
import org.alkaline.taskbrain.dsl.directives.DirectiveResult
import org.alkaline.taskbrain.dsl.directives.DirectiveSegment
import org.alkaline.taskbrain.dsl.directives.DirectiveSegmenter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Renders line content with computed directive results replacing source text.
 * Computed directives are displayed in dashed boxes and are tappable.
 *
 * This component renders display-only content - no text editing.
 * Editing happens via DirectiveEditRow when a directive is tapped.
 *
 * @param sourceContent The original line content with directive source text
 * @param lineIndex The line number (0-indexed) - used for position-based directive keys
 * @param directiveResults Map of directive key to execution result (keys are position-based)
 * @param textStyle The text style for rendering
 * @param onDirectiveTap Called when a directive is tapped, with position-based key and source text
 * @param modifier Modifier for the root composable
 */
@Composable
fun DirectiveLineContent(
    sourceContent: String,
    lineIndex: Int,
    directiveResults: Map<String, DirectiveResult>,
    textStyle: TextStyle,
    onDirectiveTap: (directiveKey: String, sourceText: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Build display text with directive results replacing source
    val displayResult = remember(sourceContent, lineIndex, directiveResults) {
        DirectiveSegmenter.buildDisplayText(sourceContent, lineIndex, directiveResults)
    }

    if (displayResult.directiveDisplayRanges.isEmpty()) {
        // No directives - render as plain text
        BasicText(
            text = displayResult.displayText,
            style = textStyle,
            modifier = modifier
        )
        return
    }

    // Check if this line contains a view directive with multi-line content
    val hasMultiLineView = displayResult.directiveDisplayRanges.any { range ->
        range.isView && range.displayText.contains('\n')
    }

    if (hasMultiLineView) {
        // Use Column layout for multi-line view content
        Column(modifier = modifier.fillMaxWidth()) {
            for (segment in displayResult.segments) {
                when (segment) {
                    is DirectiveSegment.Text -> {
                        if (segment.content.isNotEmpty()) {
                            BasicText(
                                text = segment.content,
                                style = textStyle
                            )
                        }
                    }
                    is DirectiveSegment.Directive -> {
                        val directiveRange = displayResult.directiveDisplayRanges.find { it.key == segment.key }
                        val isView = directiveRange?.isView ?: false

                        if (isView && segment.isComputed && segment.result?.error == null) {
                            ViewDirectiveContent(
                                displayText = segment.displayText,
                                textStyle = textStyle,
                                onTap = { onDirectiveTap(segment.key, segment.sourceText) }
                            )
                        } else {
                            DirectiveResultBox(
                                displayText = segment.displayText,
                                isComputed = segment.isComputed,
                                hasError = segment.result?.error != null,
                                textStyle = textStyle,
                                onTap = { onDirectiveTap(segment.key, segment.sourceText) }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Use Row layout for single-line content (original behavior)
        Row(
            modifier = modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (segment in displayResult.segments) {
                when (segment) {
                    is DirectiveSegment.Text -> {
                        BasicText(
                            text = segment.content,
                            style = textStyle
                        )
                    }
                    is DirectiveSegment.Directive -> {
                        val directiveRange = displayResult.directiveDisplayRanges.find { it.key == segment.key }
                        val isView = directiveRange?.isView ?: false

                        if (isView && segment.isComputed && segment.result?.error == null) {
                            ViewDirectiveContent(
                                displayText = segment.displayText,
                                textStyle = textStyle,
                                onTap = { onDirectiveTap(segment.key, segment.sourceText) }
                            )
                        } else {
                            DirectiveResultBox(
                                displayText = segment.displayText,
                                isComputed = segment.isComputed,
                                hasError = segment.result?.error != null,
                                textStyle = textStyle,
                                onTap = { onDirectiveTap(segment.key, segment.sourceText) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Content from a view directive, rendered inline without a box.
 * Shows the viewed notes' content with a subtle left border indicator.
 * Supports multi-line content from viewed notes.
 *
 * Milestone 10.
 */
@Composable
private fun ViewDirectiveContent(
    displayText: String,
    textStyle: TextStyle,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .viewIndicator(DirectiveColors.ViewIndicator)
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        SelectionContainer {
            Text(
                text = displayText,
                style = textStyle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A directive result displayed in a dashed box.
 * Shows the computed result (or source if not computed).
 * Empty results show a vertical dashed line placeholder.
 */
@Composable
private fun DirectiveResultBox(
    displayText: String,
    isComputed: Boolean,
    hasError: Boolean,
    textStyle: TextStyle,
    onTap: () -> Unit
) {
    val boxColor = if (hasError) DirectiveColors.ErrorBorder else DirectiveColors.SuccessBorder

    val textColor = when {
        hasError -> DirectiveColors.ErrorText
        else -> textStyle.color
    }

    val isEmpty = displayText.isEmpty()

    Box(
        modifier = Modifier
            .clickable(onClick = onTap)
            .then(
                if (isEmpty) {
                    Modifier
                        .size(width = EmptyPlaceholderWidth, height = EmptyPlaceholderHeight)
                        .emptyResultPlaceholder(boxColor)
                } else {
                    Modifier
                        .dashedBorder(boxColor)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                }
            )
    ) {
        if (!isEmpty) {
            SelectionContainer {
                Text(
                    text = displayText,
                    style = textStyle.copy(color = textColor)
                )
            }
        }
    }
}

// Empty result placeholder dimensions
private val EmptyPlaceholderWidth = 12.dp
private val EmptyPlaceholderHeight = 16.dp

/**
 * Modifier that draws a dashed border around the content.
 */
private fun Modifier.dashedBorder(color: Color): Modifier = this.drawBehind {
    val strokeWidth = 1.dp.toPx()
    val dashLength = 4.dp.toPx()
    val gapLength = 2.dp.toPx()
    val cornerRadius = 3.dp.toPx()

    drawRoundRect(
        color = color,
        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength))
        )
    )
}

/**
 * Modifier for view directive content - draws a left border indicator.
 * This provides a subtle visual distinction for viewed content.
 *
 * Milestone 10.
 */
private fun Modifier.viewIndicator(color: Color): Modifier = this.drawBehind {
    val strokeWidth = 2.dp.toPx()

    // Draw solid left border
    drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(0f, size.height),
        strokeWidth = strokeWidth
    )
}

/**
 * Modifier for empty result placeholder - draws a vertical dashed line.
 * This provides a tappable target when a directive evaluates to an empty string.
 */
private fun Modifier.emptyResultPlaceholder(color: Color): Modifier = this.drawBehind {
    val strokeWidth = 1.5.dp.toPx()
    val dashLength = 3.dp.toPx()
    val gapLength = 2.dp.toPx()
    val lineHeight = size.height

    // Draw vertical dashed line in the center
    val centerX = size.width / 2

    drawLine(
        color = color,
        start = Offset(centerX, 0f),
        end = Offset(centerX, lineHeight),
        strokeWidth = strokeWidth,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength))
    )
}

