package org.alkaline.taskbrain.dsl.language

/**
 * Token types recognized by the Mindl lexer.
 * Adds parentheses, comma, and colon for function call syntax.
 * Adds star and dot-dot for pattern quantifiers.
 * Adds dot for property access.
 * Adds semicolon for statement separation.
 */
enum class TokenType {
    // Delimiters
    LBRACKET,    // [
    RBRACKET,    // ]
    LPAREN,      // (
    RPAREN,      // )
    COMMA,       // ,
    COLON,       // :
    SEMICOLON,   // ; (statement separator)

    // Operators
    DOT,         // . (property access, current note reference)
    STAR,        // * (quantifier prefix)
    DOTDOT,      // .. (range separator in quantifiers)

    // Literals
    NUMBER,      // Integer or decimal: 123, 45.67
    STRING,      // Double-quoted: "hello"

    // Identifiers
    IDENTIFIER,  // Function names, variables: date, iso8601, my_var

    // Special
    EOF          // End of input
}
