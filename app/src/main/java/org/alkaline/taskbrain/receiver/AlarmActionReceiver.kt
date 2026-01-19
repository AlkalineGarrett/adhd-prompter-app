package org.alkaline.taskbrain.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmUpdateEvent
import org.alkaline.taskbrain.data.SnoozeDuration
import org.alkaline.taskbrain.service.AlarmScheduler
import org.alkaline.taskbrain.service.AlarmUtils
import org.alkaline.taskbrain.service.UrgentStateManager

/**
 * Handles notification action buttons (Done, Snooze, Cancel).
 *
 * When a user taps an action button on an alarm notification:
 * 1. This receiver gets the corresponding ACTION_* intent
 * 2. The handler updates the alarm status in Firestore via AlarmRepository
 * 3. Scheduled alarm triggers are cancelled via AlarmScheduler
 * 4. The notification is dismissed
 *
 * The AlarmsScreen will reflect these changes when it resumes (via lifecycle observation).
 */
class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return

        when (intent.action) {
            ACTION_MARK_DONE -> handleMarkDone(context, alarmId)
            ACTION_SNOOZE -> handleSnooze(context, alarmId)
            ACTION_CANCEL -> handleCancel(context, alarmId)
        }
    }

    private fun handleMarkDone(context: Context, alarmId: String) {
        Log.d(TAG, "Marking alarm as done: $alarmId")

        CoroutineScope(Dispatchers.IO).launch {
            val repository = AlarmRepository()
            repository.markDone(alarmId)

            // Cancel any scheduled triggers
            val scheduler = AlarmScheduler(context)
            scheduler.cancelAlarm(alarmId)

            // Exit urgent state if this alarm was in it
            UrgentStateManager(context).exitUrgentState(alarmId)

            // Dismiss the notification
            dismissNotification(context, alarmId)

            // Notify observers to refresh their data
            AlarmUpdateEvent.notifyAlarmUpdated()
        }
    }

    private fun handleSnooze(context: Context, alarmId: String) {
        // Default snooze is 10 minutes
        handleSnoozeWithDuration(context, alarmId, SnoozeDuration.TEN_MINUTES)
    }

    fun handleSnoozeWithDuration(context: Context, alarmId: String, duration: SnoozeDuration) {
        Log.d(TAG, "Snoozing alarm for ${duration.minutes} minutes: $alarmId")

        CoroutineScope(Dispatchers.IO).launch {
            val repository = AlarmRepository()
            val result = repository.getAlarm(alarmId)

            result.fold(
                onSuccess = { alarm ->
                    if (alarm == null) return@fold

                    // Update snooze time in database
                    repository.snoozeAlarm(alarmId, duration)

                    // Schedule a new alarm for the snooze time
                    val scheduler = AlarmScheduler(context)
                    val snoozeTime = AlarmUtils.calculateSnoozeEndTime(duration.minutes)
                    val alarmType = AlarmUtils.determineAlarmTypeForSnooze(alarm)

                    scheduler.scheduleSnooze(alarmId, snoozeTime, alarmType)

                    // Exit urgent state if this alarm was in it
                    UrgentStateManager(context).exitUrgentState(alarmId)

                    // Dismiss the current notification
                    dismissNotification(context, alarmId)

                    // Notify observers to refresh their data
                    AlarmUpdateEvent.notifyAlarmUpdated()
                },
                onFailure = { e ->
                    Log.e(TAG, "Error snoozing alarm", e)
                }
            )
        }
    }

    private fun handleCancel(context: Context, alarmId: String) {
        Log.d(TAG, "Cancelling alarm: $alarmId")

        CoroutineScope(Dispatchers.IO).launch {
            val repository = AlarmRepository()
            repository.markCancelled(alarmId)

            // Cancel any scheduled triggers
            val scheduler = AlarmScheduler(context)
            scheduler.cancelAlarm(alarmId)

            // Exit urgent state if this alarm was in it
            UrgentStateManager(context).exitUrgentState(alarmId)

            // Dismiss the notification
            dismissNotification(context, alarmId)

            // Notify observers to refresh their data
            AlarmUpdateEvent.notifyAlarmUpdated()
        }
    }

    private fun dismissNotification(context: Context, alarmId: String) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(AlarmUtils.getNotificationId(alarmId))
    }

    companion object {
        private const val TAG = "AlarmActionReceiver"
        const val ACTION_MARK_DONE = "org.alkaline.taskbrain.ACTION_MARK_DONE"
        const val ACTION_SNOOZE = "org.alkaline.taskbrain.ACTION_SNOOZE"
        const val ACTION_CANCEL = "org.alkaline.taskbrain.ACTION_CANCEL"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_DURATION = "snooze_duration"
    }
}
