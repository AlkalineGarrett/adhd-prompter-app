package org.alkaline.taskbrain.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmType
import org.alkaline.taskbrain.service.AlarmPresenter
import org.alkaline.taskbrain.service.AlarmScheduler
import org.alkaline.taskbrain.service.AlarmTriggerHandler
import org.alkaline.taskbrain.service.NotificationHelper
import org.alkaline.taskbrain.service.RecurrenceScheduler
import org.alkaline.taskbrain.service.TriggerResult
import org.alkaline.taskbrain.service.UrgentStateManager
import org.alkaline.taskbrain.ui.alarm.AlarmErrorActivity

/**
 * Receives alarm triggers from AlarmManager.
 * Thin shell: parses intent, delegates to [AlarmTriggerHandler], presents results.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")

        if (intent.action != AlarmScheduler.ACTION_ALARM_TRIGGERED) {
            Log.w(TAG, "Unexpected action: ${intent.action}")
            return
        }

        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
        if (alarmId == null) {
            Log.e(TAG, "No alarm ID in intent")
            showError(context, "Alarm Error", "No alarm ID provided in the trigger intent.")
            return
        }

        val alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE)?.let {
            try { AlarmType.valueOf(it) } catch (e: IllegalArgumentException) { null }
        }
        if (alarmType == null) {
            Log.e(TAG, "Invalid or missing alarm type in intent")
            showError(context, "Alarm Error", "Invalid alarm type in trigger intent.")
            return
        }

        Log.d(TAG, "Alarm received: $alarmId ($alarmType)")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val handler = AlarmTriggerHandler(
                    alarmRepository = AlarmRepository(),
                    recurrenceScheduler = RecurrenceScheduler(context),
                    presenter = AndroidAlarmPresenter(context)
                )

                when (val result = handler.handle(alarmId, alarmType)) {
                    is TriggerResult.Shown ->
                        Log.d(TAG, "Showed ${result.alarmType} for alarm: ${result.alarm.id}")
                    is TriggerResult.Suppressed ->
                        Log.d(TAG, "Suppressed alarm $alarmId: ${result.reason}")
                    is TriggerResult.NotFound ->
                        showErrorOnMain(context, "Alarm Not Found",
                            "The alarm (${result.alarmId}) was not found in the database. It may have been deleted.")
                    is TriggerResult.Error ->
                        showErrorOnMain(context, "Alarm Error", result.message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in alarm receiver", e)
                showErrorOnMain(context, "Alarm Error", "Unexpected error processing alarm: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showError(context: Context, title: String, message: String) {
        try {
            AlarmErrorActivity.show(context, title, message)
        } catch (e: Exception) {
            Log.e(TAG, "Could not show error dialog: ${e.message}")
        }
    }

    private suspend fun showErrorOnMain(context: Context, title: String, message: String) {
        withContext(Dispatchers.Main) { showError(context, title, message) }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}

/**
 * Shows alarm triggers using Android notification/urgent state infrastructure.
 */
private class AndroidAlarmPresenter(private val context: Context) : AlarmPresenter {
    private val notificationHelper = NotificationHelper(context)
    private val urgentStateManager = UrgentStateManager(context)

    override fun present(alarm: Alarm, alarmType: AlarmType) {
        if (!notificationHelper.hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted")
            showError("Permission Required",
                "Notification permission is not granted. Please enable notifications for TaskBrain in Settings.")
            return
        }

        if ((alarmType == AlarmType.URGENT || alarmType == AlarmType.ALARM) &&
            !notificationHelper.canUseFullScreenIntent()) {
            Log.w(TAG, "Full-screen intent permission NOT granted on Android 14+")
            showError("Permission Required",
                "Full-screen intent permission is not granted.\n\n" +
                "To see alarms over your lock screen, go to:\n" +
                "Settings → Apps → TaskBrain → Allow display over other apps")
        }

        val success = when (alarmType) {
            AlarmType.URGENT -> urgentStateManager.enterUrgentState(alarm, silent = false)
            else -> notificationHelper.showNotification(alarm, alarmType, silent = false)
        }

        if (!success) {
            Log.e(TAG, "Failed to show $alarmType notification for alarm: ${alarm.id}")
            showError("Notification Error",
                "Failed to show $alarmType notification for alarm: ${alarm.displayName}")
        }
    }

    private fun showError(title: String, message: String) {
        try {
            AlarmErrorActivity.show(context, title, message)
        } catch (e: Exception) {
            Log.e(TAG, "Could not show error dialog: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AndroidAlarmPresenter"
    }
}
