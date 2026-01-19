package org.alkaline.taskbrain.receiver

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmType
import org.alkaline.taskbrain.service.AlarmScheduler
import org.alkaline.taskbrain.service.AlarmUtils
import org.alkaline.taskbrain.service.NotificationChannels
import org.alkaline.taskbrain.ui.alarm.AlarmActivity

/**
 * Receives alarm triggers from AlarmManager and shows appropriate notifications.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM_TRIGGERED) return

        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        val alarmTypeName = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE) ?: return
        val alarmType = try {
            AlarmType.valueOf(alarmTypeName)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid alarm type: $alarmTypeName")
            return
        }

        Log.d(TAG, "Alarm received: $alarmId ($alarmType)")

        // Use a coroutine to fetch alarm data and show notification
        CoroutineScope(Dispatchers.IO).launch {
            val repository = AlarmRepository()
            val result = repository.getAlarm(alarmId)

            result.fold(
                onSuccess = { alarm ->
                    if (alarm == null) {
                        Log.w(TAG, "Alarm not found: $alarmId")
                        return@fold
                    }

                    if (!AlarmUtils.shouldShowAlarm(alarm)) {
                        Log.d(TAG, "Alarm should not be shown, skipping: $alarmId")
                        return@fold
                    }

                    when (alarmType) {
                        AlarmType.NOTIFY -> showNotification(context, alarm)
                        AlarmType.URGENT -> showUrgentNotification(context, alarm)
                        AlarmType.ALARM -> showAlarmWithFullScreen(context, alarm)
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Error fetching alarm: $alarmId", e)
                }
            )
        }
    }

    private fun showNotification(context: Context, alarm: Alarm) {
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted")
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Reminder")
            .setContentText(alarm.lineContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent(context, alarm))
            .addAction(createDoneAction(context, alarm))
            .build()

        notificationManager.notify(alarm.id.hashCode(), notification)
        Log.d(TAG, "Showed notification for alarm: ${alarm.id}")
    }

    private fun showUrgentNotification(context: Context, alarm: Alarm) {
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted")
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)

        val fullScreenIntent = createFullScreenIntent(context, alarm, AlarmType.URGENT)

        val notification = NotificationCompat.Builder(context, NotificationChannels.URGENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Urgent Reminder")
            .setContentText(alarm.lineContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(createContentIntent(context, alarm))
            .addAction(createDoneAction(context, alarm))
            .build()

        notificationManager.notify(alarm.id.hashCode(), notification)
        Log.d(TAG, "Showed urgent notification for alarm: ${alarm.id}")
    }

    private fun showAlarmWithFullScreen(context: Context, alarm: Alarm) {
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted")
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)

        val fullScreenIntent = createFullScreenIntent(context, alarm, AlarmType.ALARM)

        val notification = NotificationCompat.Builder(context, NotificationChannels.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Alarm")
            .setContentText(alarm.lineContent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(createContentIntent(context, alarm))
            .addAction(createDoneAction(context, alarm))
            .addAction(createSnoozeAction(context, alarm))
            .build()

        notificationManager.notify(alarm.id.hashCode(), notification)
        Log.d(TAG, "Showed alarm notification for alarm: ${alarm.id}")
    }

    private fun createContentIntent(context: Context, alarm: Alarm): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmActivity.EXTRA_ALARM_TYPE, AlarmType.NOTIFY.name)
        }
        return PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createFullScreenIntent(context: Context, alarm: Alarm, alarmType: AlarmType): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmActivity.EXTRA_ALARM_TYPE, alarmType.name)
        }
        return PendingIntent.getActivity(
            context,
            alarm.id.hashCode() + 1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDoneAction(context: Context, alarm: Alarm): NotificationCompat.Action {
        val intent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_MARK_DONE
            putExtra(AlarmActionReceiver.EXTRA_ALARM_ID, alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode() + 2000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_check_circle,
            "Done",
            pendingIntent
        ).build()
    }

    private fun createSnoozeAction(context: Context, alarm: Alarm): NotificationCompat.Action {
        val intent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_SNOOZE
            putExtra(AlarmActionReceiver.EXTRA_ALARM_ID, alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode() + 3000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_alarm,
            "Snooze",
            pendingIntent
        ).build()
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
