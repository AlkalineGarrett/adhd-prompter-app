package org.alkaline.taskbrain.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmType
import org.alkaline.taskbrain.receiver.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Result of scheduling an alarm, indicating what was scheduled and any issues.
 */
data class AlarmScheduleResult(
    val alarmId: String,
    val scheduledTriggers: List<AlarmType>,
    val skippedPastTriggers: List<AlarmType>,
    val noTriggersConfigured: Boolean,
    val usedExactAlarm: Boolean,
    val immediateNotificationShown: Boolean = false
) {
    val success: Boolean
        get() = scheduledTriggers.isNotEmpty() || immediateNotificationShown

    val message: String
        get() = when {
            noTriggersConfigured -> "No alarm times were configured"
            scheduledTriggers.isEmpty() && !immediateNotificationShown && skippedPastTriggers.isNotEmpty() ->
                "All alarm times are in the past: ${skippedPastTriggers.joinToString()}"
            scheduledTriggers.isEmpty() && !immediateNotificationShown -> "No triggers could be scheduled"
            immediateNotificationShown && scheduledTriggers.isEmpty() -> "Showed immediate notification"
            immediateNotificationShown -> "Showed immediate notification, scheduled ${scheduledTriggers.size} trigger(s): ${scheduledTriggers.joinToString()}"
            else -> "Scheduled ${scheduledTriggers.size} trigger(s): ${scheduledTriggers.joinToString()}"
        }
}

/**
 * Schedules and cancels alarms using Android's AlarmManager.
 * Each alarm can have up to 3 scheduled triggers (notify, urgent, alarm).
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val notificationHelper = NotificationHelper(context)
    private val lockScreenWallpaperManager = LockScreenWallpaperManager(context)

    /**
     * Schedules all time thresholds for an alarm.
     * Each non-null time threshold gets its own scheduled alarm.
     *
     * Lock screen notification logic:
     * - If notifyTime is set, schedules notification at that time
     * - If notifyTime is not set but alarmTime is set, schedules notification 3 hours before alarm
     * - If the effective notify time is in the past, shows notification immediately
     *
     * Urgent full-screen activity logic:
     * - If urgentTime is set, schedules urgent trigger at that time
     * - If urgentTime is not set but alarmTime is set, schedules urgent trigger 30 minutes before alarm
     * - Shows red-themed full-screen activity over lock screen when triggered
     * - If the effective urgent time is in the past (alarm < 30 min away), shows urgent activity IMMEDIATELY
     *
     * Returns a result indicating what was scheduled.
     */
    fun scheduleAlarm(alarm: Alarm): AlarmScheduleResult {
        Log.d(TAG, "scheduleAlarm called for ${alarm.id}")
        Log.d(TAG, "  notifyTime: ${alarm.notifyTime}")
        Log.d(TAG, "  urgentTime: ${alarm.urgentTime}")
        Log.d(TAG, "  alarmTime: ${alarm.alarmTime}")

        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null - cannot schedule alarms")
            return AlarmScheduleResult(
                alarmId = alarm.id,
                scheduledTriggers = emptyList(),
                skippedPastTriggers = emptyList(),
                noTriggersConfigured = false,
                usedExactAlarm = false
            )
        }

        val scheduledTriggers = mutableListOf<AlarmType>()
        val skippedPastTriggers = mutableListOf<AlarmType>()
        var usedExactAlarm = false
        var immediateNotificationShown = false

        // Check if any triggers are configured
        val hasAnyTrigger = alarm.notifyTime != null || alarm.urgentTime != null || alarm.alarmTime != null
        if (!hasAnyTrigger) {
            Log.w(TAG, "No triggers configured for alarm ${alarm.id}")
            return AlarmScheduleResult(
                alarmId = alarm.id,
                scheduledTriggers = emptyList(),
                skippedPastTriggers = emptyList(),
                noTriggersConfigured = true,
                usedExactAlarm = false
            )
        }

        // Calculate effective notify time (notifyTime if set, or 3 hours before alarmTime)
        val effectiveNotifyTime = AlarmUtils.calculateEffectiveNotifyTime(alarm)
        Log.d(TAG, "  effectiveNotifyTime: $effectiveNotifyTime (${effectiveNotifyTime?.let { java.util.Date(it) }})")

        if (effectiveNotifyTime != null) {
            val now = System.currentTimeMillis()
            if (effectiveNotifyTime <= now) {
                // Notify time is in the past - show notification immediately (silently)
                Log.d(TAG, "  NOTIFY time is in past, showing immediately")
                immediateNotificationShown = notificationHelper.showNotification(alarm, AlarmType.NOTIFY, silent = true)
                if (!immediateNotificationShown) {
                    // If we couldn't show it (permission denied), track as skipped
                    Log.w(TAG, "  Could not show immediate notification (permission?)")
                    skippedPastTriggers.add(AlarmType.NOTIFY)
                }
            } else {
                // Schedule the notification for the effective time
                Log.d(TAG, "  Scheduling NOTIFY for ${java.util.Date(effectiveNotifyTime)}")
                val result = scheduleAlarmTrigger(alarm.id, effectiveNotifyTime, AlarmType.NOTIFY)
                if (result.scheduled) {
                    Log.d(TAG, "  NOTIFY scheduled successfully")
                    scheduledTriggers.add(AlarmType.NOTIFY)
                    usedExactAlarm = usedExactAlarm || result.usedExactAlarm
                } else if (result.skippedPast) {
                    Log.w(TAG, "  NOTIFY skipped (past)")
                    skippedPastTriggers.add(AlarmType.NOTIFY)
                } else {
                    Log.e(TAG, "  NOTIFY scheduling failed: ${result.error}")
                }
            }
        } else {
            Log.d(TAG, "  No effective notify time calculated")
        }

        // Calculate effective urgent time (urgentTime if set, or 30 minutes before alarmTime)
        // URGENT is a "state of being" - if we're already in the urgent window, show immediately
        val effectiveUrgentTime = AlarmUtils.calculateEffectiveUrgentTime(alarm)
        Log.d(TAG, "  effectiveUrgentTime: $effectiveUrgentTime (${effectiveUrgentTime?.let { java.util.Date(it) }})")

        if (effectiveUrgentTime != null) {
            val now = System.currentTimeMillis()
            if (effectiveUrgentTime <= now) {
                // We're already in the urgent window - show full-screen activity immediately
                Log.d(TAG, "  URGENT time is in past, showing full-screen activity immediately")
                showUrgentActivityImmediately(alarm)
                scheduledTriggers.add(AlarmType.URGENT) // Count as "handled"
            } else {
                // Schedule the urgent trigger for later
                Log.d(TAG, "  Scheduling URGENT for ${java.util.Date(effectiveUrgentTime)}")
                val result = scheduleAlarmTrigger(alarm.id, effectiveUrgentTime, AlarmType.URGENT)
                if (result.scheduled) {
                    Log.d(TAG, "  URGENT scheduled successfully")
                    scheduledTriggers.add(AlarmType.URGENT)
                    usedExactAlarm = usedExactAlarm || result.usedExactAlarm
                } else if (result.skippedPast) {
                    Log.w(TAG, "  URGENT skipped (past)")
                    skippedPastTriggers.add(AlarmType.URGENT)
                } else {
                    Log.e(TAG, "  URGENT scheduling failed: ${result.error}")
                }
            }
        } else {
            Log.d(TAG, "  No effective urgent time calculated")
        }

        alarm.alarmTime?.let { timestamp ->
            val alarmTimeMillis = timestamp.toDate().time
            Log.d(TAG, "  Scheduling ALARM for ${java.util.Date(alarmTimeMillis)}")
            val result = scheduleAlarmTrigger(alarm.id, alarmTimeMillis, AlarmType.ALARM)
            if (result.scheduled) {
                Log.d(TAG, "  ALARM scheduled successfully")
                scheduledTriggers.add(AlarmType.ALARM)
                usedExactAlarm = usedExactAlarm || result.usedExactAlarm
            } else if (result.skippedPast) {
                Log.w(TAG, "  ALARM skipped (past)")
                skippedPastTriggers.add(AlarmType.ALARM)
            } else {
                Log.e(TAG, "  ALARM scheduling failed: ${result.error}")
            }
        }

        Log.d(TAG, "scheduleAlarm result: scheduled=$scheduledTriggers, skipped=$skippedPastTriggers")
        return AlarmScheduleResult(
            alarmId = alarm.id,
            scheduledTriggers = scheduledTriggers,
            skippedPastTriggers = skippedPastTriggers,
            noTriggersConfigured = false,
            usedExactAlarm = usedExactAlarm,
            immediateNotificationShown = immediateNotificationShown
        )
    }

    /**
     * Result of scheduling a single alarm trigger.
     */
    private data class TriggerScheduleResult(
        val scheduled: Boolean,
        val skippedPast: Boolean,
        val usedExactAlarm: Boolean,
        val error: String? = null
    )

    /**
     * Schedules a snooze alarm to fire at the specified time.
     */
    fun scheduleSnooze(alarmId: String, triggerAtMillis: Long, alarmType: AlarmType): Boolean {
        val result = scheduleAlarmTrigger(alarmId, triggerAtMillis, alarmType)
        return result.scheduled
    }

    /**
     * Cancels all scheduled triggers for an alarm.
     */
    fun cancelAlarm(alarmId: String) {
        if (alarmManager == null) {
            Log.e(TAG, "Cannot cancel alarm - AlarmManager is null")
            return
        }
        AlarmType.entries.forEach { type ->
            cancelAlarmTrigger(alarmId, type)
        }
    }

    /**
     * Cancels a specific alarm type trigger.
     */
    fun cancelAlarmTrigger(alarmId: String, alarmType: AlarmType) {
        if (alarmManager == null) return
        val pendingIntent = createPendingIntent(alarmId, alarmType)
        alarmManager.cancel(pendingIntent)
    }

    private fun scheduleAlarmTrigger(alarmId: String, triggerAtMillis: Long, alarmType: AlarmType): TriggerScheduleResult {
        if (alarmManager == null) {
            return TriggerScheduleResult(scheduled = false, skippedPast = false, usedExactAlarm = false, error = "AlarmManager is null")
        }

        // Don't schedule alarms in the past
        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) {
            return TriggerScheduleResult(scheduled = false, skippedPast = true, usedExactAlarm = false)
        }

        val pendingIntent = createPendingIntent(alarmId, alarmType)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    return TriggerScheduleResult(scheduled = true, skippedPast = false, usedExactAlarm = true)
                } else {
                    // Fall back to inexact alarm if exact alarm permission not granted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    return TriggerScheduleResult(scheduled = true, skippedPast = false, usedExactAlarm = false)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return TriggerScheduleResult(scheduled = true, skippedPast = false, usedExactAlarm = true)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm - falling back to inexact", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return TriggerScheduleResult(scheduled = true, skippedPast = false, usedExactAlarm = false)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to schedule alarm", e2)
                return TriggerScheduleResult(scheduled = false, skippedPast = false, usedExactAlarm = false, error = e2.message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
            return TriggerScheduleResult(scheduled = false, skippedPast = false, usedExactAlarm = false, error = e.message)
        }
    }

    /**
     * Checks if the app can schedule exact alarms.
     * On Android 12+, this requires user permission.
     */
    fun canScheduleExactAlarms(): Boolean {
        if (alarmManager == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Activates the urgent state immediately.
     * Called when an alarm is created within the urgent window (< 30 minutes from alarm time).
     *
     * Actions:
     * - Sets lock screen wallpaper to red/urgent color
     * - Shows a silent notification (no sound/vibration, like the NOTIFY case)
     */
    private fun showUrgentActivityImmediately(alarm: Alarm) {
        // Format the display text with due time
        val displayText = formatWallpaperText(alarm)
        val alarmTimeMillis = alarm.alarmTime?.toDate()?.time ?: System.currentTimeMillis()

        // Set the lock screen wallpaper to urgent (red) with alarm text
        lockScreenWallpaperManager.setUrgentWallpaper(alarm.id, displayText, alarmTimeMillis)

        // Show a silent notification (no sound/vibration since we're showing it immediately)
        notificationHelper.showNotification(alarm, AlarmType.URGENT, silent = true)
    }

    private fun formatWallpaperText(alarm: Alarm): String {
        val alarmTime = alarm.alarmTime?.toDate()
        return if (alarmTime != null) {
            // Use device's preferred time format (12h or 24h)
            val timeFormat = if (DateFormat.is24HourFormat(context)) {
                SimpleDateFormat("HH:mm", Locale.getDefault())
            } else {
                SimpleDateFormat("h:mm a", Locale.getDefault())
            }
            "${alarm.lineContent}: due ${timeFormat.format(alarmTime)}"
        } else {
            alarm.lineContent
        }
    }

    private fun createPendingIntent(alarmId: String, alarmType: AlarmType): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGERED
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_TYPE, alarmType.name)
        }

        val requestCode = generateRequestCode(alarmId, alarmType)

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun generateRequestCode(alarmId: String, alarmType: AlarmType): Int {
        return AlarmUtils.generateRequestCode(alarmId, alarmType)
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        const val ACTION_ALARM_TRIGGERED = "org.alkaline.taskbrain.ACTION_ALARM_TRIGGERED"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_TYPE = "alarm_type"
    }
}
