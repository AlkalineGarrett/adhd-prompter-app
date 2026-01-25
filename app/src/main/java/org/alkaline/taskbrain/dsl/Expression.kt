package org.alkaline.taskbrain.dsl

/**
 * Base class for all AST expression nodes.
 * Each subclass represents a different syntactic construct.
 *
 * Milestone 3: Adds support for parenthesized calls with named arguments.
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
 * A named argument in a function call.
 * Example: path: "foo" in find(path: "foo")
 */
data class NamedArg(
    val name: String,
    val value: Expression,
    val position: Int
)

/**
 * A function call expression.
 * Example: date, iso8601 date, add(1, 2), find(path: "foo")
 *
 * Supports two calling styles:
 * 1. Space-separated: identifiers nest right-to-left
 *    - [a b c] parses as CallExpr("a", [CallExpr("b", [CallExpr("c", [])])])
 * 2. Parenthesized: explicit argument lists with optional named args
 *    - [add(1, 2)] parses as CallExpr("add", [NumberLiteral(1), NumberLiteral(2)])
 *    - [foo(bar: "baz")] parses with named arg
 *
 * Named arguments come after positional arguments in the namedArgs list.
 */
data class CallExpr(
    val name: String,
    val args: List<Expression>,
    override val position: Int,
    val namedArgs: List<NamedArg> = emptyList()
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
