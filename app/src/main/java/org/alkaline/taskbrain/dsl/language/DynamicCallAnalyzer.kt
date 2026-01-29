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
     * Milestone 8: Added LambdaExpr support.
     * Phase 0b: Added LambdaInvocation support.
     * Phase 0c: Added OnceExpr support.
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
            is LambdaExpr -> containsDynamicCalls(expr.body)  // Check the lambda body
            is LambdaInvocation -> {
                // Check both the lambda body and the arguments
                containsDynamicCalls(expr.lambda.body) ||
                    expr.args.any { containsDynamicCalls(it) } ||
                    expr.namedArgs.any { containsDynamicCalls(it.value) }
            }
            is OnceExpr -> {
                // once[...] is NOT dynamic - it caches the result permanently
                // Even if the body contains dynamic calls, the result is static after first evaluation
                false
            }
            is RefreshExpr -> {
                // refresh[...] IS dynamic - it re-evaluates at trigger times
                // The body's dynamic status doesn't matter; the wrapper makes it dynamic
                true
            }
            is Assignment -> {
                // Check both target and value
                containsDynamicCalls(expr.target) || containsDynamicCalls(expr.value)
            }
            is StatementList -> {
                // Check all statements
                expr.statements.any { containsDynamicCalls(it) }
            }
            is MethodCall -> {
                // Method calls are dynamic if:
                // 1. The target is dynamic
                // 2. OR any argument is dynamic
                // Note: Note mutation methods (like append) don't return temporal values,
                // so we don't need special handling for them here
                containsDynamicCalls(expr.target) ||
                    expr.args.any { containsDynamicCalls(it) } ||
                    expr.namedArgs.any { containsDynamicCalls(it.value) }
            }
            is CallExpr -> {
                BuiltinRegistry.isDynamic(expr.name) ||
                    expr.args.any { containsDynamicCalls(it) } ||
                    expr.namedArgs.any { containsDynamicCalls(it.value) }
            }
        }
    }
}
