package org.alkaline.taskbrain.dsl

/**
 * Token types recognized by the DSL lexer.
 * Milestone 2: Adds IDENTIFIER for function names and variables.
 */
enum class TokenType {
    // Delimiters
    LBRACKET,    // [
    RBRACKET,    // ]

    // Literals
    NUMBER,      // Integer or decimal: 123, 45.67
    STRING,      // Double-quoted: "hello"

    // Identifiers
    IDENTIFIER,  // Function names, variables: date, iso8601, my_var

    // Special
    EOF          // End of input
}
