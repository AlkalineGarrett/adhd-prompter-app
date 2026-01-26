package org.alkaline.taskbrain.dsl.builtins

import org.alkaline.taskbrain.dsl.runtime.Arguments
import org.alkaline.taskbrain.dsl.runtime.BooleanVal
import org.alkaline.taskbrain.dsl.runtime.BuiltinFunction
import org.alkaline.taskbrain.dsl.runtime.BuiltinRegistry
import org.alkaline.taskbrain.dsl.runtime.ExecutionException
import org.alkaline.taskbrain.dsl.runtime.PatternVal
import org.alkaline.taskbrain.dsl.runtime.StringVal

/**
 * Pattern matching builtin functions.
 *
 * Milestone 4: matches(string, pattern)
 */
object PatternFunctions {

    fun register(registry: BuiltinRegistry) {
        registry.register(matchesFunction)
    }

    /**
     * matches(string, pattern) - Check if a string matches a pattern.
     *
     * Example: [matches("2026-01-15", pattern(digit*4 "-" digit*2 "-" digit*2))] -> true
     *
     * @param string The string to test
     * @param pattern The pattern to match against
     * @return BooleanVal(true) if the string matches the pattern, BooleanVal(false) otherwise
     */
    private val matchesFunction = BuiltinFunction(name = "matches") { args, _ ->
        if (args.size != 2) {
            throw ExecutionException("'matches' requires 2 arguments (string, pattern), got ${args.size}")
        }

        val stringArg = args[0]
        val patternArg = args[1]

        if (stringArg !is StringVal) {
            throw ExecutionException(
                "'matches' first argument must be a string, got ${stringArg?.typeName ?: "null"}"
            )
        }

        if (patternArg !is PatternVal) {
            throw ExecutionException(
                "'matches' second argument must be a pattern, got ${patternArg?.typeName ?: "null"}"
            )
        }

        BooleanVal(patternArg.matches(stringArg.value))
    }
}
