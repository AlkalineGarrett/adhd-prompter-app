package org.alkaline.taskbrain.dsl

/**
 * Base class for all AST expression nodes.
 * Each subclass represents a different syntactic construct.
 *
 * Milestone 2: Adds CallExpr for function calls.
 */
sealed class Expression {
    /** The position in source where this expression starts */
    abstract val position: Int
}

/**
 * A numeric literal (integer or decimal).
 * Example: 42, 3.14
 */
data class NumberLiteral(
    val value: Double,
    override val position: Int
) : Expression()

/**
 * A string literal.
 * Example: "hello world"
 */
data class StringLiteral(
    val value: String,
    override val position: Int
) : Expression()

/**
 * A function call expression.
 * Example: date, iso8601 date, add(1, 2)
 *
 * In the DSL, space-separated identifiers nest right-to-left:
 * - [a b c] parses as CallExpr("a", [CallExpr("b", [CallExpr("c", [])])])
 * - This means "call a with the result of calling b with the result of calling c"
 */
data class CallExpr(
    val name: String,
    val args: List<Expression>,
    override val position: Int
) : Expression()

/**
 * A complete directive enclosed in brackets.
 * Example: [42], ["hello"], [date]
 */
data class Directive(
    val expression: Expression,
    val sourceText: String,
    val startPosition: Int
)
