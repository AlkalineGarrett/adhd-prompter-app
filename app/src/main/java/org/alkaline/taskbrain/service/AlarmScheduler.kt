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
 * Schedules and cancels alarms using Android's AlarmManager.
 * Each alarm can have up to 3 scheduled triggers (notify, urgent, alarm).
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules all time thresholds for an alarm.
     * Each non-null time threshold gets its own scheduled alarm.
     */
    fun scheduleAlarm(alarm: Alarm) {
        alarm.notifyTime?.let { timestamp ->
            scheduleAlarmTrigger(alarm.id, timestamp.toDate().time, AlarmType.NOTIFY)
        }
        alarm.urgentTime?.let { timestamp ->
            scheduleAlarmTrigger(alarm.id, timestamp.toDate().time, AlarmType.URGENT)
        }
        alarm.alarmTime?.let { timestamp ->
            scheduleAlarmTrigger(alarm.id, timestamp.toDate().time, AlarmType.ALARM)
        }
    }

    /**
     * Schedules a snooze alarm to fire at the specified time.
     */
    fun scheduleSnooze(alarmId: String, triggerAtMillis: Long, alarmType: AlarmType) {
        scheduleAlarmTrigger(alarmId, triggerAtMillis, alarmType)
    }

    /**
     * Cancels all scheduled triggers for an alarm.
     */
    fun cancelAlarm(alarmId: String) {
        AlarmType.entries.forEach { type ->
            cancelAlarmTrigger(alarmId, type)
        }
    }

    /**
     * Cancels a specific alarm type trigger.
     */
    fun cancelAlarmTrigger(alarmId: String, alarmType: AlarmType) {
        val pendingIntent = createPendingIntent(alarmId, alarmType)
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm trigger: $alarmId ($alarmType)")
    }

    private fun scheduleAlarmTrigger(alarmId: String, triggerAtMillis: Long, alarmType: AlarmType) {
        // Don't schedule alarms in the past
        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) {
            Log.d(TAG, "Skipping past alarm: $alarmId ($alarmType) - trigger time $triggerAtMillis <= now $now")
            return
        }

        val pendingIntent = createPendingIntent(alarmId, alarmType)
        val triggerInSeconds = (triggerAtMillis - now) / 1000

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled EXACT alarm: $alarmId ($alarmType) in ${triggerInSeconds}s")
                } else {
                    // Fall back to inexact alarm if exact alarm permission not granted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.w(TAG, "Scheduled INEXACT alarm: $alarmId ($alarmType) in ${triggerInSeconds}s - exact alarm permission not granted. Go to Settings > Apps > TaskBrain > Alarms & reminders to enable.")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled EXACT alarm: $alarmId ($alarmType) in ${triggerInSeconds}s")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule exact alarm - falling back to inexact", e)
            // Fall back to inexact alarm
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    /**
     * Checks if the app can schedule exact alarms.
     * On Android 12+, this requires user permission.
     */
    fun canScheduleExactAlarms(): Boolean {
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
