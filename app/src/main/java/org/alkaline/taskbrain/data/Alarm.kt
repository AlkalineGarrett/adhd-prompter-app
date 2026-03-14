package org.alkaline.taskbrain.data

import com.google.firebase.Timestamp
import java.util.Date

/**
 * A single stage of an alarm (e.g., sound alarm, lock screen, notification).
 * Times are expressed as offsets before dueTime, or optionally as absolute times.
 */
data class AlarmStage(
    val type: AlarmStageType,
    /** Milliseconds before dueTime (positive = before due, 0 = at due). */
    val offsetMs: Long = 0,
    val enabled: Boolean = true,
    /** If non-null, overrides offset-based time computation. */
    val absoluteTime: Timestamp? = null
) {
    /** Resolves this stage's trigger time given the alarm's dueTime. */
    fun resolveTime(dueTime: Timestamp): Timestamp {
        absoluteTime?.let { return it }
        val dueMs = dueTime.toDate().time
        return Timestamp(Date(dueMs - offsetMs))
    }
}

enum class AlarmStageType {
    SOUND_ALARM,    // Audible alarm with snooze
    LOCK_SCREEN,    // Lock screen red tint / full-screen activity
    NOTIFICATION;   // Status bar notification

    /** Maps to the existing AlarmType used in PendingIntent extras. */
    fun toAlarmType(): AlarmType = when (this) {
        SOUND_ALARM -> AlarmType.ALARM
        LOCK_SCREEN -> AlarmType.URGENT
        NOTIFICATION -> AlarmType.NOTIFY
    }
}

/**
 * Represents an alarm/reminder for a note line.
 * Alarms have a due time and configurable stages that trigger at offsets before the due time.
 */
data class Alarm(
    val id: String = "",
    val userId: String = "",
    val noteId: String = "",
    val lineContent: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,

    val dueTime: Timestamp? = null,
    val stages: List<AlarmStage> = DEFAULT_STAGES,

    val status: AlarmStatus = AlarmStatus.PENDING,
    val snoozedUntil: Timestamp? = null,

    /** If this alarm was spawned by a recurring alarm template, its ID. */
    val recurringAlarmId: String? = null
) {
    /**
     * Display-friendly name with bullets, checkboxes, tabs, and alarm symbols removed.
     * Computed lazily and cached.
     */
    val displayName: String by lazy {
        var result = lineContent

        // Remove leading tabs
        result = result.trimStart('\t')

        // Remove bullet/checkbox prefixes
        DISPLAY_PREFIXES.forEach { prefix ->
            if (result.startsWith(prefix)) {
                result = result.removePrefix(prefix)
            }
        }

        // Trim leading whitespace after prefix removal
        result = result.trimStart()

        // Remove trailing alarm symbol and space before it
        if (result.endsWith(ALARM_SYMBOL)) {
            result = result.dropLast(ALARM_SYMBOL.length)
            if (result.endsWith(" ")) {
                result = result.dropLast(1)
            }
        }

        result.trim()
    }

    /** Enabled stages only. */
    val enabledStages: List<AlarmStage>
        get() = stages.filter { it.enabled }

    /**
     * The earliest trigger time across all enabled stages.
     */
    val earliestThresholdTime: Timestamp?
        get() {
            val due = dueTime ?: return null
            return enabledStages
                .map { it.resolveTime(due) }
                .minByOrNull { it.toDate().time }
        }

    /**
     * The latest trigger time across all enabled stages (or dueTime itself).
     */
    val latestThresholdTime: Timestamp?
        get() = dueTime

    companion object {
        private const val ALARM_SYMBOL = "⏰"
        private val DISPLAY_PREFIXES = listOf("• ", "☐ ", "☑ ")

        val DEFAULT_STAGES = listOf(
            AlarmStage(AlarmStageType.SOUND_ALARM, offsetMs = 0, enabled = true),
            AlarmStage(AlarmStageType.LOCK_SCREEN, offsetMs = 30 * 60 * 1000L, enabled = false),
            AlarmStage(AlarmStageType.NOTIFICATION, offsetMs = 2 * 60 * 60 * 1000L, enabled = false)
        )
    }
}

enum class AlarmStatus {
    PENDING,    // Active alarm
    DONE,       // User marked as done
    CANCELLED   // User cancelled (not done)
}

enum class SnoozeDuration(val minutes: Int) {
    TWO_MINUTES(2),
    TEN_MINUTES(10),
    ONE_HOUR(60)
}

/**
 * Priority level for alarms, used for status bar icon color.
 * Ordered from lowest to highest priority.
 */
enum class AlarmPriority {
    UPCOMING,
    NOTIFY,
    URGENT,
    ALARM
}

/**
 * Type of alarm notification to show.
 */
enum class AlarmType {
    NOTIFY,     // Regular notification
    URGENT,     // Full-screen urgent notification
    ALARM       // Audible alarm with snooze options
}
