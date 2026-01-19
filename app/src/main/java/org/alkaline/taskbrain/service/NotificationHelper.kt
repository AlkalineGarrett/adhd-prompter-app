package org.alkaline.taskbrain.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmType
import org.alkaline.taskbrain.receiver.AlarmActionReceiver
import org.alkaline.taskbrain.ui.alarm.AlarmActivity

/**
 * Helper class for showing alarm notifications.
 * Extracted to allow both AlarmReceiver and AlarmScheduler to show notifications.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    /**
     * Shows a notification for the given alarm.
     * @param alarm The alarm to show notification for
     * @param alarmType The type of alarm notification
     * @param silent If true, the notification will be added silently without sound or popup
     * Returns true if the notification was shown, false otherwise.
     */
    fun showNotification(alarm: Alarm, alarmType: AlarmType = AlarmType.NOTIFY, silent: Boolean = false): Boolean {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted")
            return false
        }

        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager is null - cannot show notification")
            return false
        }

        return when (alarmType) {
            AlarmType.NOTIFY -> showReminderNotification(alarm, silent)
            AlarmType.URGENT -> showUrgentNotification(alarm)
            AlarmType.ALARM -> showAlarmNotification(alarm)
        }
    }

    private fun showReminderNotification(alarm: Alarm, silent: Boolean = false): Boolean {
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Reminder")
            .setContentText(alarm.lineContent)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent(alarm))
            .addAction(createDoneAction(alarm))
            .addAction(createCancelAction(alarm))

        if (silent) {
            // Silent notification: low priority, no sound/vibration, no heads-up
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
                .setNotificationSilent()
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        notificationManager?.notify(AlarmUtils.getNotificationId(alarm.id), builder.build())
        Log.d(TAG, "Showed notification for alarm: ${alarm.id} (silent=$silent)")
        return true
    }

    private fun showUrgentNotification(alarm: Alarm): Boolean {
        val fullScreenIntent = createFullScreenIntent(alarm, AlarmType.URGENT)

        val notification = NotificationCompat.Builder(context, NotificationChannels.URGENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Urgent Reminder")
            .setContentText(alarm.lineContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(createContentIntent(alarm))
            .addAction(createDoneAction(alarm))
            .build()

        notificationManager?.notify(AlarmUtils.getNotificationId(alarm.id), notification)
        Log.d(TAG, "Showed urgent notification for alarm: ${alarm.id}")
        return true
    }

    private fun showAlarmNotification(alarm: Alarm): Boolean {
        val fullScreenIntent = createFullScreenIntent(alarm, AlarmType.ALARM)

        val notification = NotificationCompat.Builder(context, NotificationChannels.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Alarm")
            .setContentText(alarm.lineContent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(createContentIntent(alarm))
            .addAction(createDoneAction(alarm))
            .addAction(createSnoozeAction(alarm))
            .build()

        notificationManager?.notify(AlarmUtils.getNotificationId(alarm.id), notification)
        Log.d(TAG, "Showed alarm notification for alarm: ${alarm.id}")
        return true
    }

    private fun createContentIntent(alarm: Alarm): PendingIntent {
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

    private fun createFullScreenIntent(alarm: Alarm, alarmType: AlarmType): PendingIntent {
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

    private fun createDoneAction(alarm: Alarm): NotificationCompat.Action {
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

    private fun createSnoozeAction(alarm: Alarm): NotificationCompat.Action {
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

    private fun createCancelAction(alarm: Alarm): NotificationCompat.Action {
        val intent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_CANCEL
            putExtra(AlarmActionReceiver.EXTRA_ALARM_ID, alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode() + 4000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_close,
            "Cancel",
            pendingIntent
        ).build()
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "NotificationHelper"
    }
}
