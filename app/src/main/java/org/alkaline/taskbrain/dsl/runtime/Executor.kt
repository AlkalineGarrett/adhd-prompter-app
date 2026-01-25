package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.dsl.language.CallExpr
import org.alkaline.taskbrain.dsl.language.Directive
import org.alkaline.taskbrain.dsl.language.Expression
import org.alkaline.taskbrain.dsl.language.NumberLiteral
import org.alkaline.taskbrain.dsl.language.StringLiteral

/**
 * Evaluates DSL expressions and produces runtime values.
 *
 * Milestone 3: Supports parenthesized calls with named arguments.
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

        // Evaluate positional arguments
        val positionalValues = expr.args.map { evaluate(it, env) }

        // Evaluate named arguments
        val namedValues = expr.namedArgs.associate { namedArg ->
            namedArg.name to evaluate(namedArg.value, env)
        }

        // Build Arguments container
        val args = Arguments(positionalValues, namedValues)

        // Call the function
        return try {
            function.call(args, env)
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
