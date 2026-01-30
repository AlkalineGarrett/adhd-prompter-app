package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.dsl.language.*

/**
 * Validates that directives follow mutation and temporal rules.
 *
 * Phase 9: Mutation handling for caching.
 *
 * Rules:
 * 1. **Bare mutations** - `new`, `maybe_new`, `.append` at top level require
 *    explicit triggers (`button()` or `schedule()`)
 * 2. **Bare time values** - `date`, `time`, `datetime` function calls require
 *    `once[...]` or `refresh[...]` wrapper to control caching behavior
 *
 * This is different from IdempotencyAnalyzer:
 * - IdempotencyAnalyzer: Can this be safely cached? (repeated calls = same result)
 * - MutationValidator: Should this run automatically? (side effects = require trigger)
 *
 * For example, `maybe_new` is idempotent (safe to cache) but still requires
 * a trigger because it can create a note the first time it runs.
 */
object MutationValidator {

    /**
     * Result of mutation validation.
     */
    sealed class ValidationResult {
        /** Expression is valid and can be executed/cached. */
        object Valid : ValidationResult()

        /** Expression contains a bare mutation without proper wrapper. */
        data class BareMutation(
            val mutationType: String,
            val suggestion: String
        ) : ValidationResult()

        /** Expression contains a bare time value without proper wrapper. */
        data class BareTimeValue(
            val functionName: String,
            val suggestion: String
        ) : ValidationResult()

        fun isValid(): Boolean = this is Valid

        fun errorMessage(): String? = when (this) {
            is Valid -> null
            is BareMutation -> "$mutationType requires explicit trigger. $suggestion"
            is BareTimeValue -> "$functionName returns a time value that changes. $suggestion"
        }
    }

    /**
     * Context for validation - tracks whether we're inside a wrapper.
     */
    private data class ValidationContext(
        /** Inside button() or schedule() - mutations allowed */
        val insideActionWrapper: Boolean = false,
        /** Inside once[] or refresh[] - time values allowed */
        val insideTimeWrapper: Boolean = false
    )

    /**
     * Functions that create mutations requiring explicit triggers.
     */
    private val MUTATION_FUNCTIONS = setOf("new", "maybe_new")

    /**
     * Methods that create mutations requiring explicit triggers.
     */
    private val MUTATION_METHODS = setOf("append")

    /**
     * Functions that wrap mutations to make them safe.
     */
    private val ACTION_WRAPPER_FUNCTIONS = setOf("button", "schedule")

    /**
     * Functions that return time values (change over time).
     */
    private val TIME_FUNCTIONS = setOf("date", "time", "datetime")

    /**
     * Validate a directive expression.
     *
     * @param expr The expression to validate
     * @return ValidationResult indicating whether the expression is valid
     */
    fun validate(expr: Expression): ValidationResult {
        return validateWithContext(expr, ValidationContext())
    }

    private fun validateWithContext(
        expr: Expression,
        context: ValidationContext
    ): ValidationResult {
        return when (expr) {
            // Literals are always valid
            is NumberLiteral -> ValidationResult.Valid
            is StringLiteral -> ValidationResult.Valid

            // Variable references are valid
            is VariableRef -> ValidationResult.Valid

            // Current note reference is valid
            is CurrentNoteRef -> ValidationResult.Valid

            // Pattern expressions are valid
            is PatternExpr -> ValidationResult.Valid

            // Property access - validate target
            is PropertyAccess -> validateWithContext(expr.target, context)

            // Lambda expressions - validate body
            is LambdaExpr -> validateWithContext(expr.body, context)

            // Lambda invocations - validate lambda and arguments
            is LambdaInvocation -> validateLambdaInvocation(expr, context)

            // Once expressions - allow time values inside
            is OnceExpr -> validateWithContext(
                expr.body,
                context.copy(insideTimeWrapper = true)
            )

            // Refresh expressions - allow time values inside
            is RefreshExpr -> validateWithContext(
                expr.body,
                context.copy(insideTimeWrapper = true)
            )

            // Function calls - check for mutations and time values
            is CallExpr -> validateCallExpr(expr, context)

            // Method calls - check for mutation methods
            is MethodCall -> validateMethodCall(expr, context)

            // Assignments - validate the value
            is Assignment -> validateAssignment(expr, context)

            // Statement lists - validate all statements
            is StatementList -> validateStatementList(expr, context)
        }
    }

    private fun validateLambdaInvocation(
        expr: LambdaInvocation,
        context: ValidationContext
    ): ValidationResult {
        // Validate the lambda body
        val bodyResult = validateWithContext(expr.lambda.body, context)
        if (!bodyResult.isValid()) {
            return bodyResult
        }

        // Validate arguments
        for (arg in expr.args) {
            val argResult = validateWithContext(arg, context)
            if (!argResult.isValid()) {
                return argResult
            }
        }
        for (namedArg in expr.namedArgs) {
            val argResult = validateWithContext(namedArg.value, context)
            if (!argResult.isValid()) {
                return argResult
            }
        }

        return ValidationResult.Valid
    }

    private fun validateCallExpr(
        expr: CallExpr,
        context: ValidationContext
    ): ValidationResult {
        // Check for mutation functions
        if (expr.name in MUTATION_FUNCTIONS && !context.insideActionWrapper) {
            return ValidationResult.BareMutation(
                mutationType = "${expr.name}()",
                suggestion = "Wrap in button() or schedule() to execute."
            )
        }

        // Check for time functions
        if (expr.name in TIME_FUNCTIONS && !context.insideTimeWrapper) {
            return ValidationResult.BareTimeValue(
                functionName = "${expr.name}()",
                suggestion = "Wrap in once[...] to cache or refresh[...] to update periodically."
            )
        }

        // Action wrapper functions - enable mutation context for action arg
        if (expr.name in ACTION_WRAPPER_FUNCTIONS) {
            // First arg (label/frequency) - validate in current context
            val firstArg = expr.args.getOrNull(0)
            if (firstArg != null) {
                val firstResult = validateWithContext(firstArg, context)
                if (!firstResult.isValid()) {
                    return firstResult
                }
            }

            // Second arg (action) - validate with insideActionWrapper = true
            // Also allow time values since they're evaluated at action execution time
            val actionArg = expr.args.getOrNull(1)
            if (actionArg != null) {
                val actionResult = validateWithContext(
                    actionArg,
                    context.copy(insideActionWrapper = true, insideTimeWrapper = true)
                )
                if (!actionResult.isValid()) {
                    return actionResult
                }
            }

            // Named args - validate in current context
            for (namedArg in expr.namedArgs) {
                val argResult = validateWithContext(namedArg.value, context)
                if (!argResult.isValid()) {
                    return argResult
                }
            }

            return ValidationResult.Valid
        }

        // Regular function - validate all arguments
        for (arg in expr.args) {
            val argResult = validateWithContext(arg, context)
            if (!argResult.isValid()) {
                return argResult
            }
        }
        for (namedArg in expr.namedArgs) {
            val argResult = validateWithContext(namedArg.value, context)
            if (!argResult.isValid()) {
                return argResult
            }
        }

        return ValidationResult.Valid
    }

    private fun validateMethodCall(
        expr: MethodCall,
        context: ValidationContext
    ): ValidationResult {
        // Check for mutation methods
        if (expr.methodName in MUTATION_METHODS && !context.insideActionWrapper) {
            return ValidationResult.BareMutation(
                mutationType = ".${expr.methodName}()",
                suggestion = "Wrap in button() or schedule() to execute."
            )
        }

        // Validate target
        val targetResult = validateWithContext(expr.target, context)
        if (!targetResult.isValid()) {
            return targetResult
        }

        // Validate arguments
        for (arg in expr.args) {
            val argResult = validateWithContext(arg, context)
            if (!argResult.isValid()) {
                return argResult
            }
        }
        for (namedArg in expr.namedArgs) {
            val argResult = validateWithContext(namedArg.value, context)
            if (!argResult.isValid()) {
                return argResult
            }
        }

        return ValidationResult.Valid
    }

    private fun validateAssignment(
        expr: Assignment,
        context: ValidationContext
    ): ValidationResult {
        // Validate the value being assigned
        return validateWithContext(expr.value, context)
    }

    private fun validateStatementList(
        expr: StatementList,
        context: ValidationContext
    ): ValidationResult {
        // All statements must be valid
        for (statement in expr.statements) {
            val result = validateWithContext(statement, context)
            if (!result.isValid()) {
                return result
            }
        }
        return ValidationResult.Valid
    }

    /**
     * Check if an expression contains any mutations (for caching decisions).
     * This does NOT validate - just detects presence of mutations.
     *
     * @param expr The expression to check
     * @return true if the expression contains mutations
     */
    fun containsMutations(expr: Expression): Boolean {
        return when (expr) {
            is NumberLiteral, is StringLiteral, is VariableRef,
            is CurrentNoteRef, is PatternExpr -> false

            is PropertyAccess -> containsMutations(expr.target)

            is LambdaExpr -> containsMutations(expr.body)

            is LambdaInvocation ->
                containsMutations(expr.lambda.body) ||
                expr.args.any { containsMutations(it) } ||
                expr.namedArgs.any { containsMutations(it.value) }

            is OnceExpr -> containsMutations(expr.body)

            is RefreshExpr -> containsMutations(expr.body)

            is CallExpr -> {
                if (expr.name in MUTATION_FUNCTIONS) return true
                if (expr.name in ACTION_WRAPPER_FUNCTIONS) {
                    // Action wrappers contain mutations by design
                    return true
                }
                expr.args.any { containsMutations(it) } ||
                expr.namedArgs.any { containsMutations(it.value) }
            }

            is MethodCall -> {
                if (expr.methodName in MUTATION_METHODS) return true
                containsMutations(expr.target) ||
                expr.args.any { containsMutations(it) } ||
                expr.namedArgs.any { containsMutations(it.value) }
            }

            is Assignment -> containsMutations(expr.value)

            is StatementList -> expr.statements.any { containsMutations(it) }
        }
    }

    /**
     * Check if an expression contains time-dependent values (for caching decisions).
     *
     * @param expr The expression to check
     * @return true if the expression contains unwrapped time values
     */
    fun containsUnwrappedTimeValues(expr: Expression): Boolean {
        return containsTimeValuesInternal(expr, insideWrapper = false)
    }

    private fun containsTimeValuesInternal(
        expr: Expression,
        insideWrapper: Boolean
    ): Boolean {
        return when (expr) {
            is NumberLiteral, is StringLiteral, is VariableRef,
            is CurrentNoteRef, is PatternExpr -> false

            is PropertyAccess -> containsTimeValuesInternal(expr.target, insideWrapper)

            is LambdaExpr -> containsTimeValuesInternal(expr.body, insideWrapper)

            is LambdaInvocation ->
                containsTimeValuesInternal(expr.lambda.body, insideWrapper) ||
                expr.args.any { containsTimeValuesInternal(it, insideWrapper) } ||
                expr.namedArgs.any { containsTimeValuesInternal(it.value, insideWrapper) }

            is OnceExpr -> containsTimeValuesInternal(expr.body, insideWrapper = true)

            is RefreshExpr -> containsTimeValuesInternal(expr.body, insideWrapper = true)

            is CallExpr -> {
                if (expr.name in TIME_FUNCTIONS && !insideWrapper) return true
                expr.args.any { containsTimeValuesInternal(it, insideWrapper) } ||
                expr.namedArgs.any { containsTimeValuesInternal(it.value, insideWrapper) }
            }

            is MethodCall ->
                containsTimeValuesInternal(expr.target, insideWrapper) ||
                expr.args.any { containsTimeValuesInternal(it, insideWrapper) } ||
                expr.namedArgs.any { containsTimeValuesInternal(it.value, insideWrapper) }

            is Assignment -> containsTimeValuesInternal(expr.value, insideWrapper)

            is StatementList -> expr.statements.any { containsTimeValuesInternal(it, insideWrapper) }
        }
    }
}
