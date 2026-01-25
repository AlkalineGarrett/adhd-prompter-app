package org.alkaline.taskbrain.dsl

/**
 * Recursive descent parser for the TaskBrain DSL.
 * Produces an AST from a sequence of tokens.
 *
 * Milestone 2: Adds function calls with right-to-left nesting.
 * Space-separated tokens nest right-to-left:
 * - [a b c] parses as CallExpr("a", [CallExpr("b", [CallExpr("c", [])])])
 */
class Parser(private val tokens: List<Token>, private val source: String) {
    private var current = 0

    /**
     * Parse a complete directive.
     * @return The parsed Directive
     * @throws ParseException on syntax error
     */
    fun parseDirective(): Directive {
        val startToken = consume(TokenType.LBRACKET, "Expected '[' to start directive")
        val startPos = startToken.position
        val expression = parseExpression()
        consume(TokenType.RBRACKET, "Expected ']' to close directive")

        val endPos = previous().position + previous().lexeme.length
        val sourceText = source.substring(startPos, endPos)

        return Directive(expression, sourceText, startPos)
    }

    /**
     * Parse an expression.
     * Handles right-to-left nesting for space-separated identifiers.
     */
    private fun parseExpression(): Expression {
        return parseCallChain()
    }

    /**
     * Parse a chain of space-separated calls.
     * Uses right-to-left nesting: [a b c] -> a(b(c))
     */
    private fun parseCallChain(): Expression {
        val first = parsePrimary()

        // If the first element is not an identifier, it can't start a call chain
        if (first !is CallExpr) {
            return first
        }

        // Check if there are more expressions to nest
        if (!isAtExpressionStart()) {
            return first
        }

        // Parse the rest of the chain recursively
        val rest = parseCallChain()

        // Nest right-to-left: the rest becomes the argument to first
        return CallExpr(first.name, listOf(rest), first.position)
    }

    /**
     * Check if we're at the start of another expression (not at ] or EOF).
     */
    private fun isAtExpressionStart(): Boolean {
        return !check(TokenType.RBRACKET) && !isAtEnd()
    }

    /**
     * Parse a primary expression (literal or identifier).
     */
    private fun parsePrimary(): Expression {
        return when {
            match(TokenType.NUMBER) -> {
                val token = previous()
                NumberLiteral(token.literal as Double, token.position)
            }
            match(TokenType.STRING) -> {
                val token = previous()
                StringLiteral(token.literal as String, token.position)
            }
            match(TokenType.IDENTIFIER) -> {
                val token = previous()
                // An identifier alone becomes a zero-arg function call
                CallExpr(token.literal as String, emptyList(), token.position)
            }
            else -> throw ParseException(
                "Expected expression",
                peek().position
            )
        }
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw ParseException(message, peek().position)
    }
}

/**
 * Exception thrown when the parser encounters a syntax error.
 */
class ParseException(
    message: String,
    val position: Int
) : RuntimeException("Parse error at position $position: $message")
