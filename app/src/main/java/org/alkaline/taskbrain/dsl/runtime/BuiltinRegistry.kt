package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.dsl.builtins.ArithmeticFunctions
import org.alkaline.taskbrain.dsl.builtins.CharacterConstants
import org.alkaline.taskbrain.dsl.builtins.DateFunctions
import org.alkaline.taskbrain.dsl.builtins.NoteFunctions
import org.alkaline.taskbrain.dsl.builtins.PatternFunctions

/**
 * A builtin function that can be called from the DSL.
 *
 * @property name The function name used to call it
 * @property isDynamic True if this function can return different results on each call
 *                     (e.g., `now`, `date`, `time`). Static functions always return the
 *                     same result for the same inputs (e.g., `iso8601`, `qt`).
 * @property call The implementation that takes arguments and an environment
 */
data class BuiltinFunction(
    val name: String,
    val isDynamic: Boolean = false,
    val call: (args: Arguments, env: Environment) -> DslValue
)

/**
 * Registry of all builtin functions available in the DSL.
 *
 * Milestone 3: Adds arithmetic functions.
 * Milestone 5: Adds note functions (find).
 */
object BuiltinRegistry {
    private val functions = mutableMapOf<String, BuiltinFunction>()

    init {
        // Register all builtin modules
        DateFunctions.register(this)
        CharacterConstants.register(this)
        ArithmeticFunctions.register(this)
        PatternFunctions.register(this)
        NoteFunctions.register(this)
    }

    /**
     * Register a builtin function.
     */
    fun register(function: BuiltinFunction) {
        functions[function.name] = function
    }

    /**
     * Look up a function by name.
     * @return The function, or null if not found
     */
    fun get(name: String): BuiltinFunction? = functions[name]

    /**
     * Check if a function exists.
     */
    fun has(name: String): Boolean = functions.containsKey(name)

    /**
     * Check if a function is dynamic (can return different results each call).
     */
    fun isDynamic(name: String): Boolean = functions[name]?.isDynamic ?: false

    /**
     * Get all registered function names.
     */
    fun allNames(): Set<String> = functions.keys.toSet()
}
