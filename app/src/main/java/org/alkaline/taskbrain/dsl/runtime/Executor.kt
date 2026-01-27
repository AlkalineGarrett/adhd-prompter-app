package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.dsl.language.Assignment
import org.alkaline.taskbrain.dsl.language.CallExpr
import org.alkaline.taskbrain.dsl.language.CurrentNoteRef
import org.alkaline.taskbrain.dsl.language.Directive
import org.alkaline.taskbrain.dsl.language.Expression
import org.alkaline.taskbrain.dsl.language.MethodCall
import org.alkaline.taskbrain.dsl.language.NumberLiteral
import org.alkaline.taskbrain.dsl.language.PatternExpr
import org.alkaline.taskbrain.dsl.language.PropertyAccess
import org.alkaline.taskbrain.dsl.language.StatementList
import org.alkaline.taskbrain.dsl.language.StringLiteral
import org.alkaline.taskbrain.dsl.language.VariableRef

/**
 * Evaluates DSL expressions and produces runtime values.
 *
 * Milestone 3: Supports parenthesized calls with named arguments.
 * Milestone 4: Supports pattern expressions.
 * Milestone 6: Supports current note reference and property access.
 * Milestone 7: Supports assignment, statement lists, variables, and method calls.
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
     *
     * Milestone 7: Added Assignment, StatementList, VariableRef, MethodCall.
     */
    fun evaluate(expr: Expression, env: Environment): DslValue {
        return when (expr) {
            is NumberLiteral -> NumberVal(expr.value)
            is StringLiteral -> StringVal(expr.value)
            is CallExpr -> evaluateCall(expr, env)
            is PatternExpr -> evaluatePattern(expr)
            is CurrentNoteRef -> evaluateCurrentNoteRef(expr, env)
            is PropertyAccess -> evaluatePropertyAccess(expr, env)
            is Assignment -> evaluateAssignment(expr, env)
            is StatementList -> evaluateStatementList(expr, env)
            is VariableRef -> evaluateVariableRef(expr, env)
            is MethodCall -> evaluateMethodCall(expr, env)
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
            is NoteVal -> target.getProperty(expr.property, env)
            else -> throw ExecutionException(
                "Cannot access property '${expr.property}' on ${target.typeName}",
                expr.position
            )
        }
    }

    /**
     * Evaluate an assignment expression.
     * Example: [x: 5], [.path: "foo"]
     *
     * Milestone 7.
     */
    private fun evaluateAssignment(expr: Assignment, env: Environment): DslValue {
        val value = evaluate(expr.value, env)

        when (val target = expr.target) {
            is VariableRef -> {
                // Variable definition: [x: 5]
                env.define(target.name, value)
            }
            is PropertyAccess -> {
                // Property assignment: [.path: "foo"] or [note.path: "bar"]
                val targetObj = evaluate(target.target, env)
                when (targetObj) {
                    is NoteVal -> {
                        targetObj.setProperty(target.property, value, env)
                    }
                    else -> throw ExecutionException(
                        "Cannot assign to property '${target.property}' on ${targetObj.typeName}",
                        expr.position
                    )
                }
            }
            is CurrentNoteRef -> {
                // [.: value] - not really meaningful, but could be used to replace note
                throw ExecutionException(
                    "Cannot assign directly to current note. Use property assignment like [.path: value]",
                    expr.position
                )
            }
            else -> throw ExecutionException(
                "Invalid assignment target",
                expr.position
            )
        }

        return value
    }

    /**
     * Evaluate a statement list, returning the value of the last statement.
     * Example: [x: 5; y: 10; add(x, y)]
     *
     * Milestone 7.
     */
    private fun evaluateStatementList(expr: StatementList, env: Environment): DslValue {
        var result: DslValue = StringVal("")  // Default if empty
        for (statement in expr.statements) {
            result = evaluate(statement, env)
        }
        return result
    }

    /**
     * Evaluate a variable reference.
     * Example: [x] where x was previously defined
     *
     * Milestone 7.
     */
    private fun evaluateVariableRef(expr: VariableRef, env: Environment): DslValue {
        return env.get(expr.name)
            ?: throw ExecutionException(
                "Undefined variable '${expr.name}'",
                expr.position
            )
    }

    /**
     * Evaluate a method call on an expression.
     * Example: [.append("text")], [note.append("text")]
     *
     * Milestone 7.
     */
    private fun evaluateMethodCall(expr: MethodCall, env: Environment): DslValue {
        val target = evaluate(expr.target, env)

        // Evaluate arguments
        val positionalArgs = expr.args.map { evaluate(it, env) }
        val namedArgs = expr.namedArgs.associate { it.name to evaluate(it.value, env) }
        val args = Arguments(positionalArgs, namedArgs)

        // Dispatch to the appropriate method based on target type
        return when (target) {
            is NoteVal -> target.callMethod(expr.methodName, args, env, expr.position)
            else -> throw ExecutionException(
                "Cannot call method '${expr.methodName}' on ${target.typeName}",
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
     *
     * Milestone 7: For zero-arg calls, first check if it's a variable reference.
     */
    private fun evaluateCall(expr: CallExpr, env: Environment): DslValue {
        // For zero-arg calls, first check if this is a variable reference
        // This allows [x: 5; x] where second 'x' refers to the variable
        if (expr.args.isEmpty() && expr.namedArgs.isEmpty()) {
            val variableValue = env.get(expr.name)
            if (variableValue != null) {
                return variableValue
            }
        }

        val function = BuiltinRegistry.get(expr.name)
            ?: throw ExecutionException(
                "Unknown function or variable '${expr.name}'",
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
