package org.alkaline.taskbrain.dsl

/**
 * Character constant functions for mobile-friendly string building.
 *
 * Since the DSL has no escape sequences in strings (to make typing on mobile
 * easier), special characters are inserted using these constant functions.
 *
 * Milestone 2: qt, nl, tab, ret
 */
object CharacterConstants {

    fun register(registry: BuiltinRegistry) {
        charConstants.forEach { registry.register(it) }
    }

    /** Creates a zero-arg function that returns a constant string value. */
    private fun charConstant(name: String, value: String) = BuiltinFunction(name) { args, _ ->
        if (args.hasPositional()) {
            throw ExecutionException("'$name' takes no arguments, got ${args.size}")
        }
        StringVal(value)
    }

    private val charConstants = listOf(
        charConstant("qt", "\""),   // Double quote
        charConstant("nl", "\n"),   // Newline
        charConstant("tab", "\t"),  // Tab
        charConstant("ret", "\r")   // Carriage return
    )
}
