package org.alkaline.taskbrain.dsl.runtime

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Runtime values in the DSL.
 * Each subclass represents a different type of value that can result from evaluation.
 *
 * Milestone 2: Adds DateVal, TimeVal, DateTimeVal.
 */
sealed class DslValue {
    /**
     * Type identifier for serialization.
     */
    abstract val typeName: String

    /**
     * Convert to a display string for rendering results.
     */
    abstract fun toDisplayString(): String

    /**
     * Serialize to a Map for Firestore storage.
     */
    fun serialize(): Map<String, Any?> = mapOf(
        "type" to typeName,
        "value" to serializeValue()
    )

    protected abstract fun serializeValue(): Any?

    companion object {
        /**
         * Deserialize a DslValue from a Firestore map.
         * @throws IllegalArgumentException for unknown types
         */
        fun deserialize(map: Map<String, Any?>): DslValue {
            val type = map["type"] as? String
                ?: throw IllegalArgumentException("Missing 'type' field in serialized DslValue")
            val value = map["value"]

            return when (type) {
                "number" -> NumberVal((value as Number).toDouble())
                "string" -> StringVal(value as String)
                "date" -> DateVal(LocalDate.parse(value as String))
                "time" -> TimeVal(LocalTime.parse(value as String))
                "datetime" -> DateTimeVal(LocalDateTime.parse(value as String))
                else -> throw IllegalArgumentException("Unknown DslValue type: $type")
            }
        }
    }
}

/**
 * A numeric value.
 */
data class NumberVal(val value: Double) : DslValue() {
    override val typeName: String = "number"

    override fun toDisplayString(): String {
        // Display integers without decimal point
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    override fun serializeValue(): Any = value
}

/**
 * A string value.
 */
data class StringVal(val value: String) : DslValue() {
    override val typeName: String = "string"

    override fun toDisplayString(): String = value

    override fun serializeValue(): Any = value
}

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
