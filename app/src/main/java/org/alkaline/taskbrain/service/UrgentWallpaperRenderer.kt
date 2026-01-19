package org.alkaline.taskbrain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Renders the urgent wallpaper bitmap.
 *
 * Separates graphics rendering from wallpaper state management.
 * Creates a red background with a yellow text band containing alarm information.
 */
class UrgentWallpaperRenderer(private val context: Context) {

    /**
     * Creates an urgent wallpaper bitmap with the given alarms displayed.
     *
     * @param alarms List of urgent alarms to display (sorted by time recommended)
     * @return Bitmap for the lock screen wallpaper
     */
    fun createUrgentWallpaper(alarms: List<UrgentAlarmInfo>): Bitmap {
        // Get screen dimensions for the wallpaper
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        // Create a solid red bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(URGENT_COLOR)

        if (alarms.isEmpty()) {
            return bitmap
        }

        // Build lines to display and draw the text band
        val lines = prepareAlarmLines(alarms)
        drawTextBand(canvas, lines, width, height)

        return bitmap
    }

    /**
     * Prepares the text lines to display on the wallpaper.
     * Shows up to 2 alarms, plus a "more" indicator if needed.
     */
    private fun prepareAlarmLines(alarms: List<UrgentAlarmInfo>): List<String> {
        val lines = mutableListOf<String>()
        val sortedAlarms = alarms.sortedBy { it.alarmTimeMillis }

        // Add first two alarms
        sortedAlarms.take(2).forEach { alarm ->
            lines.add(alarm.displayText)
        }

        // If more than 2, add "more" indicator
        if (sortedAlarms.size > 2) {
            val extraCount = sortedAlarms.size - 2
            lines.add("($extraCount more alarm${if (extraCount > 1) "s" else ""} due after)")
        }

        return lines
    }

    /**
     * Draws a yellow text band with the alarm lines.
     */
    private fun drawTextBand(canvas: Canvas, lines: List<String>, width: Int, height: Int) {
        // Setup text paint
        val textPaint = Paint().apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            // Smaller text to accommodate multiple lines
            textSize = (width / 18f).coerceIn(32f, 72f)
        }

        // Calculate dimensions
        val dimensions = calculateBandDimensions(lines, textPaint, height)

        // Draw yellow band
        val bandPaint = Paint().apply {
            color = BAND_COLOR
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, dimensions.bandTop, width.toFloat(), dimensions.bandBottom, bandPaint)

        // Draw text lines centered in the band
        val x = width / 2f
        var y = dimensions.firstLineY

        for (line in lines) {
            canvas.drawText(line, x, y, textPaint)
            y += dimensions.lineHeight + dimensions.lineSpacing
        }
    }

    /**
     * Data class holding band dimension calculations.
     */
    private data class BandDimensions(
        val bandTop: Float,
        val bandBottom: Float,
        val firstLineY: Float,
        val lineHeight: Float,
        val lineSpacing: Float
    )

    /**
     * Calculates the dimensions for the text band.
     */
    private fun calculateBandDimensions(lines: List<String>, paint: Paint, screenHeight: Int): BandDimensions {
        val lineHeight = paint.descent() - paint.ascent()
        val lineSpacing = lineHeight * 0.3f  // 30% spacing between lines
        val totalTextHeight = (lineHeight * lines.size) + (lineSpacing * (lines.size - 1))
        val margin = lineHeight * 0.5f  // Margin above/below all text

        // Calculate band dimensions - centered at 1/3 up from bottom
        val bandCenterY = screenHeight - (screenHeight / 3f)
        val bandTop = bandCenterY - (totalTextHeight / 2) - margin
        val bandBottom = bandCenterY + (totalTextHeight / 2) + margin

        // Calculate first line Y position
        val firstLineY = bandCenterY - (totalTextHeight / 2) - paint.ascent()

        return BandDimensions(
            bandTop = bandTop,
            bandBottom = bandBottom,
            firstLineY = firstLineY,
            lineHeight = lineHeight,
            lineSpacing = lineSpacing
        )
    }

    companion object {
        // Dark red color for urgent state
        private const val URGENT_COLOR = 0xFFB71C1C.toInt()
        // Yellow color for text band
        private const val BAND_COLOR = 0xFFFFEB3B.toInt()
    }
}
