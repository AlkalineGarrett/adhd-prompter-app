package org.alkaline.taskbrain

import android.app.Application
import org.alkaline.taskbrain.service.NotificationChannels

/**
 * Application class for TaskBrain.
 * Initializes notification channels on app startup.
 */
class TaskBrainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createChannels(this)
    }
}
