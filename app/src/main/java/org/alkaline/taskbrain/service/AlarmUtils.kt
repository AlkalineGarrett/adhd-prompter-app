package org.alkaline.taskbrain.service

import android.content.Context
import android.text.format.DateFormat
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmType
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Utility functions for alarm-related logic.
 * Extracted for testability.
 */
object AlarmUtils {

    /**
     * Generates a unique request code for a PendingIntent.
     * Combines alarm ID hash with alarm type ordinal to ensure uniqueness.
     */
    fun generateRequestCode(alarmId: String, alarmType: AlarmType): Int {
        return alarmId.hashCode() * 10 + alarmType.ordinal
    }

    /**
     * Determines which alarm type to use when snoozing.
     * Returns the highest priority type that was set on the alarm.
     */
    fun determineAlarmTypeForSnooze(alarm: Alarm): AlarmType {
        return when {
            alarm.alarmTime != null -> AlarmType.ALARM
            alarm.urgentTime != null -> AlarmType.URGENT
            else -> AlarmType.NOTIFY
        }
    }

    /**
     * Gets all triggers that should be scheduled for an alarm.
     * Returns a list of (triggerTimeMillis, alarmType) pairs.
     * Only includes non-null time thresholds.
     */
    fun getTriggersToSchedule(alarm: Alarm): List<Pair<Long, AlarmType>> {
        val triggers = mutableListOf<Pair<Long, AlarmType>>()

        alarm.notifyTime?.let {
            triggers.add(it.toDate().time to AlarmType.NOTIFY)
        }
        alarm.urgentTime?.let {
            triggers.add(it.toDate().time to AlarmType.URGENT)
        }
        alarm.alarmTime?.let {
            triggers.add(it.toDate().time to AlarmType.ALARM)
        }

        return triggers
    }

    /**
     * Filters triggers to only include those in the future.
     */
    fun filterFutureTriggers(
        triggers: List<Pair<Long, AlarmType>>,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): List<Pair<Long, AlarmType>> {
        return triggers.filter { (time, _) -> time > currentTimeMillis }
    }

    /**
     * Calculates the snooze end time.
     */
    fun calculateSnoozeEndTime(
        durationMinutes: Int,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Long {
        return currentTimeMillis + durationMinutes * 60 * 1000L
    }

    /**
     * Checks if an alarm should be shown based on its status and snooze state.
     */
    fun shouldShowAlarm(
        alarm: Alarm,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        // Not pending - don't show
        if (alarm.status != org.alkaline.taskbrain.data.AlarmStatus.PENDING) {
            return false
        }

        // Snoozed and snooze hasn't expired - don't show
        if (alarm.snoozedUntil != null &&
            alarm.snoozedUntil.toDate().time > currentTimeMillis) {
            return false
        }

        return true
    }

    /**
     * Generates the notification ID for an alarm.
     * Used consistently across AlarmReceiver (to show) and AlarmActivity/AlarmActionReceiver (to dismiss).
     */
    fun getNotificationId(alarmId: String): Int {
        return alarmId.hashCode()
    }

    /**
     * Hours before alarm time to show lock screen notification if notifyTime is not set.
     */
    const val DEFAULT_NOTIFY_HOURS_BEFORE_ALARM = 3

    /**
     * Minutes before alarm time to show urgent notification if urgentTime is not set.
     */
    const val DEFAULT_URGENT_MINUTES_BEFORE_ALARM = 30

    /**
     * Calculates the effective notify time for an alarm.
     * - If notifyTime is set, returns notifyTime
     * - If notifyTime is not set but alarmTime is set, returns alarmTime - 3 hours
     * - If neither is set, returns null
     *
     * @param alarm The alarm to calculate notify time for
     * @return The effective notify time in milliseconds, or null if no notification should be scheduled
     */
    fun calculateEffectiveNotifyTime(alarm: Alarm): Long? {
        // If notifyTime is explicitly set, use it
        alarm.notifyTime?.let {
            return it.toDate().time
        }

        // If alarmTime is set, calculate 3 hours before
        alarm.alarmTime?.let {
            val alarmTimeMillis = it.toDate().time
            return alarmTimeMillis - (DEFAULT_NOTIFY_HOURS_BEFORE_ALARM * 60 * 60 * 1000L)
        }

        // No notification needed
        return null
    }

    /**
     * Calculates the effective urgent time for an alarm.
     * - If urgentTime is set, returns urgentTime
     * - If urgentTime is not set but alarmTime is set, returns alarmTime - 30 minutes
     * - If neither is set, returns null
     *
     * @param alarm The alarm to calculate urgent time for
     * @return The effective urgent time in milliseconds, or null if no urgent notification should be scheduled
     */
    fun calculateEffectiveUrgentTime(alarm: Alarm): Long? {
        // If urgentTime is explicitly set, use it
        alarm.urgentTime?.let {
            return it.toDate().time
        }

        // If alarmTime is set, calculate 30 minutes before
        alarm.alarmTime?.let {
            val alarmTimeMillis = it.toDate().time
            return alarmTimeMillis - (DEFAULT_URGENT_MINUTES_BEFORE_ALARM * 60 * 1000L)
        }

        // No urgent notification needed
        return null
    }

    /**
     * Determines if the effective notify time is in the past.
     *
     * @param effectiveNotifyTime The calculated notify time in milliseconds
     * @param currentTimeMillis The current time (for testing)
     * @return true if the notify time is in the past or equal to current time
     */
    fun isNotifyTimeInPast(
        effectiveNotifyTime: Long,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return effectiveNotifyTime <= currentTimeMillis
    }

    /**
     * Formats display text for wallpaper/notifications.
     * Includes alarm name and due time if available.
     *
     * @param context Context for accessing DateFormat preferences
     * @param alarm The alarm to format text for
     * @return Formatted display text (e.g., "Task name: due 2:30 PM")
     */
    fun formatDisplayText(context: Context, alarm: Alarm): String {
        val alarmTime = alarm.alarmTime?.toDate()
        return if (alarmTime != null) {
            // Use device's preferred time format (12h or 24h)
            val timeFormat = if (DateFormat.is24HourFormat(context)) {
                SimpleDateFormat("HH:mm", Locale.getDefault())
            } else {
                SimpleDateFormat("h:mm a", Locale.getDefault())
            }
            "${alarm.displayName}: due ${timeFormat.format(alarmTime)}"
        } else {
            alarm.displayName
        }
    }
}
