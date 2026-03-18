package org.alkaline.taskbrain.service

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.AlarmStage
import org.alkaline.taskbrain.data.RecurrencePreset
import org.alkaline.taskbrain.data.RecurringAlarm
import org.alkaline.taskbrain.data.RecurrenceType
import org.alkaline.taskbrain.data.toTimeOfDay
import org.alkaline.taskbrain.ui.currentnote.components.CustomFrequency
import org.alkaline.taskbrain.ui.currentnote.components.EndType
import org.alkaline.taskbrain.ui.currentnote.components.RecurrenceConfig
import org.alkaline.taskbrain.ui.currentnote.components.RelativeUnit

/**
 * Maps UI [RecurrenceConfig] to a [RecurringAlarm] template.
 */
object RecurrenceConfigMapper {

    /**
     * Creates a [RecurringAlarm] from the dialog's due time, stages, and recurrence config.
     *
     * @param noteId The note/line ID this alarm is associated with
     * @param lineContent Snapshot of the line text
     * @param dueTime The due time set in the dialog
     * @param stages The alarm stages configuration
     * @param config The recurrence configuration from the UI
     */
    fun toRecurringAlarm(
        noteId: String,
        lineContent: String,
        dueTime: Timestamp?,
        stages: List<AlarmStage>,
        config: RecurrenceConfig
    ): RecurringAlarm {
        return RecurringAlarm(
            noteId = noteId,
            lineContent = lineContent,
            recurrenceType = config.recurrenceType,
            rrule = if (config.recurrenceType == RecurrenceType.FIXED) buildRRule(config) else null,
            relativeIntervalMs = if (config.recurrenceType == RecurrenceType.RELATIVE) {
                config.relativeInterval.toLong() * config.relativeUnit.toMs
            } else null,
            dueOffsetMs = 0,  // base time IS the due time for recurring alarms
            stages = stages,
            anchorTimeOfDay = if (config.recurrenceType == RecurrenceType.FIXED) {
                dueTime?.toTimeOfDay()
            } else null,
            endDate = if (config.endType == EndType.ON_DATE) config.endDate else null,
            repeatCount = if (config.endType == EndType.AFTER_COUNT) config.repeatCount else null
        )
    }

    private fun buildRRule(config: RecurrenceConfig): String {
        // If a preset is selected, use its RRULE directly
        config.selectedPreset?.let { return it.rrule }

        // Build custom RRULE
        val freq = when (config.customFrequency) {
            CustomFrequency.DAYS -> RRuleParser.Frequency.DAILY
            CustomFrequency.WEEKS -> RRuleParser.Frequency.WEEKLY
        }
        val byDay = if (config.customFrequency == CustomFrequency.WEEKS && config.selectedDays.isNotEmpty()) {
            config.selectedDays.toList()
        } else null

        return RRuleParser.build(freq, config.customInterval, byDay)
    }

    /**
     * Converts a [RecurringAlarm] template back to a [RecurrenceConfig] for pre-populating the UI.
     */
    fun toRecurrenceConfig(recurring: RecurringAlarm): RecurrenceConfig {
        return when (recurring.recurrenceType) {
            RecurrenceType.FIXED -> toFixedConfig(recurring)
            RecurrenceType.RELATIVE -> toRelativeConfig(recurring)
        }
    }

    private fun toFixedConfig(recurring: RecurringAlarm): RecurrenceConfig {
        val rrule = recurring.rrule ?: return RecurrenceConfig(enabled = true, recurrenceType = RecurrenceType.FIXED)

        // Check if it matches a preset
        val matchingPreset = RecurrencePreset.entries.firstOrNull { it.rrule == rrule }
        if (matchingPreset != null) {
            return RecurrenceConfig(
                enabled = true,
                recurrenceType = RecurrenceType.FIXED,
                selectedPreset = matchingPreset,
                endType = endTypeFrom(recurring),
                endDate = recurring.endDate,
                repeatCount = recurring.repeatCount ?: 10
            )
        }

        // Parse custom RRULE
        val parsed = RRuleParser.parse(rrule)
        val customFrequency = when (parsed.freq) {
            RRuleParser.Frequency.DAILY -> CustomFrequency.DAYS
            RRuleParser.Frequency.WEEKLY -> CustomFrequency.WEEKS
            RRuleParser.Frequency.MONTHLY -> CustomFrequency.DAYS // monthly uses interval in days
        }

        return RecurrenceConfig(
            enabled = true,
            recurrenceType = RecurrenceType.FIXED,
            selectedPreset = null,
            customInterval = parsed.interval,
            customFrequency = customFrequency,
            selectedDays = parsed.byDay?.toSet() ?: emptySet(),
            endType = endTypeFrom(recurring),
            endDate = recurring.endDate,
            repeatCount = recurring.repeatCount ?: 10
        )
    }

    private fun toRelativeConfig(recurring: RecurringAlarm): RecurrenceConfig {
        val intervalMs = recurring.relativeIntervalMs ?: 0L

        // Find the best unit to display in
        val (interval, unit) = when {
            intervalMs > 0 && intervalMs % RelativeUnit.WEEKS.toMs == 0L ->
                (intervalMs / RelativeUnit.WEEKS.toMs).toInt() to RelativeUnit.WEEKS
            intervalMs > 0 && intervalMs % RelativeUnit.DAYS.toMs == 0L ->
                (intervalMs / RelativeUnit.DAYS.toMs).toInt() to RelativeUnit.DAYS
            intervalMs > 0 && intervalMs % RelativeUnit.HOURS.toMs == 0L ->
                (intervalMs / RelativeUnit.HOURS.toMs).toInt() to RelativeUnit.HOURS
            else -> 1 to RelativeUnit.DAYS
        }

        return RecurrenceConfig(
            enabled = true,
            recurrenceType = RecurrenceType.RELATIVE,
            relativeInterval = interval,
            relativeUnit = unit,
            endType = endTypeFrom(recurring),
            endDate = recurring.endDate,
            repeatCount = recurring.repeatCount ?: 10
        )
    }

    private fun endTypeFrom(recurring: RecurringAlarm): EndType = when {
        recurring.endDate != null -> EndType.ON_DATE
        recurring.repeatCount != null -> EndType.AFTER_COUNT
        else -> EndType.NEVER
    }
}
