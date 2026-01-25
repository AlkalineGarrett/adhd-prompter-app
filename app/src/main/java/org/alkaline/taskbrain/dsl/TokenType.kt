package org.alkaline.taskbrain.dsl

/**
 * Token types recognized by the DSL lexer.
 * Milestone 1: Literals only (LBRACKET, RBRACKET, NUMBER, STRING, EOF)
 */
enum class TokenType {
    // Delimiters
    LBRACKET,    // [
    RBRACKET,    // ]

    // Literals
    NUMBER,      // Integer or decimal: 123, 45.67
    STRING,      // Double-quoted: "hello"

    // Special
    EOF          // End of input
}
