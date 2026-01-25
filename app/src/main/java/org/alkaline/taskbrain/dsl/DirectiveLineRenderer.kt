package org.alkaline.taskbrain.dsl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
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

    // Render with directive boxes
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var currentPos = 0

        for (segment in displayResult.segments) {
            when (segment) {
                is DirectiveSegment.Text -> {
                    BasicText(
                        text = segment.content,
                        style = textStyle
                    )
                    currentPos += segment.content.length
                }
                is DirectiveSegment.Directive -> {
                    DirectiveResultBox(
                        displayText = segment.displayText,
                        isComputed = segment.isComputed,
                        hasError = segment.result?.error != null,
                        textStyle = textStyle,
                        onTap = { onDirectiveTap(segment.key, segment.sourceText) }
                    )
                    currentPos += segment.displayText.length
                }
            }
        }
    }
}

/**
 * A directive result displayed in a dashed box.
 * Shows the computed result (or source if not computed).
 */
@Composable
private fun DirectiveResultBox(
    displayText: String,
    isComputed: Boolean,
    hasError: Boolean,
    textStyle: TextStyle,
    onTap: () -> Unit
) {
    val boxColor = if (hasError) ErrorBoxColor else ComputedBoxColor

    val textColor = when {
        hasError -> ErrorTextColor
        else -> textStyle.color
    }

    Box(
        modifier = Modifier
            .clickable(onClick = onTap)
            .dashedBorder(boxColor)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        BasicText(
            text = displayText,
            style = textStyle.copy(color = textColor)
        )
    }
}

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

// Colors for directive boxes
private val ComputedBoxColor = Color(0xFF4CAF50)  // Green
private val ErrorBoxColor = Color(0xFFF44336)     // Red
private val ErrorTextColor = Color(0xFFD32F2F)    // Dark red
