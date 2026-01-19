package org.alkaline.taskbrain.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager

/**
 * Notification channels for alarms and reminders.
 * Must be created before any notifications can be posted.
 */
object NotificationChannels {
    const val REMINDER_CHANNEL_ID = "reminders"
    const val URGENT_CHANNEL_ID = "urgent_reminders"
    const val ALARM_CHANNEL_ID = "alarms"

    /**
     * Creates all notification channels for the app.
     * Safe to call multiple times - Android ignores recreating existing channels.
     */
    fun createChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        // Regular notification (notifyTime)
        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification reminders for your tasks"
        }

        // Urgent notification (urgentTime) - higher priority with lights
        val urgentChannel = NotificationChannel(
            URGENT_CHANNEL_ID,
            "Urgent Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent reminders that need immediate attention"
            enableLights(true)
            lightColor = android.graphics.Color.RED
        }

        // Alarm (alarmTime) - full screen intent with alarm sound
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Audible alarms for critical reminders"
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            setSound(alarmSound, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
        }

        notificationManager.createNotificationChannels(
            listOf(reminderChannel, urgentChannel, alarmChannel)
        )
    }
}
