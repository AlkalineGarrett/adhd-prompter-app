package org.alkaline.taskbrain.ui.currentnote.rendering

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.alkaline.taskbrain.ui.currentnote.LineState

/**
 * Cache for measured prefix widths and last known indent map.
 * Prefix cache: Key is the prefix string (e.g., "\t\t☐ "), value is the width in pixels.
 * Indent map cache: Stores the last known good visual line → indent mapping.
 */
class PrefixWidthCache {
    private val prefixCache = mutableMapOf<String, Float>()
    private var lastIndentMap: Map<Int, Float> = emptyMap()
    private var lastLayoutLineCount: Int = 0

    fun getOrMeasure(prefix: String, textMeasurer: TextMeasurer, textStyle: TextStyle): Float {
        return prefixCache.getOrPut(prefix) {
            if (prefix.isEmpty()) {
                0f
            } else {
                val result = textMeasurer.measure(prefix, textStyle)
                result.size.width.toFloat()
            }
        }
    }

    fun updateIndentMap(indentMap: Map<Int, Float>, lineCount: Int) {
        lastIndentMap = indentMap
        lastLayoutLineCount = lineCount
    }

    fun getLastIndentMap(): Map<Int, Float> = lastIndentMap
    fun getLastLayoutLineCount(): Int = lastLayoutLineCount

    fun clear() {
        prefixCache.clear()
        lastIndentMap = emptyMap()
        lastLayoutLineCount = 0
    }
}

/**
 * Extracts the prefix (leading tabs + bullet/checkbox) from a line.
 * Returns the prefix string that should be used for indent measurement.
 *
 * Delegates to LineState.extractPrefix for consistency.
 */
fun extractLinePrefix(line: String): String = LineState.extractPrefix(line)

/**
 * Calculates the hanging indent (in pixels) for a line by measuring the actual
 * rendered width of its prefix (tabs + bullet/checkbox).
 */
fun calculateLineIndentPx(
    line: String,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    cache: PrefixWidthCache
): Float {
    val prefix = extractLinePrefix(line)
    return cache.getOrMeasure(prefix, textMeasurer, textStyle)
}

/**
 * Builds a map from visual line index to indent amount in pixels.
 * Only wrapped lines (not the first visual line of a logical line) get indent.
 */
fun buildVisualLineIndentMap(
    text: String,
    textLayoutResult: TextLayoutResult?,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    cache: PrefixWidthCache
): Map<Int, Float> {
    if (textLayoutResult == null) return emptyMap()

    val result = mutableMapOf<Int, Float>()
    val lines = text.split("\n")
    var charOffset = 0

    lines.forEachIndexed { logicalLineIndex, line ->
        val lineStartOffset = charOffset
        val lineEndOffset = charOffset + line.length

        // Get the visual lines for this logical line
        val firstVisualLine = if (lineStartOffset < textLayoutResult.layoutInput.text.length) {
            textLayoutResult.getLineForOffset(lineStartOffset)
        } else {
            textLayoutResult.lineCount - 1
        }

        val lastVisualLine = if (lineEndOffset > 0 && lineEndOffset <= textLayoutResult.layoutInput.text.length) {
            textLayoutResult.getLineForOffset(maxOf(0, lineEndOffset - 1))
        } else if (line.isEmpty()) {
            firstVisualLine
        } else {
            textLayoutResult.lineCount - 1
        }

        // Calculate indent for this logical line using actual text measurement
        val indentPx = calculateLineIndentPx(line, textMeasurer, textStyle, cache)

        // Apply indent to wrapped visual lines (all except the first visual line of this logical line)
        for (visualLine in (firstVisualLine + 1)..lastVisualLine) {
            result[visualLine] = indentPx
        }

        // Move to next logical line (+1 for newline character)
        charOffset = lineEndOffset + 1
    }

    return result
}

/**
 * Modifier that applies hanging indent by translating wrapped lines during drawing.
 *
 * This works by:
 * 1. Measuring the actual rendered width of line prefixes (tabs + bullet/checkbox)
 * 2. Using the TextLayoutResult to identify which visual lines are wrapped
 * 3. During drawing, translating wrapped line portions to the right by the measured indent
 *
 * Note: This is a visual-only effect. Cursor and selection highlighting will be based on
 * the original layout positions, which may cause slight misalignment on wrapped lines.
 */
fun Modifier.hangingIndent(
    text: String,
    textLayoutResult: TextLayoutResult?,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    cache: PrefixWidthCache
): Modifier {
    if (textLayoutResult == null || text.isEmpty()) {
        return this
    }

    // Check if the layout result matches the current text
    val layoutText = textLayoutResult.layoutInput.text.text
    val isLayoutFresh = (layoutText == text)

    return this.drawWithContent {
        val indentMap: Map<Int, Float>
        val lineCount: Int

        if (isLayoutFresh) {
            // Layout matches text - calculate fresh indent map and cache it
            indentMap = buildVisualLineIndentMap(text, textLayoutResult, textMeasurer, textStyle, cache)
            lineCount = textLayoutResult.lineCount
            cache.updateIndentMap(indentMap, lineCount)
        } else {
            // Layout is stale - use cached indent map as fallback to avoid flashing
            indentMap = cache.getLastIndentMap()
            lineCount = cache.getLastLayoutLineCount()
        }

        if (indentMap.isEmpty() || lineCount == 0) {
            // No wrapped lines or no cached data, draw normally
            drawContent()
            return@drawWithContent
        }

        // Draw each visual line, translating wrapped ones
        // Use the actual layout for line positions (even if stale, positions are close enough)
        val actualLineCount = textLayoutResult.lineCount

        for (visualLineIndex in 0 until actualLineCount) {
            val lineTop = textLayoutResult.getLineTop(visualLineIndex)
            val lineBottom = textLayoutResult.getLineBottom(visualLineIndex)
            val indent = indentMap[visualLineIndex] ?: 0f

            // Clip to this line's vertical bounds and draw with translation
            clipRect(
                left = 0f,
                top = lineTop,
                right = size.width,
                bottom = lineBottom
            ) {
                translate(left = indent) {
                    this@drawWithContent.drawContent()
                }
            }
        }
    }
}

/**
 * Composable function to create and remember a PrefixWidthCache.
 */
@Composable
fun rememberPrefixWidthCache(): PrefixWidthCache {
    return remember { PrefixWidthCache() }
}
