package org.alkaline.taskbrain.dsl.language

/**
 * Analyzes AST expressions for idempotency.
 *
 * Idempotency rules:
 * - Idempotent mutations (allowed at top-level): property assignments (.path, .name)
 * - Non-idempotent mutations (require button/schedule): .append(), new()
 * - Idempotent operations: maybe_new(), find(), calculations, property access
 * - Propagation: A StatementList is non-idempotent if any statement is non-idempotent
 *
 * Non-idempotent directives at top-level will show an error suggesting
 * to use `button` or `schedule` constructs.
 */
object IdempotencyAnalyzer {

    /**
     * Result of idempotency analysis.
     */
    data class AnalysisResult(
        val isIdempotent: Boolean,
        val nonIdempotentReason: String? = null
    ) {
        companion object {
            val IDEMPOTENT = AnalysisResult(true)
            fun nonIdempotent(reason: String) = AnalysisResult(false, reason)
        }
    }

    /**
     * Non-idempotent method names that require explicit triggers.
     */
    private val NON_IDEMPOTENT_METHODS = setOf("append")

    /**
     * Non-idempotent function names that require explicit triggers.
     */
    private val NON_IDEMPOTENT_FUNCTIONS = setOf("new")

    /**
     * Idempotent property names that can be assigned at top-level.
     * These set a value, so repeated execution produces the same result.
     */
    private val IDEMPOTENT_PROPERTIES = setOf("path", "name")

    /**
     * Analyze an expression for idempotency.
     *
     * Milestone 8: Added LambdaExpr support.
     * Phase 0b: Added LambdaInvocation support.
     *
     * @param expr The expression to analyze
     * @return AnalysisResult indicating whether the expression is idempotent
     */
    fun analyze(expr: Expression): AnalysisResult {
        return when (expr) {
            // Literals are always idempotent
            is NumberLiteral -> AnalysisResult.IDEMPOTENT
            is StringLiteral -> AnalysisResult.IDEMPOTENT

            // Variable references are idempotent (just reading a value)
            is VariableRef -> AnalysisResult.IDEMPOTENT

            // Current note reference is idempotent (just a reference)
            is CurrentNoteRef -> AnalysisResult.IDEMPOTENT

            // Property access is idempotent (reading a property)
            is PropertyAccess -> analyze(expr.target)

            // Pattern expressions are idempotent (they're just values)
            is PatternExpr -> AnalysisResult.IDEMPOTENT

            // Lambda expressions - analyze the body for idempotency
            is LambdaExpr -> analyze(expr.body)

            // Lambda invocations - analyze lambda body and arguments
            is LambdaInvocation -> analyzeLambdaInvocation(expr)

            // Function calls - check if the function is non-idempotent
            is CallExpr -> analyzeCallExpr(expr)

            // Method calls - check if the method is non-idempotent
            is MethodCall -> analyzeMethodCall(expr)

            // Assignments - check what's being assigned
            is Assignment -> analyzeAssignment(expr)

            // Statement lists - non-idempotent if any statement is
            is StatementList -> analyzeStatementList(expr)
        }
    }

    private fun analyzeLambdaInvocation(expr: LambdaInvocation): AnalysisResult {
        // Check the lambda body
        val lambdaResult = analyze(expr.lambda.body)
        if (!lambdaResult.isIdempotent) {
            return lambdaResult
        }

        // Check arguments
        for (arg in expr.args) {
            val argResult = analyze(arg)
            if (!argResult.isIdempotent) {
                return argResult
            }
        }
        for (namedArg in expr.namedArgs) {
            val argResult = analyze(namedArg.value)
            if (!argResult.isIdempotent) {
                return argResult
            }
        }

        return AnalysisResult.IDEMPOTENT
    }

    private fun analyzeCallExpr(expr: CallExpr): AnalysisResult {
        // Check if the function itself is non-idempotent
        if (expr.name in NON_IDEMPOTENT_FUNCTIONS) {
            return AnalysisResult.nonIdempotent(
                "${expr.name}() creates new data and requires an explicit trigger. " +
                "Wrap in button() or schedule() to execute."
            )
        }

        // Check arguments for non-idempotency
        for (arg in expr.args) {
            val argResult = analyze(arg)
            if (!argResult.isIdempotent) {
                return argResult
            }
        }
        for (namedArg in expr.namedArgs) {
            val argResult = analyze(namedArg.value)
            if (!argResult.isIdempotent) {
                return argResult
            }
        }

        return AnalysisResult.IDEMPOTENT
    }

    private fun analyzeMethodCall(expr: MethodCall): AnalysisResult {
        // Check if the method is non-idempotent
        if (expr.methodName in NON_IDEMPOTENT_METHODS) {
            return AnalysisResult.nonIdempotent(
                ".${expr.methodName}() modifies data and requires an explicit trigger. " +
                "Wrap in button() or schedule() to execute."
            )
        }

        // Check target and arguments
        val targetResult = analyze(expr.target)
        if (!targetResult.isIdempotent) {
            return targetResult
        }

        for (arg in expr.args) {
            val argResult = analyze(arg)
            if (!argResult.isIdempotent) {
                return argResult
            }
        }
        for (namedArg in expr.namedArgs) {
            val argResult = analyze(namedArg.value)
            if (!argResult.isIdempotent) {
                return argResult
            }
        }

        return AnalysisResult.IDEMPOTENT
    }

    private fun analyzeAssignment(expr: Assignment): AnalysisResult {
        // Property assignments to idempotent properties are allowed
        // Example: [.path: "foo"] or [.name: "bar"]
        if (expr.target is PropertyAccess) {
            val propAccess = expr.target
            if (propAccess.property in IDEMPOTENT_PROPERTIES) {
                // Property assignment is idempotent, but check the value
                return analyze(expr.value)
            }
            // Non-idempotent property assignment (if we add more properties later)
            // For now, only path and name exist and are idempotent
        }

        // Variable assignments - check if the value is idempotent
        // Example: [x: new(path: "foo")] - non-idempotent because of new()
        return analyze(expr.value)
    }

    private fun analyzeStatementList(expr: StatementList): AnalysisResult {
        // A statement list is non-idempotent if ANY statement is non-idempotent
        for (statement in expr.statements) {
            val result = analyze(statement)
            if (!result.isIdempotent) {
                return result
            }
        }
        return AnalysisResult.IDEMPOTENT
    }
}
