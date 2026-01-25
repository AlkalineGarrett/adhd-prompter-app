package org.alkaline.taskbrain.dsl

/**
 * Token types recognized by the DSL lexer.
 * Milestone 3: Adds parentheses, comma, and colon for function call syntax.
 */
enum class TokenType {
    // Delimiters
    LBRACKET,    // [
    RBRACKET,    // ]
    LPAREN,      // (
    RPAREN,      // )
    COMMA,       // ,
    COLON,       // :

    // Literals
    NUMBER,      // Integer or decimal: 123, 45.67
    STRING,      // Double-quoted: "hello"

    // Identifiers
    IDENTIFIER,  // Function names, variables: date, iso8601, my_var

    // Special
    EOF          // End of input
}
