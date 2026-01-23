package org.alkaline.taskbrain

import android.app.Application
import org.alkaline.taskbrain.service.NotificationChannels
import org.alkaline.taskbrain.ui.currentnote.undo.UndoStatePersistence

/**
 * Application class for TaskBrain.
 * Initializes notification channels and clears session-scoped data on app startup.
 */
class TaskBrainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createChannels(this)
        // Clear persisted undo state on cold start (session boundary)
        UndoStatePersistence.clearAllPersistedState(this)
    }
}
