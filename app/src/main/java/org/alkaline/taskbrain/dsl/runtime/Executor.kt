package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.dsl.language.CallExpr
import org.alkaline.taskbrain.dsl.language.CurrentNoteRef
import org.alkaline.taskbrain.dsl.language.Directive
import org.alkaline.taskbrain.dsl.language.Expression
import org.alkaline.taskbrain.dsl.language.NumberLiteral
import org.alkaline.taskbrain.dsl.language.PatternExpr
import org.alkaline.taskbrain.dsl.language.PropertyAccess
import org.alkaline.taskbrain.dsl.language.StringLiteral

/**
 * Evaluates DSL expressions and produces runtime values.
 *
 * Milestone 3: Supports parenthesized calls with named arguments.
 * Milestone 4: Supports pattern expressions.
 * Milestone 6: Supports current note reference and property access.
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
            is PatternExpr -> evaluatePattern(expr)
            is CurrentNoteRef -> evaluateCurrentNoteRef(expr, env)
            is PropertyAccess -> evaluatePropertyAccess(expr, env)
        }
    }

    /**
     * Evaluate a current note reference.
     * Returns the current note from the environment.
     *
     * Milestone 6.
     */
    private fun evaluateCurrentNoteRef(expr: CurrentNoteRef, env: Environment): NoteVal {
        return env.getCurrentNote()
            ?: throw ExecutionException(
                "No current note in context (use [.] only within a note)",
                expr.position
            )
    }

    /**
     * Evaluate property access on an expression.
     * Example: note.path, note.created
     *
     * Milestone 6.
     */
    private fun evaluatePropertyAccess(expr: PropertyAccess, env: Environment): DslValue {
        val target = evaluate(expr.target, env)
        return when (target) {
            is NoteVal -> target.getProperty(expr.property)
            else -> throw ExecutionException(
                "Cannot access property '${expr.property}' on ${target.typeName}",
                expr.position
            )
        }
    }

    /**
     * Evaluate a pattern expression to produce a PatternVal.
     */
    private fun evaluatePattern(expr: PatternExpr): PatternVal {
        return PatternVal.compile(expr.elements)
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
