package org.alkaline.taskbrain.receiver

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmType
import org.alkaline.taskbrain.service.AlarmScheduler
import org.alkaline.taskbrain.service.AlarmUtils
import org.alkaline.taskbrain.service.NotificationChannels
import org.alkaline.taskbrain.ui.alarm.AlarmActivity
import org.alkaline.taskbrain.ui.alarm.AlarmErrorActivity

/**
 * Receives alarm triggers from AlarmManager and shows appropriate notifications.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")

        if (intent.action != AlarmScheduler.ACTION_ALARM_TRIGGERED) {
            Log.w(TAG, "Unexpected action: ${intent.action}, expected: ${AlarmScheduler.ACTION_ALARM_TRIGGERED}")
            return
        }

        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
        if (alarmId == null) {
            Log.e(TAG, "No alarm ID in intent")
            showErrorDialog(context, "Alarm Error", "No alarm ID provided in the trigger intent.")
            return
        }

        val alarmTypeName = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE)
        if (alarmTypeName == null) {
            Log.e(TAG, "No alarm type in intent")
            showErrorDialog(context, "Alarm Error", "No alarm type provided in the trigger intent.")
            return
        }

        val alarmType = try {
            AlarmType.valueOf(alarmTypeName)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid alarm type: $alarmTypeName", e)
            showErrorDialog(context, "Alarm Error", "Invalid alarm type: $alarmTypeName")
            return
        }

        Log.d(TAG, "Alarm received: $alarmId ($alarmType)")

        // Use goAsync() to keep the BroadcastReceiver alive while we do async work
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = AlarmRepository()
                val result = repository.getAlarm(alarmId)

                result.fold(
                    onSuccess = { alarm ->
                        if (alarm == null) {
                            Log.w(TAG, "Alarm not found in database: $alarmId")
                            showErrorDialogOnMain(context, "Alarm Not Found",
                                "The alarm ($alarmId) was not found in the database. It may have been deleted.")
                            return@fold
                        }

                        if (!AlarmUtils.shouldShowAlarm(alarm)) {
                            Log.d(TAG, "Alarm should not be shown (status=${alarm.status}, snoozedUntil=${alarm.snoozedUntil}): $alarmId")
                            // This is expected behavior, not an error - don't show dialog
                            return@fold
                        }

                        Log.d(TAG, "About to show alarm: type=$alarmType, content=${alarm.lineContent}")

                        withContext(Dispatchers.Main) {
                            when (alarmType) {
                                AlarmType.NOTIFY -> showNotification(context, alarm)
                                AlarmType.URGENT -> showUrgentNotification(context, alarm)
                                AlarmType.ALARM -> showAlarmWithFullScreen(context, alarm)
                            }
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error fetching alarm: $alarmId", e)
                        showErrorDialogOnMain(context, "Alarm Error",
                            "Failed to fetch alarm data: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in alarm receiver", e)
                showErrorDialogOnMain(context, "Alarm Error",
                    "Unexpected error processing alarm: ${e.message}")
            } finally {
                // Signal that we're done with async processing
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, alarm: Alarm) {
        Log.d(TAG, "showNotification called for ${alarm.id}")

        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted")
            showErrorDialog(context, "Permission Required",
                "Notification permission is not granted. Please enable notifications for TaskBrain in Settings.")
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager is null")
            showErrorDialog(context, "System Error",
                "Could not access NotificationManager. Please restart your device.")
            return
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Reminder")
            .setContentText(alarm.lineContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent(context, alarm))
            .addAction(createDoneAction(context, alarm))
            .addAction(createCancelAction(context, alarm))
            .build()

        notificationManager.notify(AlarmUtils.getNotificationId(alarm.id), notification)
        Log.d(TAG, "Showed notification for alarm: ${alarm.id}")
    }

    private fun showUrgentNotification(context: Context, alarm: Alarm) {
        Log.d(TAG, "showUrgentNotification called for ${alarm.id}")

        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted")
            showErrorDialog(context, "Permission Required",
                "Notification permission is not granted. Please enable notifications for TaskBrain in Settings.")
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager is null")
            showErrorDialog(context, "System Error",
                "Could not access NotificationManager. Please restart your device.")
            return
        }

        // Check full-screen intent permission on Android 14+
        val canUseFullScreen = checkFullScreenIntentPermission(context, notificationManager)
        Log.d(TAG, "Can use full-screen intent: $canUseFullScreen")

        if (!canUseFullScreen) {
            showErrorDialog(context, "Permission Required",
                "Full-screen intent permission is not granted.\n\n" +
                "To see urgent alarms over your lock screen, go to:\n" +
                "Settings → Apps → TaskBrain → Allow display over other apps")
        }

        val fullScreenIntent = createFullScreenIntent(context, alarm, AlarmType.URGENT)

        // Build notification with full-screen intent
        val notification = NotificationCompat.Builder(context, NotificationChannels.URGENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Urgent Reminder")
            .setContentText(alarm.lineContent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(createContentIntent(context, alarm))
            .addAction(createDoneAction(context, alarm))
            .build()

        notificationManager.notify(AlarmUtils.getNotificationId(alarm.id), notification)
        Log.d(TAG, "Posted urgent notification with fullScreenIntent for alarm: ${alarm.id}")
        // Note: Don't start activity directly - let fullScreenIntent handle it
        // This way it only shows full-screen when device is locked
    }

    private fun showAlarmWithFullScreen(context: Context, alarm: Alarm) {
        Log.d(TAG, "showAlarmWithFullScreen called for ${alarm.id}")

        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted")
            showErrorDialog(context, "Permission Required",
                "Notification permission is not granted. Please enable notifications for TaskBrain in Settings.")
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager == null) {
            Log.e(TAG, "NotificationManager is null")
            showErrorDialog(context, "System Error",
                "Could not access NotificationManager. Please restart your device.")
            return
        }

        // Check full-screen intent permission on Android 14+
        val canUseFullScreen = checkFullScreenIntentPermission(context, notificationManager)
        Log.d(TAG, "Can use full-screen intent: $canUseFullScreen")

        if (!canUseFullScreen) {
            showErrorDialog(context, "Permission Required",
                "Full-screen intent permission is not granted.\n\n" +
                "To see alarms over your lock screen, go to:\n" +
                "Settings → Apps → TaskBrain → Allow display over other apps")
        }

        val fullScreenIntent = createFullScreenIntent(context, alarm, AlarmType.ALARM)

        val notification = NotificationCompat.Builder(context, NotificationChannels.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Alarm")
            .setContentText(alarm.lineContent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(createContentIntent(context, alarm))
            .addAction(createDoneAction(context, alarm))
            .addAction(createSnoozeAction(context, alarm))
            .build()

        notificationManager.notify(AlarmUtils.getNotificationId(alarm.id), notification)
        Log.d(TAG, "Posted alarm notification with fullScreenIntent for alarm: ${alarm.id}")
        // Note: Don't start activity directly - let fullScreenIntent handle it
        // This way it only shows full-screen when device is locked
    }

    /**
     * Checks if the app can use full-screen intents.
     * On Android 14+, this requires explicit permission.
     */
    private fun checkFullScreenIntentPermission(context: Context, notificationManager: NotificationManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val canUse = notificationManager.canUseFullScreenIntent()
            if (!canUse) {
                Log.w(TAG, "Full-screen intent permission NOT granted on Android 14+")
            }
            canUse
        } else {
            // Before Android 14, full-screen intent is allowed with USE_FULL_SCREEN_INTENT permission
            true
        }
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

    private fun createCancelAction(context: Context, alarm: Alarm): NotificationCompat.Action {
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

    private fun hasNotificationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showErrorDialog(context: Context, title: String, message: String) {
        try {
            AlarmErrorActivity.show(context, title, message)
        } catch (e: Exception) {
            Log.e(TAG, "Could not show error dialog: ${e.message}")
        }
    }

    private suspend fun showErrorDialogOnMain(context: Context, title: String, message: String) {
        withContext(Dispatchers.Main) {
            showErrorDialog(context, title, message)
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
