package org.alkaline.taskbrain.dsl.runtime.values

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A date value (year, month, day only).
 */
data class DateVal(val value: LocalDate) : DslValue() {
    override val typeName: String = "date"

    override fun toDisplayString(): String = value.format(DateTimeFormatter.ISO_LOCAL_DATE)

    override fun serializeValue(): Any = value.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

/**
 * A time value (hour, minute, second only).
 */
data class TimeVal(val value: LocalTime) : DslValue() {
    override val typeName: String = "time"

    override fun toDisplayString(): String = value.format(DISPLAY_TIME_FORMAT)

    override fun serializeValue(): Any = value.format(DateTimeFormatter.ISO_LOCAL_TIME)

    companion object {
        private val DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

/**
 * A datetime value (date + time).
 */
data class DateTimeVal(val value: LocalDateTime) : DslValue() {
    override val typeName: String = "datetime"

    override fun toDisplayString(): String = value.format(DISPLAY_DATETIME_FORMAT)

    override fun serializeValue(): Any = value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    companion object {
        // Display as "2026-01-25, 14:30:00" instead of "2026-01-25T14:30:00"
        private val DISPLAY_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm:ss")
    }
}
