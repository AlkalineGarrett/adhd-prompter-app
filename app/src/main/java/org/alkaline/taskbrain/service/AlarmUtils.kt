package org.alkaline.taskbrain.service

import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmType

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
}
