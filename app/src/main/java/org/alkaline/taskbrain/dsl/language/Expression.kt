package org.alkaline.taskbrain.dsl.language

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

// ============================================================================
// Pattern AST Nodes (Milestone 4)
// ============================================================================

/**
 * Character class types for pattern matching.
 */
enum class CharClassType {
    DIGIT,   // Matches 0-9
    LETTER,  // Matches a-z, A-Z
    SPACE,   // Matches whitespace
    PUNCT,   // Matches punctuation
    ANY      // Matches any character
}

/**
 * Quantifier for pattern elements.
 */
sealed class Quantifier {
    /** Matches exactly n times. Example: *4 */
    data class Exact(val n: Int) : Quantifier()

    /** Matches between min and max times. max=null means unbounded. Example: *(0..5), *(1..) */
    data class Range(val min: Int, val max: Int?) : Quantifier()

    /** Matches any number of times (0+). Equivalent to Range(0, null). Example: *any */
    data object Any : Quantifier()
}

/**
 * A single element in a pattern.
 */
sealed class PatternElement {
    /** The position in source where this element starts */
    abstract val position: Int
}

/**
 * A character class in a pattern.
 * Example: digit, letter, space, punct, any
 */
data class CharClass(
    val type: CharClassType,
    override val position: Int
) : PatternElement()

/**
 * A literal string in a pattern.
 * Example: "-" in pattern(digit*4 "-" digit*2)
 */
data class PatternLiteral(
    val value: String,
    override val position: Int
) : PatternElement()

/**
 * A quantified pattern element.
 * Example: digit*4, letter*any, any*(1..)
 */
data class Quantified(
    val element: PatternElement,
    val quantifier: Quantifier,
    override val position: Int
) : PatternElement()

/**
 * A complete pattern expression.
 * Example: pattern(digit*4 "-" digit*2 "-" digit*2)
 */
data class PatternExpr(
    val elements: List<PatternElement>,
    override val position: Int
) : Expression()
