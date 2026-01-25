package org.alkaline.taskbrain.dsl

import org.junit.Assert.*
import org.junit.Test

class LexerTest {

    // region Brackets

    @Test
    fun `tokenizes empty directive brackets`() {
        val tokens = Lexer("[]").tokenize()

        assertEquals(3, tokens.size)
        assertEquals(TokenType.LBRACKET, tokens[0].type)
        assertEquals(TokenType.RBRACKET, tokens[1].type)
        assertEquals(TokenType.EOF, tokens[2].type)
    }

    @Test
    fun `tokenizes brackets with whitespace`() {
        val tokens = Lexer("[  ]").tokenize()

        assertEquals(3, tokens.size)
        assertEquals(TokenType.LBRACKET, tokens[0].type)
        assertEquals(TokenType.RBRACKET, tokens[1].type)
    }

    // endregion

    // region Numbers

    @Test
    fun `tokenizes integer`() {
        val tokens = Lexer("[42]").tokenize()

        assertEquals(4, tokens.size)
        assertEquals(TokenType.NUMBER, tokens[1].type)
        assertEquals("42", tokens[1].lexeme)
        assertEquals(42.0, tokens[1].literal)
    }

    @Test
    fun `tokenizes decimal number`() {
        val tokens = Lexer("[3.14]").tokenize()

        assertEquals(TokenType.NUMBER, tokens[1].type)
        assertEquals("3.14", tokens[1].lexeme)
        assertEquals(3.14, tokens[1].literal)
    }

    @Test
    fun `tokenizes zero`() {
        val tokens = Lexer("[0]").tokenize()

        assertEquals(TokenType.NUMBER, tokens[1].type)
        assertEquals(0.0, tokens[1].literal)
    }

    @Test
    fun `tokenizes large number`() {
        val tokens = Lexer("[123456789]").tokenize()

        assertEquals(TokenType.NUMBER, tokens[1].type)
        assertEquals(123456789.0, tokens[1].literal)
    }

    @Test
    fun `tokenizes number with leading zeros in decimal`() {
        val tokens = Lexer("[1.001]").tokenize()

        assertEquals(TokenType.NUMBER, tokens[1].type)
        assertEquals(1.001, tokens[1].literal)
    }

    // endregion

    // region Strings

    @Test
    fun `tokenizes simple string`() {
        val tokens = Lexer("[\"hello\"]").tokenize()

        assertEquals(4, tokens.size)
        assertEquals(TokenType.STRING, tokens[1].type)
        assertEquals("hello", tokens[1].literal)
    }

    @Test
    fun `tokenizes empty string`() {
        val tokens = Lexer("[\"\"]").tokenize()

        assertEquals(TokenType.STRING, tokens[1].type)
        assertEquals("", tokens[1].literal)
    }

    @Test
    fun `tokenizes string with spaces`() {
        val tokens = Lexer("[\"hello world\"]").tokenize()

        assertEquals(TokenType.STRING, tokens[1].type)
        assertEquals("hello world", tokens[1].literal)
    }

    @Test
    fun `tokenizes string with special characters literally`() {
        // No escape sequences - backslash is just a backslash
        val tokens = Lexer("[\"path\\file\"]").tokenize()

        assertEquals(TokenType.STRING, tokens[1].type)
        assertEquals("path\\file", tokens[1].literal)
    }

    @Test
    fun `tokenizes string with numbers and punctuation`() {
        val tokens = Lexer("[\"test-123_abc\"]").tokenize()

        assertEquals(TokenType.STRING, tokens[1].type)
        assertEquals("test-123_abc", tokens[1].literal)
    }

    // endregion

    // region Whitespace handling

    @Test
    fun `ignores leading and trailing whitespace`() {
        val tokens = Lexer("  [ 42 ]  ").tokenize()

        assertEquals(4, tokens.size)
        assertEquals(TokenType.LBRACKET, tokens[0].type)
        assertEquals(TokenType.NUMBER, tokens[1].type)
        assertEquals(TokenType.RBRACKET, tokens[2].type)
    }

    @Test
    fun `handles newlines as whitespace`() {
        val tokens = Lexer("[\n42\n]").tokenize()

        assertEquals(4, tokens.size)
        assertEquals(TokenType.NUMBER, tokens[1].type)
    }

    @Test
    fun `handles tabs as whitespace`() {
        val tokens = Lexer("[\t42\t]").tokenize()

        assertEquals(4, tokens.size)
        assertEquals(TokenType.NUMBER, tokens[1].type)
    }

    // endregion

    // region Position tracking

    @Test
    fun `tracks token positions correctly`() {
        val tokens = Lexer("[42]").tokenize()

        assertEquals(0, tokens[0].position) // [
        assertEquals(1, tokens[1].position) // 42
        assertEquals(3, tokens[2].position) // ]
    }

    @Test
    fun `tracks positions with whitespace`() {
        val tokens = Lexer("[ 42 ]").tokenize()

        assertEquals(0, tokens[0].position)  // [
        assertEquals(2, tokens[1].position)  // 42
        assertEquals(5, tokens[2].position)  // ]
    }

    // endregion

    // region Error cases

    @Test
    fun `throws on unterminated string`() {
        val exception = assertThrows(LexerException::class.java) {
            Lexer("[\"hello]").tokenize()
        }
        assertTrue(exception.message!!.contains("Unterminated string"))
    }

    @Test
    fun `throws on unexpected character`() {
        val exception = assertThrows(LexerException::class.java) {
            Lexer("[@]").tokenize()
        }
        assertTrue(exception.message!!.contains("Unexpected character"))
    }

    // endregion
}
