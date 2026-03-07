package org.alkaline.taskbrain.dsl.directives

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.dsl.runtime.values.ScheduleFrequency

/**
 * Represents a scheduled directive action stored in Firestore.
 *
 * Schedules track periodic actions that should be executed on a schedule (daily, hourly, weekly).
 * The actual action (lambda) is stored as the original directive source text and re-parsed
 * at execution time to ensure correct evaluation context.
 *
 * Firestore path: users/{userId}/schedules/{scheduleId}
 */
data class Schedule(
    val id: String = "",
    val userId: String = "",
    val noteId: String = "",
    val notePath: String = "",
    val directiveHash: String = "",
    val directiveSource: String = "",
    val frequency: ScheduleFrequency = ScheduleFrequency.DAILY,
    val atTime: String? = null,
    /** Whether this schedule uses precise timing (AlarmManager) vs approximate (WorkManager). */
    val precise: Boolean = false,
    val nextExecution: Timestamp? = null,
    val status: ScheduleStatus = ScheduleStatus.ACTIVE,
    val lastExecution: Timestamp? = null,
    val lastError: String? = null,
    val failureCount: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    /**
     * Display-friendly description of the schedule.
     */
    val displayDescription: String
        get() {
            val timeStr = atTime?.let { " at $it" } ?: ""
            val preciseStr = if (precise) " ⏰" else ""
            val statusStr = if (status != ScheduleStatus.ACTIVE) " (${status.name.lowercase()})" else ""
            return "${frequency.identifier}$timeStr$preciseStr$statusStr"
        }

    /**
     * Whether the schedule should be executed.
     */
    val isActive: Boolean
        get() = status == ScheduleStatus.ACTIVE

    /**
     * Whether the schedule has failed too many times and should be paused.
     */
    val shouldPause: Boolean
        get() = failureCount >= MAX_FAILURES

    companion object {
        /**
         * Maximum number of consecutive failures before pausing the schedule.
         */
        const val MAX_FAILURES = 3
    }
}

/**
 * Status of a schedule.
 */
enum class ScheduleStatus {
    /** Schedule is active and will execute on schedule. */
    ACTIVE,

    /** Schedule is paused by user or due to errors. */
    PAUSED,

    /** Schedule execution failed and requires user attention. */
    FAILED,

    /** Schedule has been cancelled (note deleted or user action). */
    CANCELLED
}
