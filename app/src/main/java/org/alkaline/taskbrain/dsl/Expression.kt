package org.alkaline.taskbrain.dsl

/**
 * Base class for all AST expression nodes.
 * Each subclass represents a different syntactic construct.
 *
 * Milestone 1: NumberLiteral, StringLiteral only
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
 * A complete directive enclosed in brackets.
 * Example: [42], ["hello"]
 */
data class Directive(
    val expression: Expression,
    val sourceText: String,
    val startPosition: Int
)
