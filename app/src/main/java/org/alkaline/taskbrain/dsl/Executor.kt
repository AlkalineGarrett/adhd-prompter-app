package org.alkaline.taskbrain.dsl

/**
 * Evaluates DSL expressions and produces runtime values.
 *
 * Milestone 2: Adds function call evaluation.
 */
class Executor {

    /**
     * Execute a directive and return its result.
     */
    fun execute(directive: Directive, env: Environment = Environment()): DslValue {
        return evaluate(directive.expression, env)
    }

    /**
     * Evaluate an expression to produce a value.
     */
    fun evaluate(expr: Expression, env: Environment): DslValue {
        return when (expr) {
            is NumberLiteral -> NumberVal(expr.value)
            is StringLiteral -> StringVal(expr.value)
            is CallExpr -> evaluateCall(expr, env)
        }
    }

    /**
     * Evaluate a function call expression.
     */
    private fun evaluateCall(expr: CallExpr, env: Environment): DslValue {
        val function = BuiltinRegistry.get(expr.name)
            ?: throw ExecutionException(
                "Unknown function '${expr.name}'",
                expr.position
            )

        // Evaluate all arguments
        val argValues = expr.args.map { evaluate(it, env) }

        // Call the function
        return try {
            function.call(argValues, env)
        } catch (e: ExecutionException) {
            throw e
        } catch (e: Exception) {
            throw ExecutionException(
                "Error in '${expr.name}': ${e.message}",
                expr.position
            )
        }
    }
}

/**
 * Exception thrown during DSL execution.
 */
class ExecutionException(
    message: String,
    val position: Int? = null
) : RuntimeException(message)
