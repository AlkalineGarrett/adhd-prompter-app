package org.alkaline.taskbrain.dsl.directives

import com.google.firebase.Timestamp

/**
 * Represents an execution record for a scheduled directive action.
 *
 * Tracks whether a schedule was executed successfully, missed, or manually run.
 * Missed schedules (executedAt == null) are shown to the user for manual review.
 *
 * Firestore path: users/{userId}/scheduleExecutions/{executionId}
 */
data class ScheduleExecution(
    val id: String = "",
    val scheduleId: String = "",
    val userId: String = "",
    /** The original time the schedule was supposed to execute. */
    val scheduledFor: Timestamp? = null,
    /** When the schedule actually executed (null = missed, pending user review). */
    val executedAt: Timestamp? = null,
    /** Whether the execution was successful (only meaningful when executedAt is set). */
    val success: Boolean = false,
    /** Error message if execution failed. */
    val error: String? = null,
    /** True if this execution was triggered manually from the Schedules screen. */
    val manualRun: Boolean = false,
    /** When this record was created. */
    val createdAt: Timestamp? = null
) {
    /**
     * Whether this execution record represents a missed schedule awaiting user review.
     */
    val isMissed: Boolean
        get() = executedAt == null

    /**
     * Whether this execution was successful and completed.
     */
    val isCompleted: Boolean
        get() = executedAt != null && success

    /**
     * Whether this execution failed.
     */
    val isFailed: Boolean
        get() = executedAt != null && !success
}

/**
 * ScheduleExecution enriched with data from the associated Schedule.
 * Used for display in the Schedules screen UI.
 */
data class EnrichedExecution(
    val execution: ScheduleExecution,
    /** Note path from Schedule (e.g., "projects/2026"). */
    val notePath: String,
    /** First line of note content, used as note name. */
    val noteName: String,
    /** The directive source code. */
    val directiveSource: String
) {
    // Delegate common properties
    val id: String get() = execution.id
    val scheduleId: String get() = execution.scheduleId
    val scheduledFor get() = execution.scheduledFor
    val executedAt get() = execution.executedAt
    val success get() = execution.success
    val error get() = execution.error
    val manualRun get() = execution.manualRun
    val isMissed get() = execution.isMissed
    val isCompleted get() = execution.isCompleted
    val isFailed get() = execution.isFailed
}
