package org.alkaline.taskbrain.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Data class representing an urgent alarm for display on the lock screen.
 */
data class UrgentAlarmInfo(
    val id: String,
    val displayText: String,
    val alarmTimeMillis: Long
)

/**
 * Manages lock screen wallpaper for urgent alarm states.
 *
 * When an alarm enters the urgent window (< 30 min from alarm time),
 * sets a red wallpaper on the lock screen. Restores the original
 * wallpaper when all urgent alarms are done/cancelled/snoozed.
 *
 * Supports multiple urgent alarms, displaying them sorted by time.
 */
class LockScreenWallpaperManager(private val context: Context) {

    private val wallpaperManager: WallpaperManager = WallpaperManager.getInstance(context)
    private val backupFile: File = File(context.filesDir, BACKUP_FILENAME)

    /**
     * Adds an alarm to the urgent wallpaper display.
     * Backs up the current lock screen wallpaper on first alarm.
     *
     * @param alarmId The alarm ID
     * @param displayText The text to display (e.g., "Task name: due 2:30 PM")
     * @param alarmTimeMillis The alarm time in milliseconds (for sorting)
     * @return true if successful, false otherwise
     */
    fun setUrgentWallpaper(alarmId: String, displayText: String, alarmTimeMillis: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        try {
            // Back up current wallpaper if not already backed up (first urgent alarm)
            if (!backupFile.exists()) {
                backupCurrentWallpaper()
            }

            // Add this alarm to the list
            val alarms = getActiveAlarms().toMutableList()
            // Remove if already exists (update case)
            alarms.removeAll { it.id == alarmId }
            alarms.add(UrgentAlarmInfo(alarmId, displayText, alarmTimeMillis))
            saveActiveAlarms(alarms)

            // Create and set the urgent wallpaper
            updateWallpaper()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Removes an alarm from the urgent wallpaper display.
     * Restores the original wallpaper only if no urgent alarms remain.
     *
     * @param alarmId The alarm ID to remove
     * @return true if wallpaper was restored (no alarms left), false if urgent state continues
     */
    fun restoreWallpaper(alarmId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        try {
            // Remove this alarm from the list
            val alarms = getActiveAlarms().toMutableList()
            val removed = alarms.removeAll { it.id == alarmId }

            if (!removed) {
                return false
            }

            saveActiveAlarms(alarms)

            if (alarms.isEmpty()) {
                // No more urgent alarms - restore original wallpaper
                return restoreWallpaperInternal()
            } else {
                // Still have urgent alarms - update wallpaper with remaining alarms
                updateWallpaper()
                return false
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Force restores the original wallpaper regardless of active alarms.
     * Use this for cleanup scenarios.
     */
    fun forceRestoreWallpaper(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        clearActiveAlarms()
        return restoreWallpaperInternal()
    }

    /**
     * Checks if there's currently an urgent wallpaper active.
     */
    fun isUrgentWallpaperActive(): Boolean {
        return getActiveAlarms().isNotEmpty()
    }

    /**
     * Gets all active urgent alarm IDs.
     */
    fun getActiveAlarmIds(): List<String> {
        return getActiveAlarms().map { it.id }
    }

    private fun updateWallpaper() {
        val alarms = getActiveAlarms().sortedBy { it.alarmTimeMillis }
        val urgentBitmap = createUrgentWallpaper(alarms)
        wallpaperManager.setBitmap(
            urgentBitmap,
            null,
            true,
            WallpaperManager.FLAG_LOCK
        )
        urgentBitmap.recycle()
    }

    private fun restoreWallpaperInternal(): Boolean {
        try {
            if (backupFile.exists()) {
                // Restore from backup
                val inputStream = backupFile.inputStream()
                wallpaperManager.setStream(
                    inputStream,
                    null,
                    true,
                    WallpaperManager.FLAG_LOCK
                )
                inputStream.close()

                // Clean up
                backupFile.delete()
                clearActiveAlarms()
                return true
            } else {
                // No backup - just clear to system default
                wallpaperManager.clear(WallpaperManager.FLAG_LOCK)
                clearActiveAlarms()
                return true
            }
        } catch (e: Exception) {
            clearActiveAlarms()
            return false
        }
    }

    private fun backupCurrentWallpaper() {
        try {
            // Get current lock screen wallpaper
            val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.getDrawable(WallpaperManager.FLAG_LOCK)
                    ?: wallpaperManager.drawable // Fall back to system wallpaper
            } else {
                wallpaperManager.drawable
            }

            if (drawable == null) {
                return
            }

            // Convert to bitmap
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            // Save to file
            FileOutputStream(backupFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
        } catch (e: Exception) {
            // Ignore backup failures
        }
    }

    private fun createUrgentWallpaper(alarms: List<UrgentAlarmInfo>): Bitmap {
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

        // Build lines to display (up to 3)
        val lines = mutableListOf<String>()
        val sortedAlarms = alarms.sortedBy { it.alarmTimeMillis }

        // Add first two alarms
        sortedAlarms.take(2).forEach { alarm ->
            lines.add(alarm.displayText)
        }

        // If more than 2, add "more" indicator
        if (sortedAlarms.size > 2) {
            lines.add("(${sortedAlarms.size - 2} more alarm${if (sortedAlarms.size > 3) "s" else ""} due after)")
        }

        // Setup text paint - smaller to fit multiple lines
        val textPaint = Paint().apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            // Smaller text to accommodate multiple lines
            textSize = (width / 18f).coerceIn(32f, 72f)
        }

        // Calculate total height of all lines
        val lineHeight = textPaint.descent() - textPaint.ascent()
        val lineSpacing = lineHeight * 0.3f  // 30% spacing between lines
        val totalTextHeight = (lineHeight * lines.size) + (lineSpacing * (lines.size - 1))
        val margin = lineHeight * 0.5f  // Margin above/below all text

        // Calculate band dimensions - centered at 1/3 up from bottom
        val bandCenterY = height - (height / 3f)
        val bandTop = bandCenterY - (totalTextHeight / 2) - margin
        val bandBottom = bandCenterY + (totalTextHeight / 2) + margin

        // Draw yellow band
        val bandPaint = Paint().apply {
            color = BAND_COLOR
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, bandTop, width.toFloat(), bandBottom, bandPaint)

        // Draw text lines centered in the band
        val x = width / 2f
        var y = bandCenterY - (totalTextHeight / 2) - textPaint.ascent()

        for (line in lines) {
            canvas.drawText(line, x, y, textPaint)
            y += lineHeight + lineSpacing
        }

        return bitmap
    }

    private fun getActiveAlarms(): List<UrgentAlarmInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ACTIVE_ALARMS, null) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                UrgentAlarmInfo(
                    id = obj.getString("id"),
                    displayText = obj.getString("displayText"),
                    alarmTimeMillis = obj.getLong("alarmTimeMillis")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveActiveAlarms(alarms: List<UrgentAlarmInfo>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            val obj = JSONObject().apply {
                put("id", alarm.id)
                put("displayText", alarm.displayText)
                put("alarmTimeMillis", alarm.alarmTimeMillis)
            }
            array.put(obj)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_ALARMS, array.toString()).apply()
    }

    private fun clearActiveAlarms() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ACTIVE_ALARMS).apply()
    }

    companion object {
        private const val PREFS_NAME = "lock_screen_wallpaper_prefs"
        private const val KEY_ACTIVE_ALARMS = "active_alarms"
        private const val BACKUP_FILENAME = "wallpaper_backup.png"

        // Dark red color for urgent state
        private const val URGENT_COLOR = 0xFFB71C1C.toInt()
        // Yellow color for text band
        private const val BAND_COLOR = 0xFFFFEB3B.toInt()
    }
}
