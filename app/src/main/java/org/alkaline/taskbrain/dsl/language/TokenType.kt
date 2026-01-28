package org.alkaline.taskbrain.dsl.language

/**
 * Token types recognized by the Mindl lexer.
 * Milestone 3: Adds parentheses, comma, and colon for function call syntax.
 * Milestone 4: Adds star and dot-dot for pattern quantifiers.
 * Milestone 6: Adds dot for property access.
 * Milestone 7: Adds semicolon for statement separation.
 */
enum class TokenType {
    // Delimiters
    LBRACKET,    // [
    RBRACKET,    // ]
    LPAREN,      // (
    RPAREN,      // )
    COMMA,       // ,
    COLON,       // :
    SEMICOLON,   // ; (statement separator) - Milestone 7

    // Operators
    DOT,         // . (property access, current note reference) - Milestone 6
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
