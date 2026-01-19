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
     * Checks if the app can use full-screen intents.
     * On Android 14+ (API 34), this requires explicit permission granted in settings.
     * On older versions, the USE_FULL_SCREEN_INTENT manifest permission is sufficient.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.canUseFullScreenIntent() ?: false
        } else {
            // Before Android 14, USE_FULL_SCREEN_INTENT manifest permission is enough
            true
        }
    }

    /**
     * Returns true if full-screen intent permission needs to be granted in settings.
     * Only returns true on Android 14+ where this is a special permission.
     */
    fun needsFullScreenIntentPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    /**
     * Data class containing the overall permission status for alarm functionality.
     */
    data class AlarmPermissionStatus(
        val hasNotificationPermission: Boolean,
        val canScheduleExactAlarms: Boolean,
        val areNotificationsEnabled: Boolean,
        val canUseFullScreenIntent: Boolean
    ) {
        val canShowAlarms: Boolean
            get() = hasNotificationPermission && areNotificationsEnabled

        val canShowFullScreenAlarms: Boolean
            get() = canShowAlarms && canUseFullScreenIntent

        val hasAllPermissions: Boolean
            get() = hasNotificationPermission && canScheduleExactAlarms && areNotificationsEnabled && canUseFullScreenIntent
    }

    /**
     * Gets the complete permission status for alarm functionality.
     */
    fun getAlarmPermissionStatus(context: Context): AlarmPermissionStatus {
        return AlarmPermissionStatus(
            hasNotificationPermission = hasNotificationPermission(context),
            canScheduleExactAlarms = canScheduleExactAlarms(context),
            areNotificationsEnabled = areNotificationsEnabled(context),
            canUseFullScreenIntent = canUseFullScreenIntent(context)
        )
    }
}
