package org.alkaline.taskbrain.dsl.language

import org.alkaline.taskbrain.dsl.runtime.BuiltinRegistry

/**
 * Analyzes AST expressions for dynamic function calls.
 *
 * Dynamic functions can return different results on each call (e.g., `now`, `date`, `time`).
 * Static functions always return the same result for the same inputs (e.g., `iso8601`, `qt`).
 *
 * This analysis is used to determine if a directive needs re-execution on confirm.
 */
object DynamicCallAnalyzer {

    /**
     * Check if an expression contains any dynamic function calls.
     *
     * Milestone 4: Added PatternExpr support.
     * Milestone 6: Added CurrentNoteRef and PropertyAccess support.
     * Milestone 7: Added Assignment, StatementList, VariableRef, MethodCall support.
     *
     * @param expr The expression to analyze
     * @return true if the expression contains any dynamic calls
     */
    fun containsDynamicCalls(expr: Expression): Boolean {
        return when (expr) {
            is NumberLiteral -> false
            is StringLiteral -> false
            is PatternExpr -> false  // Patterns are static
            is CurrentNoteRef -> false  // Current note reference is static
            is PropertyAccess -> containsDynamicCalls(expr.target)  // Check the target
            is VariableRef -> false  // Variables themselves are static
            is Assignment -> {
                // Check both target and value
                containsDynamicCalls(expr.target) || containsDynamicCalls(expr.value)
            }
            is StatementList -> {
                // Check all statements
                expr.statements.any { containsDynamicCalls(it) }
            }
            is MethodCall -> {
                // Method calls on notes (like append) are considered dynamic
                true
            }
            is CallExpr -> {
                BuiltinRegistry.isDynamic(expr.name) ||
                    expr.args.any { containsDynamicCalls(it) } ||
                    expr.namedArgs.any { containsDynamicCalls(it.value) }
            }
        }
    }
}
