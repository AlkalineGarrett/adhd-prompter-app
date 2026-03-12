package org.alkaline.taskbrain.ui.currentnote.util

import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmStatus
import java.util.Calendar

/**
 * Maps alarm data to visual overlay badges for rendering on alarm symbols.
 */
object AlarmOverlayMapping {

    private val PAST_DUE_COLOR = Color(0xFFD32F2F)
    private val COMPLETED_COLOR = Color(0xFF388E3C)
    private val CANCELLED_COLOR = Color(0xFF757575)
    private val RECURRING_TODAY_COLOR = Color(0xFFFF9800)   // Orange
    private val RECURRING_LATER_COLOR = Color(0xFF1976D2)   // Blue
    private const val RECURRING_SYMBOL = "↻"

    /**
     * Maps an alarm to its visual overlay badge based on status and thresholds.
     */
    fun alarmToOverlay(alarm: Alarm, now: Timestamp): SymbolOverlay {
        val badge = when {
            alarm.status == AlarmStatus.DONE ->
                SymbolBadge.Corner(text = "✓", color = COMPLETED_COLOR)
            alarm.status == AlarmStatus.CANCELLED ->
                SymbolBadge.Corner(text = "✕", color = CANCELLED_COLOR)
            alarm.latestThresholdTime != null && alarm.latestThresholdTime!! < now ->
                SymbolBadge.Centered(text = "!", color = PAST_DUE_COLOR)
            alarm.recurringAlarmId != null ->
                recurringBadge(alarm, now)
            else -> SymbolBadge.None
        }
        return SymbolOverlay(symbol = AlarmSymbolUtils.ALARM_SYMBOL, badge = badge)
    }

    private fun recurringBadge(alarm: Alarm, now: Timestamp): SymbolBadge {
        val latestTime = alarm.latestThresholdTime ?: return recurringCornerBadge(RECURRING_LATER_COLOR)
        val isToday = isSameDay(now.toDate().time, latestTime.toDate().time)
        val color = if (isToday) RECURRING_TODAY_COLOR else RECURRING_LATER_COLOR
        return recurringCornerBadge(color)
    }

    private fun recurringCornerBadge(color: Color) = SymbolBadge.Corner(
        text = RECURRING_SYMBOL, color = color,
        sizeFraction = 0.8f, dimSymbol = false, dimCornerOnly = true,
        verticalOffsetFraction = -0.22f, thicken = true
    )

    private fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
