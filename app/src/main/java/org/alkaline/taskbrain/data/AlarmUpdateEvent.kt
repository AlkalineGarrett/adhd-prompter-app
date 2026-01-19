package org.alkaline.taskbrain.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton event bus for alarm updates.
 *
 * When an alarm is modified externally (e.g., via notification action),
 * emit an event here. Observers (like AlarmsViewModel) can collect
 * from the flow and refresh their data.
 */
object AlarmUpdateEvent {
    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updates = _updates.asSharedFlow()

    /**
     * Call this when an alarm has been modified and UIs should refresh.
     */
    fun notifyAlarmUpdated() {
        _updates.tryEmit(Unit)
    }
}
