package org.alkaline.taskbrain.dsl.runtime.values

/**
 * Represents an undefined value.
 * Returned when accessing non-existent data (e.g., out of bounds, missing property, hierarchy beyond depth).
 * Following the design principle: graceful undefined access instead of errors.
 *
 * Milestone 7.
 */
object UndefinedVal : DslValue() {
    override val typeName: String = "undefined"

    override fun toDisplayString(): String = "undefined"

    override fun serializeValue(): Any? = null
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
 * A boolean value.
 * Milestone 4: Added for matches() function result.
 */
data class BooleanVal(val value: Boolean) : DslValue() {
    override val typeName: String = "boolean"

    override fun toDisplayString(): String = if (value) "true" else "false"

    override fun serializeValue(): Any = value
}
