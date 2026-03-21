package org.alkaline.taskbrain.ui.currentnote.util

import org.alkaline.taskbrain.data.AlarmMarkers

/**
 * Utilities for managing alarm symbols and directives in text.
 * Delegates shared constants/functions to [AlarmMarkers] in the data layer.
 */
object AlarmSymbolUtils {

    const val ALARM_SYMBOL = AlarmMarkers.ALARM_SYMBOL

    /** Regex matching alarm directives like [alarm("abc123")] */
    val ALARM_DIRECTIVE_REGEX = AlarmMarkers.ALARM_DIRECTIVE_REGEX

    /** Creates an alarm directive string: [alarm("abc123")] */
    fun alarmDirective(alarmId: String): String = AlarmMarkers.alarmDirective(alarmId)

    /** Strips all alarm directives and plain alarm symbols from text. */
    fun stripAlarmMarkers(text: String): String = AlarmMarkers.stripAlarmMarkers(text)
}
