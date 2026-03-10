package org.alkaline.taskbrain.ui.currentnote.util

import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmStatus

/**
 * Maps alarm data to visual overlay badges for rendering on alarm symbols.
 */
object AlarmOverlayMapping {

    private val PAST_DUE_COLOR = Color(0xFFD32F2F)
    private val COMPLETED_COLOR = Color(0xFF388E3C)
    private val CANCELLED_COLOR = Color(0xFF757575)

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
            else -> SymbolBadge.None
        }
        return SymbolOverlay(symbol = AlarmSymbolUtils.ALARM_SYMBOL, badge = badge)
    }
}
