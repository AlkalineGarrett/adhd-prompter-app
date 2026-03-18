package org.alkaline.taskbrain.service

import android.util.Log
import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmStage
import org.alkaline.taskbrain.data.RecurringAlarm
import org.alkaline.taskbrain.data.RecurringAlarmRepository
import org.alkaline.taskbrain.data.TimeOfDay
import org.alkaline.taskbrain.data.toTimeOfDay
import org.alkaline.taskbrain.ui.currentnote.components.RecurrenceConfig

/**
 * Shared business logic for updating recurring alarm templates and propagating
 * time changes to pending instances. Used by both AlarmsViewModel and
 * CurrentNoteViewModel to avoid duplication.
 */
class RecurrenceTemplateManager(
    private val recurringRepo: RecurringAlarmRepository,
    private val alarmRepo: AlarmRepository,
    private val alarmStateManager: AlarmStateManager
) {

    /**
     * Updates an alarm instance's times, optionally propagating to the recurrence template.
     * Returns the schedule result from the instance update.
     */
    suspend fun updateInstanceTimes(
        alarm: Alarm,
        dueTime: Timestamp?,
        stages: List<AlarmStage>,
        alsoUpdateRecurrence: Boolean
    ): Result<AlarmScheduleResult> {
        val result = alarmStateManager.update(alarm, dueTime, stages)

        if (alsoUpdateRecurrence) {
            val recurringAlarmId = alarm.recurringAlarmId
                ?: return result
            val newAnchor = dueTime?.toTimeOfDay()
            recurringRepo.updateTimes(recurringAlarmId, newAnchor, stages).onFailure {
                Log.e(TAG, "Failed to propagate time change to recurrence template $recurringAlarmId", it)
            }
        }

        return result
    }

    /**
     * Updates the recurrence template's times and pattern, optionally propagating
     * time changes to all pending instances that still match the old template times.
     */
    suspend fun updateRecurrenceTemplate(
        recurringAlarmId: String,
        dueTime: Timestamp?,
        stages: List<AlarmStage>,
        recurrenceConfig: RecurrenceConfig,
        alsoUpdateMatchingInstances: Boolean
    ): Result<Unit> {
        val existing = recurringRepo.get(recurringAlarmId).getOrNull()
            ?: return Result.failure(
                IllegalStateException("Recurring alarm not found: $recurringAlarmId")
            )

        val oldAnchor = existing.anchorTimeOfDay
        val oldStages = existing.stages

        val updated = mergeTemplate(existing, dueTime, stages, recurrenceConfig)
        val updateResult = recurringRepo.update(updated)

        if (updateResult.isSuccess && alsoUpdateMatchingInstances && dueTime != null) {
            propagateTimesToMatchingInstances(
                recurringAlarmId, oldAnchor, oldStages, dueTime, stages
            )
        }

        return updateResult
    }

    /**
     * Updates all pending instances whose times match the old template anchor/stages
     * to use the new due time-of-day and stages.
     */
    suspend fun propagateTimesToMatchingInstances(
        recurringAlarmId: String,
        oldAnchor: TimeOfDay?,
        oldStages: List<AlarmStage>,
        newDueTime: Timestamp,
        newStages: List<AlarmStage>
    ) {
        val matchingInstances = recurringRepo.getMatchingPendingInstances(
            recurringAlarmId, oldAnchor, oldStages, alarmRepo
        )
        val newTimeOfDay = newDueTime.toTimeOfDay()
        for (instance in matchingInstances) {
            val instanceDueDate = instance.dueTime?.toDate() ?: continue
            val updatedDueTime = newTimeOfDay.onSameDateAs(instanceDueDate)
            alarmStateManager.update(instance, updatedDueTime, newStages).onFailure {
                Log.e(TAG, "Failed to propagate time change to instance ${instance.id}", it)
            }
        }
    }

    companion object {
        private const val TAG = "RecurrenceTemplateMgr"

        /**
         * Builds an updated [RecurringAlarm] by applying new recurrence config and stage
         * configuration while preserving the existing template's identity and state fields.
         */
        fun mergeTemplate(
            existing: RecurringAlarm,
            dueTime: Timestamp?,
            stages: List<AlarmStage>,
            config: RecurrenceConfig
        ): RecurringAlarm = RecurrenceConfigMapper.toRecurringAlarm(
            noteId = existing.noteId,
            lineContent = existing.lineContent,
            dueTime = dueTime,
            stages = stages,
            config = config
        ).copy(
            id = existing.id,
            userId = existing.userId,
            completionCount = existing.completionCount,
            lastCompletionDate = existing.lastCompletionDate,
            currentAlarmId = existing.currentAlarmId,
            // anchorTimeOfDay comes from the mapper (derived from dueTime) —
            // intentionally NOT preserved from existing, so edits update the anchor.
            status = existing.status,
            createdAt = existing.createdAt
        )
    }
}
