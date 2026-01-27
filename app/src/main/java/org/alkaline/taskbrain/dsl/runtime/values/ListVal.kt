package org.alkaline.taskbrain.dsl.runtime.values

/**
 * A list value containing other DslValues.
 * Created by the find() function, list() function, etc.
 *
 * Milestone 5.
 */
data class ListVal(val items: List<DslValue>) : DslValue() {
    override val typeName: String = "list"

    override fun toDisplayString(): String {
        if (items.isEmpty()) return "[]"
        return "[${items.joinToString(", ") { it.toDisplayString() }}]"
    }

    override fun serializeValue(): Any = items.map { it.serialize() }

    /** Number of items in the list. */
    val size: Int get() = items.size

    /** Check if the list is empty. */
    fun isEmpty(): Boolean = items.isEmpty()

    /** Check if the list is not empty. */
    fun isNotEmpty(): Boolean = items.isNotEmpty()

    /** Get an item by index, or null if out of bounds. */
    operator fun get(index: Int): DslValue? = items.getOrNull(index)
}
