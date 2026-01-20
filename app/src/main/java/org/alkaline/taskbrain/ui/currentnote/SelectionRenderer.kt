package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density

/**
 * Draws selection highlight for the prefix portion of a line.
 *
 * @param prefix The prefix text
 * @param selectionRange The selection range within the prefix (in prefix-local offsets)
 * @param textStyle The text style used for measuring
 */
@Composable
fun PrefixSelectionOverlay(
    prefix: String,
    selectionRange: IntRange,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val startWidth = if (selectionRange.first > 0) {
        textMeasurer.measure(prefix.substring(0, selectionRange.first), textStyle).size.width.toFloat()
    } else 0f

    val endWidth = textMeasurer.measure(
        prefix.substring(0, (selectionRange.last + 1).coerceAtMost(prefix.length)),
        textStyle
    ).size.width.toFloat()

    Box(
        modifier = modifier
            .offset(x = with(density) { startWidth.toDp() })
            .width(with(density) { (endWidth - startWidth).toDp() })
            .fillMaxHeight()
            .background(EditorConfig.SelectionColor)
    )
}

/**
 * Draws selection highlight for the content portion of a line.
 * Handles multi-line (wrapped) content correctly.
 *
 * @param selectionRange The selection range in content-local offsets
 * @param contentLength The total length of the content
 * @param textLayout The TextLayoutResult from the content TextField
 * @param density The current density for pixel-to-dp conversion
 * @param includesNewline Whether the selection includes the newline at the end of this line
 * @param spaceWidth Width of a space character in pixels (used for newline visualization)
 */
@Composable
fun ContentSelectionOverlay(
    selectionRange: IntRange?,
    contentLength: Int,
    textLayout: TextLayoutResult,
    density: Density,
    includesNewline: Boolean = false,
    spaceWidth: Float = 0f
) {
    // Draw content selection if there's a selection range
    if (selectionRange != null) {
        val selStart = selectionRange.first
        val selEnd = selectionRange.last + 1

        // Get visual line range for selection
        val startLine = textLayout.getLineForOffset(selStart.coerceIn(0, contentLength))
        val endLine = textLayout.getLineForOffset(
            (selEnd - 1).coerceIn(0, contentLength.coerceAtLeast(1) - 1).coerceAtLeast(0)
        )

        for (line in startLine..endLine) {
            val lineStart = textLayout.getLineStart(line)
            val lineEnd = textLayout.getLineEnd(line)

            val highlightStart = maxOf(selStart, lineStart)
            val highlightEnd = minOf(selEnd, lineEnd)

            if (highlightStart < highlightEnd) {
                val startX = textLayout.getHorizontalPosition(highlightStart, true)
                // Use getLineRight when highlight extends to end of line,
                // because getHorizontalPosition(lineEnd) returns 0 (start of next wrapped line)
                val endX = if (highlightEnd >= lineEnd) {
                    textLayout.getLineRight(line)
                } else {
                    textLayout.getHorizontalPosition(highlightEnd, true)
                }
                val lineTop = textLayout.getLineTop(line)
                val lineBottom = textLayout.getLineBottom(line)

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { startX.toDp() },
                            y = with(density) { lineTop.toDp() }
                        )
                        .width(with(density) { (endX - startX).toDp() })
                        .height(with(density) { (lineBottom - lineTop).toDp() })
                        .background(EditorConfig.SelectionColor)
                )
            }
        }
    }

    // Draw newline selection at the end of the last visual line
    if (includesNewline && spaceWidth > 0f) {
        val lastVisualLine = textLayout.lineCount - 1
        val lineRight = textLayout.getLineRight(lastVisualLine)
        val lineTop = textLayout.getLineTop(lastVisualLine)
        val lineBottom = textLayout.getLineBottom(lastVisualLine)

        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { lineRight.toDp() },
                    y = with(density) { lineTop.toDp() }
                )
                .width(with(density) { spaceWidth.toDp() })
                .height(with(density) { (lineBottom - lineTop).toDp() })
                .background(EditorConfig.NewlineSelectionColor)
        )
    }
}

/**
 * Converts a line selection range to content selection range.
 * The line selection is in full-line coordinates (including prefix).
 * Returns null if there's no selection in the content area.
 */
fun lineSelectionToContentSelection(
    lineSelection: IntRange,
    prefixLength: Int,
    contentLength: Int
): IntRange? {
    val contentStart = (lineSelection.first - prefixLength).coerceIn(0, contentLength)
    val contentEnd = (lineSelection.last + 1 - prefixLength).coerceIn(0, contentLength)
    return if (contentStart < contentEnd) contentStart until contentEnd else null
}

/**
 * Converts a line selection range to prefix selection range.
 * Returns null if there's no selection in the prefix area.
 */
fun lineSelectionToPrefixSelection(
    lineSelection: IntRange,
    prefixLength: Int
): IntRange? {
    val prefixStart = lineSelection.first.coerceIn(0, prefixLength)
    val prefixEnd = (lineSelection.last + 1).coerceIn(0, prefixLength)
    return if (prefixStart < prefixEnd) prefixStart until prefixEnd else null
}
