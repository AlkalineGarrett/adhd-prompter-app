package org.alkaline.taskbrain.dsl.language

/**
 * Lexer for the TaskBrain DSL.
 * Converts source text into a sequence of tokens.
 *
 * Milestone 3: Adds parentheses, comma, and colon for function call syntax.
 * Milestone 4: Adds star (*) and dot-dot (..) for pattern quantifiers.
 *
 * Note: Strings have no escape sequences (mobile-friendly design).
 * Special characters like quotes and newlines are inserted using
 * constants (qt, nl, tab, ret) with the string() function.
 */
class Lexer(private val source: String) {
    private var start = 0
    private var current = 0
    private val tokens = mutableListOf<Token>()

    /**
     * Tokenize the entire source string.
     * @return List of tokens ending with EOF
     * @throws LexerException on invalid input
     */
    fun tokenize(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }
        tokens.add(Token(TokenType.EOF, "", null, current))
        return tokens
    }

    private fun scanToken() {
        when (val c = advance()) {
            '[' -> addToken(TokenType.LBRACKET)
            ']' -> addToken(TokenType.RBRACKET)
            '(' -> addToken(TokenType.LPAREN)
            ')' -> addToken(TokenType.RPAREN)
            ',' -> addToken(TokenType.COMMA)
            ':' -> addToken(TokenType.COLON)
            '*' -> addToken(TokenType.STAR)
            '.' -> dotOrDotDot()
            '"' -> string()
            ' ', '\t', '\r', '\n' -> { /* skip whitespace */ }
            else -> when {
                c.isDigit() -> number()
                c.isIdentifierStart() -> identifier()
                else -> throw LexerException("Unexpected character '$c'", current - 1)
            }
        }
    }

    /**
     * Handle '.' - either a single dot (for property access, Milestone 6)
     * or '..' for pattern range quantifiers (Milestone 4).
     */
    private fun dotOrDotDot() {
        if (peek() == '.') {
            advance() // consume second '.'
            addToken(TokenType.DOTDOT)
        } else {
            // Single dot - for property access (Milestone 6)
            addToken(TokenType.DOT)
        }
    }

    /**
     * Parse an identifier (function name or variable).
     * Identifiers start with a letter or underscore, followed by
     * letters, digits, or underscores.
     */
    private fun identifier() {
        while (peek().isIdentifierPart()) advance()

        val text = source.substring(start, current)
        addToken(TokenType.IDENTIFIER, text)
    }

    private fun Char.isIdentifierStart(): Boolean = isLetter() || this == '_'

    private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_'

    private fun number() {
        while (peek().isDigit()) advance()

        // Look for decimal part
        if (peek() == '.' && peekNext().isDigit()) {
            advance() // consume the '.'
            while (peek().isDigit()) advance()
        }

        val text = source.substring(start, current)
        addToken(TokenType.NUMBER, text.toDouble())
    }

    /**
     * Parse a string literal. Strings are delimited by double quotes and
     * have no escape sequences - all characters are literal.
     * To include special characters, use the string() function with constants
     * like qt (quote), nl (newline), tab, ret (carriage return).
     */
    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            advance()
        }

        if (isAtEnd()) {
            throw LexerException("Unterminated string", start)
        }

        advance() // closing "

        // Extract string content (without the quotes)
        val content = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, content)
    }

    private fun isAtEnd(): Boolean = current >= source.length

    private fun advance(): Char = source[current++]

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]

    private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]

    private fun addToken(type: TokenType, literal: Any? = null) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, start))
    }
}

/**
 * Exception thrown when the lexer encounters invalid input.
 */
class LexerException(
    message: String,
    val position: Int
) : RuntimeException("Lexer error at position $position: $message")
