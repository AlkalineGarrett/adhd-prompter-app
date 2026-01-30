package org.alkaline.taskbrain.dsl.cache

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Represents a time at which a refresh directive should be re-evaluated.
 *
 * Phase 3: Time-based refresh analysis.
 */
sealed class TimeTrigger {
    /**
     * Check if this trigger should fire at the given time.
     */
    abstract fun shouldTriggerAt(now: LocalDateTime): Boolean

    /**
     * Get the next trigger time after the given time.
     * Returns null if no future trigger exists.
     */
    abstract fun nextTriggerAfter(now: LocalDateTime): LocalDateTime?

    /**
     * Whether this is a recurring trigger (fires daily) or one-time.
     */
    abstract val isRecurring: Boolean
}

/**
 * A trigger at a specific time each day.
 * Used for time comparisons like `time.gt("12:00")`.
 */
data class DailyTimeTrigger(
    val triggerTime: LocalTime
) : TimeTrigger() {
    override val isRecurring: Boolean = true

    override fun shouldTriggerAt(now: LocalDateTime): Boolean {
        return now.toLocalTime() == triggerTime
    }

    override fun nextTriggerAfter(now: LocalDateTime): LocalDateTime {
        val todayTrigger = now.toLocalDate().atTime(triggerTime)
        return if (now.isBefore(todayTrigger)) {
            todayTrigger
        } else {
            todayTrigger.plusDays(1)
        }
    }

    override fun toString(): String = "DailyTimeTrigger($triggerTime)"
}

/**
 * A one-time trigger on a specific date.
 * Used for date comparisons like `date.gt("2026-01-15")`.
 */
data class DateTrigger(
    val triggerDate: LocalDate
) : TimeTrigger() {
    override val isRecurring: Boolean = false

    override fun shouldTriggerAt(now: LocalDateTime): Boolean {
        return now.toLocalDate() == triggerDate
    }

    override fun nextTriggerAfter(now: LocalDateTime): LocalDateTime? {
        val trigger = triggerDate.atStartOfDay()
        return if (now.isBefore(trigger)) trigger else null
    }

    override fun toString(): String = "DateTrigger($triggerDate)"
}

/**
 * A one-time trigger at a specific date and time.
 * Used for datetime comparisons like `datetime.gt("2026-01-15T12:00")`.
 */
data class DateTimeTrigger(
    val triggerDateTime: LocalDateTime
) : TimeTrigger() {
    override val isRecurring: Boolean = false

    override fun shouldTriggerAt(now: LocalDateTime): Boolean {
        return now == triggerDateTime
    }

    override fun nextTriggerAfter(now: LocalDateTime): LocalDateTime? {
        return if (now.isBefore(triggerDateTime)) triggerDateTime else null
    }

    override fun toString(): String = "DateTimeTrigger($triggerDateTime)"
}

/**
 * Result of analyzing a refresh expression for triggers.
 */
data class RefreshAnalysis(
    /** List of detected triggers */
    val triggers: List<TimeTrigger>,
    /** Whether the analysis was successful */
    val success: Boolean = true,
    /** Error message if analysis failed */
    val error: String? = null
) {
    companion object {
        fun success(triggers: List<TimeTrigger>) = RefreshAnalysis(triggers)

        fun error(message: String) = RefreshAnalysis(
            triggers = emptyList(),
            success = false,
            error = message
        )
    }
}

/**
 * Information extracted from a time comparison in the AST.
 * Used during backtrace analysis.
 */
data class TimeComparison(
    /** The type of temporal value being compared (time, date, datetime) */
    val temporalType: TemporalType,
    /** The literal being compared against (e.g., "12:00") */
    val literal: String,
    /** Accumulated offset from .plus() operations (in minutes for time, days for date) */
    val offset: Long = 0,
    /** The comparison operator (gt, lt, eq, etc.) */
    val operator: ComparisonOperator
)

/**
 * Type of temporal value.
 */
enum class TemporalType {
    TIME,
    DATE,
    DATETIME
}

/**
 * Comparison operators.
 */
enum class ComparisonOperator {
    GT,   // greater than
    LT,   // less than
    GTE,  // greater than or equal
    LTE,  // less than or equal
    EQ,   // equal
    NE    // not equal
}
