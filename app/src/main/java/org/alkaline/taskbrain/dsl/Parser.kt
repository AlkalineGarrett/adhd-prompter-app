package org.alkaline.taskbrain.dsl

/**
 * Recursive descent parser for the TaskBrain DSL.
 * Produces an AST from a sequence of tokens.
 *
 * Milestone 1: Parses single literal expressions [number] or [string]
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
     * Milestone 1: Only literals are supported.
     */
    private fun parseExpression(): Expression {
        return parseLiteral()
    }

    private fun parseLiteral(): Expression {
        return when {
            match(TokenType.NUMBER) -> {
                val token = previous()
                NumberLiteral(token.literal as Double, token.position)
            }
            match(TokenType.STRING) -> {
                val token = previous()
                StringLiteral(token.literal as String, token.position)
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
