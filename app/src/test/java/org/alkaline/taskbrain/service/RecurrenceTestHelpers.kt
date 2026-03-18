package org.alkaline.taskbrain.service

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmStage
import org.alkaline.taskbrain.data.AlarmStageType
import org.alkaline.taskbrain.data.AlarmStatus
import org.alkaline.taskbrain.data.AlarmType
import org.alkaline.taskbrain.data.RecurrenceType
import org.alkaline.taskbrain.data.RecurringAlarm
import org.alkaline.taskbrain.data.RecurringAlarmStatus
import org.alkaline.taskbrain.data.TimeOfDay
import java.util.Calendar
import java.util.Date

/**
 * Shared test helpers for recurrence-related tests.
 */
object RecurrenceTestHelpers {

    val HOUR_MS = 60 * 60 * 1000L
    val DAY_MS = 24 * HOUR_MS

    /** Tomorrow at 9:00 AM (clean hour/minute so TimeOfDay round-trips). */
    val day1_9am: Long = run {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val day2_9am = day1_9am + DAY_MS
    val day3_9am = day1_9am + 2 * DAY_MS

    val threeStageConfig = listOf(
        AlarmStage(type = AlarmStageType.NOTIFICATION, offsetMs = 2 * HOUR_MS, enabled = true),
        AlarmStage(type = AlarmStageType.LOCK_SCREEN, offsetMs = 30 * 60 * 1000L, enabled = true),
        AlarmStage(type = AlarmStageType.SOUND_ALARM, offsetMs = 0, enabled = true)
    )

    val successScheduleResult = AlarmScheduleResult(
        alarmId = "test",
        scheduledTriggers = listOf(AlarmType.ALARM),
        skippedPastTriggers = emptyList(),
        noTriggersConfigured = false,
        usedExactAlarm = true
    )

    fun ts(millis: Long) = Timestamp(Date(millis))

    fun timeOfDay(millis: Long): TimeOfDay {
        val cal = Calendar.getInstance().apply { time = Date(millis) }
        return TimeOfDay(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    fun alarm(
        id: String,
        dueTimeMs: Long = day1_9am,
        recurringAlarmId: String = "rec1",
        status: AlarmStatus = AlarmStatus.PENDING,
        stages: List<AlarmStage> = threeStageConfig
    ) = Alarm(
        id = id, noteId = "note1", lineContent = "Daily standup",
        dueTime = ts(dueTimeMs), stages = stages,
        recurringAlarmId = recurringAlarmId, status = status
    )

    fun dailyRecurring(
        id: String = "rec1",
        currentAlarmId: String? = null,
        completionCount: Int = 0,
        repeatCount: Int? = null,
        lastCompletionDate: Timestamp? = null,
        anchorTimeOfDay: TimeOfDay? = null,
        status: RecurringAlarmStatus = RecurringAlarmStatus.ACTIVE
    ) = RecurringAlarm(
        id = id, noteId = "note1", lineContent = "Daily standup",
        recurrenceType = RecurrenceType.FIXED, rrule = "FREQ=DAILY",
        stages = threeStageConfig, currentAlarmId = currentAlarmId,
        completionCount = completionCount, repeatCount = repeatCount,
        lastCompletionDate = lastCompletionDate,
        anchorTimeOfDay = anchorTimeOfDay, status = status,
        createdAt = Timestamp.now()
    )
}
