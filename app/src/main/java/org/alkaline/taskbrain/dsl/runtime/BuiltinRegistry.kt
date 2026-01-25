package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.dsl.builtins.ArithmeticFunctions
import org.alkaline.taskbrain.dsl.builtins.CharacterConstants
import org.alkaline.taskbrain.dsl.builtins.DateFunctions
import org.alkaline.taskbrain.dsl.language.CallExpr
import org.alkaline.taskbrain.dsl.language.Expression
import org.alkaline.taskbrain.dsl.language.NumberLiteral
import org.alkaline.taskbrain.dsl.language.StringLiteral

/**
 * Container for function arguments, supporting both positional and named arguments.
 *
 * @property positional List of positional arguments in order
 * @property named Map of named argument name to value
 */
data class Arguments(
    val positional: List<DslValue>,
    val named: Map<String, DslValue> = emptyMap()
) {
    /** Get a positional argument by index, or null if not present. */
    operator fun get(index: Int): DslValue? = positional.getOrNull(index)

    /** Get a named argument by name, or null if not present. */
    operator fun get(name: String): DslValue? = named[name]

    /** Get a positional argument, throwing if not present. */
    fun require(index: Int, paramName: String = "argument $index"): DslValue =
        positional.getOrNull(index)
            ?: throw ExecutionException("Missing required $paramName")

    /** Get a named argument, throwing if not present. */
    fun requireNamed(name: String): DslValue =
        named[name] ?: throw ExecutionException("Missing required argument '$name'")

    /** Total number of positional arguments. */
    val size: Int get() = positional.size

    /** Check if any positional arguments were provided. */
    fun hasPositional(): Boolean = positional.isNotEmpty()

    /** Check if any named arguments were provided. */
    fun hasNamed(): Boolean = named.isNotEmpty()

    /** Check if a specific named argument was provided. */
    fun hasNamed(name: String): Boolean = named.containsKey(name)
}

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
 */
object BuiltinRegistry {
    private val functions = mutableMapOf<String, BuiltinFunction>()

    init {
        // Register all builtin modules
        DateFunctions.register(this)
        CharacterConstants.register(this)
        ArithmeticFunctions.register(this)
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

    /**
     * Check if an expression contains any dynamic function calls.
     * Used to determine if a directive needs re-execution on confirm.
     */
    fun containsDynamicCalls(expr: Expression): Boolean {
        return when (expr) {
            is NumberLiteral -> false
            is StringLiteral -> false
            is CallExpr -> {
                isDynamic(expr.name) ||
                    expr.args.any { containsDynamicCalls(it) } ||
                    expr.namedArgs.any { containsDynamicCalls(it.value) }
            }
        }
    }
}
