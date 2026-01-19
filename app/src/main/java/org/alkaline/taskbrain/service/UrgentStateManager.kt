package org.alkaline.taskbrain.service

import android.content.Context
import android.util.Log
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmType

/**
 * Single point of control for entering/exiting urgent state.
 *
 * Urgent state includes:
 * - Red lock screen wallpaper with alarm text
 * - Notification (silent when entering programmatically, with sound when triggered by AlarmManager)
 *
 * This manager coordinates both components to ensure consistent state.
 */
class UrgentStateManager(private val context: Context) {

    private val wallpaperManager = LockScreenWallpaperManager(context)
    private val notificationHelper = NotificationHelper(context)

    /**
     * Enters the urgent state for an alarm.
     *
     * @param alarm The alarm entering urgent state
     * @param silent If true, notification will be silent (no sound/vibration)
     * @return true if urgent state was entered successfully
     */
    fun enterUrgentState(alarm: Alarm, silent: Boolean = false): Boolean {
        Log.d(TAG, "Entering urgent state for alarm: ${alarm.id}, silent=$silent")

        // Set the lock screen wallpaper
        val displayText = AlarmUtils.formatDisplayText(context, alarm)
        val alarmTimeMillis = alarm.alarmTime?.toDate()?.time ?: System.currentTimeMillis()
        val wallpaperSuccess = wallpaperManager.setUrgentWallpaper(alarm.id, displayText, alarmTimeMillis)

        if (!wallpaperSuccess) {
            Log.w(TAG, "Failed to set urgent wallpaper for alarm: ${alarm.id}")
        }

        // Show the notification
        val notificationSuccess = notificationHelper.showNotification(alarm, AlarmType.URGENT, silent)

        if (!notificationSuccess) {
            Log.w(TAG, "Failed to show urgent notification for alarm: ${alarm.id}")
        }

        return wallpaperSuccess || notificationSuccess
    }

    /**
     * Exits the urgent state for an alarm.
     *
     * @param alarmId The ID of the alarm exiting urgent state
     * @return true if wallpaper was fully restored (no other urgent alarms remain)
     */
    fun exitUrgentState(alarmId: String): Boolean {
        Log.d(TAG, "Exiting urgent state for alarm: $alarmId")
        return wallpaperManager.restoreWallpaper(alarmId)
    }

    /**
     * Checks if an alarm is in the urgent state (has urgent wallpaper active).
     *
     * @param alarmId The ID of the alarm to check
     * @return true if the alarm is in urgent state
     */
    fun isInUrgentState(alarmId: String): Boolean {
        return wallpaperManager.getActiveAlarmIds().contains(alarmId)
    }

    /**
     * Checks if any alarm is in urgent state.
     *
     * @return true if any urgent wallpaper is active
     */
    fun hasActiveUrgentState(): Boolean {
        return wallpaperManager.isUrgentWallpaperActive()
    }

    /**
     * Gets all alarm IDs currently in urgent state.
     *
     * @return List of alarm IDs with active urgent state
     */
    fun getActiveUrgentAlarmIds(): List<String> {
        return wallpaperManager.getActiveAlarmIds()
    }

    /**
     * Force exits all urgent states and restores wallpaper.
     * Use for cleanup scenarios.
     *
     * @return true if wallpaper was restored
     */
    fun forceExitAllUrgentStates(): Boolean {
        Log.d(TAG, "Force exiting all urgent states")
        return wallpaperManager.forceRestoreWallpaper()
    }

    companion object {
        private const val TAG = "UrgentStateManager"
    }
}
