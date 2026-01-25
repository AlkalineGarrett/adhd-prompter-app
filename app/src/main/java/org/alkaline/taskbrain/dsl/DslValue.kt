package org.alkaline.taskbrain.dsl

/**
 * Runtime values in the DSL.
 * Each subclass represents a different type of value that can result from evaluation.
 *
 * Milestone 1: NumberVal, StringVal only
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
