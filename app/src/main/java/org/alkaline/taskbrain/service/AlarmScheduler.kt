package org.alkaline.taskbrain.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmType
import org.alkaline.taskbrain.receiver.AlarmReceiver

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
    private val urgentStateManager = UrgentStateManager(context)

    /**
     * Schedules all time thresholds for an alarm.
     * Each non-null time threshold gets its own scheduled alarm.
     *
     * Returns a result indicating what was scheduled.
     */
    fun scheduleAlarm(alarm: Alarm): AlarmScheduleResult {
        Log.d(TAG, "scheduleAlarm called for ${alarm.id}")
        Log.d(TAG, "  notifyTime: ${alarm.notifyTime}, urgentTime: ${alarm.urgentTime}, alarmTime: ${alarm.alarmTime}")

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

        // Schedule each trigger type
        val notifyResult = scheduleNotifyTrigger(alarm)
        val urgentResult = scheduleUrgentTrigger(alarm)
        val alarmResult = scheduleAlarmTimeTrigger(alarm)

        // Combine results
        val scheduledTriggers = listOfNotNull(
            if (notifyResult.scheduled) AlarmType.NOTIFY else null,
            if (urgentResult.scheduled) AlarmType.URGENT else null,
            if (alarmResult.scheduled) AlarmType.ALARM else null
        )
        val skippedPastTriggers = listOfNotNull(
            if (notifyResult.skippedPast) AlarmType.NOTIFY else null,
            if (urgentResult.skippedPast) AlarmType.URGENT else null,
            if (alarmResult.skippedPast) AlarmType.ALARM else null
        )
        val usedExactAlarm = notifyResult.usedExactAlarm || urgentResult.usedExactAlarm || alarmResult.usedExactAlarm
        val immediateNotificationShown = notifyResult.immediateAction || urgentResult.immediateAction

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
     * Result of scheduling a specific trigger type.
     */
    private data class TriggerTypeResult(
        val scheduled: Boolean = false,
        val skippedPast: Boolean = false,
        val usedExactAlarm: Boolean = false,
        val immediateAction: Boolean = false
    )

    /**
     * Schedules the NOTIFY trigger.
     * - If notifyTime is set, schedules at that time
     * - If notifyTime is not set but alarmTime is set, schedules 3 hours before alarm
     * - If the effective time is in the past, shows notification immediately
     */
    private fun scheduleNotifyTrigger(alarm: Alarm): TriggerTypeResult {
        val effectiveNotifyTime = AlarmUtils.calculateEffectiveNotifyTime(alarm)
        if (effectiveNotifyTime == null) {
            Log.d(TAG, "  No effective notify time calculated")
            return TriggerTypeResult()
        }

        Log.d(TAG, "  effectiveNotifyTime: ${java.util.Date(effectiveNotifyTime)}")
        val now = System.currentTimeMillis()

        if (effectiveNotifyTime <= now) {
            // Notify time is in the past - show notification immediately (silently)
            Log.d(TAG, "  NOTIFY time is in past, showing immediately")
            val shown = notificationHelper.showNotification(alarm, AlarmType.NOTIFY, silent = true)
            return if (shown) {
                TriggerTypeResult(scheduled = true, immediateAction = true)
            } else {
                Log.w(TAG, "  Could not show immediate notification (permission?)")
                TriggerTypeResult(skippedPast = true)
            }
        }

        // Schedule the notification for the effective time
        Log.d(TAG, "  Scheduling NOTIFY for ${java.util.Date(effectiveNotifyTime)}")
        val result = scheduleAlarmTrigger(alarm.id, effectiveNotifyTime, AlarmType.NOTIFY)
        return when {
            result.scheduled -> {
                Log.d(TAG, "  NOTIFY scheduled successfully")
                TriggerTypeResult(scheduled = true, usedExactAlarm = result.usedExactAlarm)
            }
            result.skippedPast -> {
                Log.w(TAG, "  NOTIFY skipped (past)")
                TriggerTypeResult(skippedPast = true)
            }
            else -> {
                Log.e(TAG, "  NOTIFY scheduling failed: ${result.error}")
                TriggerTypeResult()
            }
        }
    }

    /**
     * Schedules the URGENT trigger.
     * - If urgentTime is set, schedules at that time
     * - If urgentTime is not set but alarmTime is set, schedules 30 minutes before alarm
     * - If the effective time is in the past (in urgent window), enters urgent state immediately
     */
    private fun scheduleUrgentTrigger(alarm: Alarm): TriggerTypeResult {
        val effectiveUrgentTime = AlarmUtils.calculateEffectiveUrgentTime(alarm)
        if (effectiveUrgentTime == null) {
            Log.d(TAG, "  No effective urgent time calculated")
            return TriggerTypeResult()
        }

        Log.d(TAG, "  effectiveUrgentTime: ${java.util.Date(effectiveUrgentTime)}")
        val now = System.currentTimeMillis()

        if (effectiveUrgentTime <= now) {
            // We're already in the urgent window - enter urgent state immediately
            Log.d(TAG, "  URGENT time is in past, entering urgent state immediately")
            urgentStateManager.enterUrgentState(alarm, silent = true)
            return TriggerTypeResult(scheduled = true, immediateAction = true)
        }

        // Schedule the urgent trigger for later
        Log.d(TAG, "  Scheduling URGENT for ${java.util.Date(effectiveUrgentTime)}")
        val result = scheduleAlarmTrigger(alarm.id, effectiveUrgentTime, AlarmType.URGENT)
        return when {
            result.scheduled -> {
                Log.d(TAG, "  URGENT scheduled successfully")
                TriggerTypeResult(scheduled = true, usedExactAlarm = result.usedExactAlarm)
            }
            result.skippedPast -> {
                Log.w(TAG, "  URGENT skipped (past)")
                TriggerTypeResult(skippedPast = true)
            }
            else -> {
                Log.e(TAG, "  URGENT scheduling failed: ${result.error}")
                TriggerTypeResult()
            }
        }
    }

    /**
     * Schedules the ALARM trigger at the alarm time if set.
     */
    private fun scheduleAlarmTimeTrigger(alarm: Alarm): TriggerTypeResult {
        val timestamp = alarm.alarmTime ?: return TriggerTypeResult()
        val alarmTimeMillis = timestamp.toDate().time

        Log.d(TAG, "  Scheduling ALARM for ${java.util.Date(alarmTimeMillis)}")
        val result = scheduleAlarmTrigger(alarm.id, alarmTimeMillis, AlarmType.ALARM)
        return when {
            result.scheduled -> {
                Log.d(TAG, "  ALARM scheduled successfully")
                TriggerTypeResult(scheduled = true, usedExactAlarm = result.usedExactAlarm)
            }
            result.skippedPast -> {
                Log.w(TAG, "  ALARM skipped (past)")
                TriggerTypeResult(skippedPast = true)
            }
            else -> {
                Log.e(TAG, "  ALARM scheduling failed: ${result.error}")
                TriggerTypeResult()
            }
        }
    }

    /**
     * Result of scheduling a single alarm trigger via AlarmManager.
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
