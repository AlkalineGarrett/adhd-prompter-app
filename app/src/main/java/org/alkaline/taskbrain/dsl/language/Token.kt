package org.alkaline.taskbrain.dsl.language

/**
 * A token produced by the lexer.
 *
 * @property type The type of token
 * @property lexeme The raw text that was matched
 * @property literal The parsed value (e.g., Double for NUMBER, String for STRING)
 * @property position The character offset in the source where this token starts
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any? = null,
    val position: Int = 0
) {
    override fun toString(): String = when (literal) {
        null -> "$type '$lexeme'"
        else -> "$type '$lexeme' ($literal)"
    }
}
