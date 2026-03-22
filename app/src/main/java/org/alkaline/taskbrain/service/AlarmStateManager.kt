package org.alkaline.taskbrain.service

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmStage
import org.alkaline.taskbrain.data.SnoozeDuration

/**
 * Centralizes all alarm state transition side effects.
 *
 * Every transition that deactivates an alarm (done, cancelled, deleted, updated)
 * must: cancel scheduled triggers, exit urgent state (restore wallpaper),
 * and dismiss notifications. This class ensures no side effect is forgotten.
 *
 * All suspend functions return Result<T> so callers can handle errors.
 */
class AlarmStateManager(
    private val repository: AlarmRepository = AlarmRepository(),
    private val scheduler: AlarmScheduler,
    private val urgentStateManager: UrgentStateManager,
    private val notificationManager: NotificationManager?,
    private val recurrenceScheduler: RecurrenceScheduler? = null
) {

    constructor(context: Context) : this(
        repository = AlarmRepository(),
        scheduler = AlarmScheduler(context),
        urgentStateManager = UrgentStateManager(context),
        notificationManager = context.getSystemService(NotificationManager::class.java),
        recurrenceScheduler = RecurrenceScheduler(context)
    )

    /**
     * Cleans up all system side effects for an alarm: cancels scheduled triggers,
     * exits urgent state (restores wallpaper if no other urgent alarms remain),
     * and dismisses any active notification.
     */
    fun deactivate(alarmId: String) {
        scheduler.cancelAlarm(alarmId)
        urgentStateManager.exitUrgentState(alarmId)
        notificationManager?.cancel(AlarmUtils.getNotificationId(alarmId))
    }

    /**
     * Creates a new alarm: persists to DB, fetches with server-set fields, schedules triggers.
     *
     * @return (alarmId, scheduleResult) on success.
     */
    suspend fun create(alarm: Alarm): Result<Pair<String, AlarmScheduleResult>> {
        return repository.createAlarm(alarm).mapCatching { alarmId ->
            val createdAlarm = repository.getAlarm(alarmId).getOrNull()
                ?: alarm.copy(id = alarmId)
            val scheduleResult = scheduler.scheduleAlarm(createdAlarm)
            alarmId to scheduleResult
        }.also { result ->
            result.onFailure { Log.e(TAG, "Error creating alarm", it) }
        }
    }

    /**
     * Snoozes an alarm: updates DB, deactivates current state, schedules snooze trigger.
     */
    suspend fun snooze(alarmId: String, duration: SnoozeDuration): Result<Unit> {
        return runCatching {
            val alarm = repository.getAlarm(alarmId).getOrThrow()
                ?: throw IllegalStateException("Alarm not found: $alarmId")

            repository.snoozeAlarm(alarmId, duration).getOrThrow()
            deactivate(alarmId)

            val snoozeTime = AlarmUtils.calculateSnoozeEndTime(duration.minutes)
            val alarmType = AlarmUtils.determineAlarmTypeForSnooze(alarm)
            scheduler.scheduleSnooze(alarmId, snoozeTime, alarmType)
            Unit
        }.also { result ->
            result.onFailure { Log.e(TAG, "Error snoozing alarm: $alarmId", it) }
        }
    }

    /**
     * Dismisses an alarm's active presentation: exits urgent state and dismisses notification.
     * Does NOT cancel future PendingIntents (the alarm remains scheduled).
     */
    fun dismiss(alarmId: String) {
        urgentStateManager.exitUrgentState(alarmId)
        notificationManager?.cancel(AlarmUtils.getNotificationId(alarmId))
    }

    suspend fun markDone(alarmId: String): Result<Unit> {
        deactivate(alarmId)
        return repository.markDone(alarmId).also { result ->
            result.onSuccess { scheduleNextRecurrence(alarmId, completed = true) }
            result.onFailure { Log.e(TAG, "Error marking alarm done: $alarmId", it) }
        }
    }

    suspend fun markCancelled(alarmId: String): Result<Unit> {
        deactivate(alarmId)
        return repository.markCancelled(alarmId).also { result ->
            result.onSuccess { scheduleNextRecurrence(alarmId, completed = false) }
            result.onFailure { Log.e(TAG, "Error marking alarm cancelled: $alarmId", it) }
        }
    }

    /**
     * If this alarm belongs to a recurring alarm, triggers creation of the next instance.
     */
    private suspend fun scheduleNextRecurrence(alarmId: String, completed: Boolean) {
        val scheduler = recurrenceScheduler ?: return
        val alarm = repository.getAlarm(alarmId)
            .onFailure { Log.e(TAG, "Failed to fetch alarm $alarmId for next recurrence", it) }
            .getOrNull() ?: return
        if (alarm.recurringAlarmId == null) return

        try {
            if (completed) {
                scheduler.onInstanceCompleted(alarm)
            } else {
                scheduler.onInstanceCancelled(alarm)
            }
        } catch (e: Exception) {
            // RecurrenceScheduler surfaces errors to the user internally via AlarmErrorActivity.
            // Catch here to prevent crashing the markDone/markCancelled caller.
            Log.e(TAG, "Error scheduling next recurrence for $alarmId", e)
        }
    }

    suspend fun delete(alarmId: String): Result<Unit> {
        deactivate(alarmId)
        return repository.deleteAlarm(alarmId).also { result ->
            result.onFailure { Log.e(TAG, "Error deleting alarm: $alarmId", it) }
        }
    }

    /**
     * Updates an alarm's due time and stages, reschedules triggers, and clears stale
     * urgent state/notifications from the old schedule.
     *
     * @return Result containing the schedule result on success.
     */
    suspend fun update(
        alarm: Alarm,
        dueTime: Timestamp?,
        stages: List<AlarmStage>
    ): Result<AlarmScheduleResult> {
        // Reset notifiedStageType when timings change so sync will re-sound.
        // Compare only timing-relevant fields (type, offsetMs, absoluteTimeOfDay), not `enabled`.
        val timingsChanged = alarm.dueTime != dueTime ||
            alarm.stages.map { Triple(it.type, it.offsetMs, it.absoluteTimeOfDay) } !=
            stages.map { Triple(it.type, it.offsetMs, it.absoluteTimeOfDay) }
        val updatedAlarm = alarm.copy(
            dueTime = dueTime,
            stages = stages,
            notifiedStageType = if (timingsChanged) null else alarm.notifiedStageType
        )

        return repository.updateAlarm(updatedAlarm).map {
            deactivate(alarm.id)
            scheduler.scheduleAlarm(updatedAlarm)
        }.also { result ->
            result.onFailure { Log.e(TAG, "Error updating alarm: ${alarm.id}", it) }
        }
    }

    /**
     * Reactivates a done/cancelled alarm and reschedules its triggers.
     *
     * @return Result containing the schedule result on success, or null if the alarm
     *         was not found after reactivation.
     */
    suspend fun reactivate(alarmId: String): Result<AlarmScheduleResult?> {
        return repository.reactivateAlarm(alarmId).map {
            repository.getAlarm(alarmId).getOrNull()?.let { alarm ->
                scheduler.scheduleAlarm(alarm)
            }
        }.also { result ->
            result.onFailure { Log.e(TAG, "Error reactivating alarm: $alarmId", it) }
        }
    }

    companion object {
        private const val TAG = "AlarmStateManager"
    }
}
