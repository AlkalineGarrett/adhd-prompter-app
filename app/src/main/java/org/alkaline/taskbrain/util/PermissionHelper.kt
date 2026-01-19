package org.alkaline.taskbrain.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Centralized utility for checking Android permissions.
 * Provides methods to check various permissions required by the app.
 */
object PermissionHelper {

    /**
     * Checks if the app has notification permission.
     * On Android 13+ (API 33), POST_NOTIFICATIONS is a runtime permission.
     * On older versions, this always returns true.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if the app can schedule exact alarms.
     * On Android 12+ (API 31), exact alarms require special permission.
     * On older versions, this always returns true.
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    /**
     * Checks if notifications are enabled in system settings.
     * This is different from the runtime permission - the user can disable
     * notifications for the app in system settings.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return notificationManager?.areNotificationsEnabled() ?: false
    }

    /**
     * Returns true if notification permission needs to be requested at runtime.
     * Only returns true on Android 13+ where POST_NOTIFICATIONS is a runtime permission.
     */
    fun shouldRequestNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * Data class containing the overall permission status for alarm functionality.
     */
    data class AlarmPermissionStatus(
        val hasNotificationPermission: Boolean,
        val canScheduleExactAlarms: Boolean,
        val areNotificationsEnabled: Boolean
    ) {
        val canShowAlarms: Boolean
            get() = hasNotificationPermission && areNotificationsEnabled

        val hasAllPermissions: Boolean
            get() = hasNotificationPermission && canScheduleExactAlarms && areNotificationsEnabled
    }

    /**
     * Gets the complete permission status for alarm functionality.
     */
    fun getAlarmPermissionStatus(context: Context): AlarmPermissionStatus {
        return AlarmPermissionStatus(
            hasNotificationPermission = hasNotificationPermission(context),
            canScheduleExactAlarms = canScheduleExactAlarms(context),
            areNotificationsEnabled = areNotificationsEnabled(context)
        )
    }
}
