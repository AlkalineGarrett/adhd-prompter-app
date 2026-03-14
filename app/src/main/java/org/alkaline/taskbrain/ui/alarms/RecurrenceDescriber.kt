package org.alkaline.taskbrain.ui.alarms

import android.content.Context
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.data.RecurrenceType
import org.alkaline.taskbrain.data.RecurringAlarm
import org.alkaline.taskbrain.service.RRuleParser
import org.alkaline.taskbrain.ui.currentnote.components.RelativeUnit
import java.util.Calendar

/**
 * Generates human-readable descriptions of recurring alarm schedules.
 */
object RecurrenceDescriber {

    private val DAY_NAMES = mapOf(
        Calendar.MONDAY to "Mon",
        Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu",
        Calendar.FRIDAY to "Fri",
        Calendar.SATURDAY to "Sat",
        Calendar.SUNDAY to "Sun"
    )

    private val WEEKDAY_SET = setOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY
    )

    fun describe(context: Context, recurring: RecurringAlarm): String {
        return when (recurring.recurrenceType) {
            RecurrenceType.FIXED -> describeFixed(context, recurring)
            RecurrenceType.RELATIVE -> describeRelative(context, recurring)
        }
    }

    private fun describeFixed(context: Context, recurring: RecurringAlarm): String {
        val rrule = recurring.rrule
            ?: return context.getString(R.string.recurrence_desc_unknown)

        val parsed = try {
            RRuleParser.parse(rrule)
        } catch (_: Exception) {
            return context.getString(R.string.recurrence_desc_unknown)
        }

        return when (parsed.freq) {
            RRuleParser.Frequency.DAILY -> describeDaily(context, parsed)
            RRuleParser.Frequency.WEEKLY -> describeWeekly(context, parsed)
            RRuleParser.Frequency.MONTHLY -> describeMonthly(context, parsed)
        }
    }

    private fun describeDaily(context: Context, parsed: RRuleParser.ParsedRule): String {
        return if (parsed.interval == 1) {
            context.getString(R.string.recurrence_desc_daily)
        } else {
            context.getString(R.string.recurrence_desc_every_n_days, parsed.interval)
        }
    }

    private fun describeWeekly(context: Context, parsed: RRuleParser.ParsedRule): String {
        val days = parsed.byDay?.sorted()

        if (days.isNullOrEmpty()) {
            return if (parsed.interval == 1) {
                context.getString(R.string.recurrence_desc_weekly)
            } else {
                context.getString(R.string.recurrence_desc_every_n_weeks, parsed.interval)
            }
        }

        if (parsed.interval == 1 && days.toSet() == WEEKDAY_SET) {
            return context.getString(R.string.recurrence_desc_weekdays)
        }

        val dayNames = days.mapNotNull { DAY_NAMES[it] }.joinToString(", ")
        return if (parsed.interval == 1) {
            context.getString(R.string.recurrence_desc_weekly_on, dayNames)
        } else {
            context.getString(R.string.recurrence_desc_every_n_weeks_on, parsed.interval, dayNames)
        }
    }

    private fun describeMonthly(context: Context, parsed: RRuleParser.ParsedRule): String {
        return if (parsed.interval == 1) {
            context.getString(R.string.recurrence_desc_monthly)
        } else {
            context.getString(R.string.recurrence_desc_every_n_months, parsed.interval)
        }
    }

    private fun describeRelative(context: Context, recurring: RecurringAlarm): String {
        val intervalMs = recurring.relativeIntervalMs
            ?: return context.getString(R.string.recurrence_desc_unknown)

        return when {
            intervalMs % RelativeUnit.WEEKS.toMs == 0L -> {
                val weeks = (intervalMs / RelativeUnit.WEEKS.toMs).toInt()
                if (weeks == 1) context.getString(R.string.recurrence_desc_relative_1_week)
                else context.getString(R.string.recurrence_desc_relative_weeks, weeks)
            }
            intervalMs % RelativeUnit.DAYS.toMs == 0L -> {
                val days = (intervalMs / RelativeUnit.DAYS.toMs).toInt()
                if (days == 1) context.getString(R.string.recurrence_desc_relative_1_day)
                else context.getString(R.string.recurrence_desc_relative_days, days)
            }
            intervalMs % RelativeUnit.HOURS.toMs == 0L -> {
                val hours = (intervalMs / RelativeUnit.HOURS.toMs).toInt()
                if (hours == 1) context.getString(R.string.recurrence_desc_relative_1_hour)
                else context.getString(R.string.recurrence_desc_relative_hours, hours)
            }
            else -> context.getString(R.string.recurrence_desc_unknown)
        }
    }
}
