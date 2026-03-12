package org.alkaline.taskbrain.ui.currentnote.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DIM_OVERLAY_COLOR = Color(0x99FFFFFF)
private val DIM_CORNER_OVERLAY_COLOR = Color(0xCCFFFFFF)

/**
 * Draws badge overlays on symbol characters in rendered text.
 *
 * Groups overlays by symbol character, finds all occurrences of each symbol
 * in the text, and draws the corresponding badge on each occurrence.
 *
 * @param content The rendered text (must match [textLayoutResult])
 * @param textLayoutResult The layout result from BasicText rendering
 * @param overlays One [SymbolOverlay] per symbol occurrence, in left-to-right order per symbol type
 * @param textMeasurer A TextMeasurer for drawing badge text
 */
fun Modifier.drawSymbolOverlays(
    content: String,
    textLayoutResult: TextLayoutResult?,
    overlays: List<SymbolOverlay>,
    textMeasurer: TextMeasurer
): Modifier {
    if (overlays.isEmpty()) return this

    // Pre-compute at composition time, not on every draw call
    val bySymbol = overlays.groupBy { it.symbol }
    val symbolPositions = bySymbol.keys.associateWith { symbol -> findAllPositions(content, symbol) }

    return this.drawWithContent {
        drawContent()

        val layout = textLayoutResult ?: return@drawWithContent

        for ((symbol, symbolOverlays) in bySymbol) {
            val positions = symbolPositions[symbol] ?: continue

            for ((index, charOffset) in positions.withIndex()) {
            val badge = symbolOverlays.getOrNull(index)?.badge ?: SymbolBadge.None
            if (badge is SymbolBadge.None) continue

            val symbolRect = try {
                layout.getBoundingBox(charOffset)
            } catch (e: Exception) {
                continue
            }

            val dimSymbol = when (badge) {
                is SymbolBadge.Centered -> badge.dimSymbol
                is SymbolBadge.Corner -> badge.dimSymbol && !badge.dimCornerOnly
                is SymbolBadge.None -> false
            }
            if (dimSymbol) {
                drawRect(
                    color = DIM_OVERLAY_COLOR,
                    topLeft = Offset(symbolRect.left, symbolRect.top),
                    size = Size(symbolRect.width, symbolRect.height)
                )
            }

            // Dim only the corner area if requested, with slight bleed into adjacent quadrants
            if (badge is SymbolBadge.Corner && badge.dimCornerOnly) {
                val bleed = symbolRect.width * 0.15f
                drawRect(
                    color = DIM_CORNER_OVERLAY_COLOR,
                    topLeft = Offset(symbolRect.center.x - bleed, symbolRect.top),
                    size = Size(symbolRect.width / 2f + bleed, symbolRect.height / 2f + bleed)
                )
            }

            when (badge) {
                is SymbolBadge.Centered -> drawCenteredBadge(
                    textMeasurer = textMeasurer,
                    text = badge.text,
                    color = badge.color,
                    symbolCenterX = symbolRect.center.x,
                    symbolCenterY = symbolRect.center.y,
                    fontSizePx = symbolRect.height * badge.sizeFraction
                )
                is SymbolBadge.Corner -> drawCornerBadge(
                    textMeasurer = textMeasurer,
                    badge = badge,
                    symbolRect = symbolRect,
                    fontSizePx = symbolRect.height * badge.sizeFraction
                )
                is SymbolBadge.None -> { /* handled above */ }
            }
        }
    }
}
}

/**
 * Returns whether any overlay has a visible badge.
 */
fun List<SymbolOverlay>.hasVisibleBadges(): Boolean =
    any { it.badge !is SymbolBadge.None }

/**
 * Finds all character offsets of a symbol string in [text].
 */
private fun findAllPositions(text: String, symbol: String): List<Int> {
    val positions = mutableListOf<Int>()
    for (i in text.indices) {
        if (text[i].toString() == symbol) {
            positions.add(i)
        }
    }
    return positions
}

/**
 * Draws a badge character centered on the symbol.
 */
private fun DrawScope.drawCenteredBadge(
    textMeasurer: TextMeasurer,
    text: String,
    color: Color,
    symbolCenterX: Float,
    symbolCenterY: Float,
    fontSizePx: Float
) {
    val style = TextStyle(
        fontSize = (fontSizePx / density).sp,
        fontWeight = FontWeight.Black,
        color = color
    )
    val measured = textMeasurer.measure(text, style)
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            x = symbolCenterX - measured.size.width / 2f,
            y = symbolCenterY - measured.size.height / 2f
        )
    )
}

/**
 * Draws a badge character in the upper-right quadrant of the symbol.
 * Draws a white outline behind the badge for contrast.
 */
private fun DrawScope.drawCornerBadge(
    textMeasurer: TextMeasurer,
    badge: SymbolBadge.Corner,
    symbolRect: Rect,
    fontSizePx: Float
) {
    val style = TextStyle(
        fontSize = (fontSizePx / density).sp,
        fontWeight = FontWeight.Black,
        color = badge.color
    )
    val measured = textMeasurer.measure(badge.text, style)
    val topLeft = Offset(
        x = symbolRect.center.x + (symbolRect.width / 2f - measured.size.width) / 2f,
        y = symbolRect.top - measured.size.height * 0.15f +
                symbolRect.height * badge.verticalOffsetFraction
    )

    // Draw white outline behind badge for contrast
    val outlineStyle = style.copy(color = Color.White)
    val outlineMeasured = textMeasurer.measure(badge.text, outlineStyle)
    val outlineOffsets = listOf(
        Offset(-1f, -1f), Offset(1f, -1f),
        Offset(-1f, 1f), Offset(1f, 1f),
        Offset(-1.5f, 0f), Offset(1.5f, 0f),
        Offset(0f, -1.5f), Offset(0f, 1.5f)
    )
    for (offset in outlineOffsets) {
        drawText(
            textLayoutResult = outlineMeasured,
            topLeft = topLeft + offset
        )
    }

    // Thicken by drawing the colored text at slight offsets too
    if (badge.thicken) {
        val thickenOffsets = listOf(
            Offset(-0.5f, 0f), Offset(0.5f, 0f),
            Offset(0f, -0.5f), Offset(0f, 0.5f)
        )
        for (offset in thickenOffsets) {
            drawText(
                textLayoutResult = measured,
                topLeft = topLeft + offset
            )
        }
    }

    drawText(
        textLayoutResult = measured,
        topLeft = topLeft
    )
}
