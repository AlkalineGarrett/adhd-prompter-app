package org.alkaline.taskbrain.dsl

/**
 * Evaluates DSL expressions and produces runtime values.
 *
 * Milestone 1: Only evaluates literal expressions.
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
