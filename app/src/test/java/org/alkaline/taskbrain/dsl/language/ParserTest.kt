package org.alkaline.taskbrain.dsl.language

import org.junit.Assert.*
import org.junit.Test

class ParserTest {

    private fun parse(source: String): Directive {
        val tokens = Lexer(source).tokenize()
        return Parser(tokens, source).parseDirective()
    }

    // region Number literals

    @Test
    fun `parses integer literal`() {
        val directive = parse("[42]")

        assertTrue(directive.expression is NumberLiteral)
        assertEquals(42.0, (directive.expression as NumberLiteral).value, 0.0)
    }

    @Test
    fun `parses decimal literal`() {
        val directive = parse("[3.14]")

        assertTrue(directive.expression is NumberLiteral)
        assertEquals(3.14, (directive.expression as NumberLiteral).value, 0.0)
    }

    @Test
    fun `parses zero`() {
        val directive = parse("[0]")

        assertTrue(directive.expression is NumberLiteral)
        assertEquals(0.0, (directive.expression as NumberLiteral).value, 0.0)
    }

    // endregion

    // region String literals

    @Test
    fun `parses simple string`() {
        val directive = parse("[\"hello\"]")

        assertTrue(directive.expression is StringLiteral)
        assertEquals("hello", (directive.expression as StringLiteral).value)
    }

    @Test
    fun `parses empty string`() {
        val directive = parse("[\"\"]")

        assertTrue(directive.expression is StringLiteral)
        assertEquals("", (directive.expression as StringLiteral).value)
    }

    @Test
    fun `parses string with special characters`() {
        val directive = parse("[\"hello-world_123\"]")

        assertTrue(directive.expression is StringLiteral)
        assertEquals("hello-world_123", (directive.expression as StringLiteral).value)
    }

    // endregion

    // region Directive metadata

    @Test
    fun `captures source text`() {
        val directive = parse("[42]")

        assertEquals("[42]", directive.sourceText)
    }

    @Test
    fun `captures source text with whitespace`() {
        val directive = parse("[ 42 ]")

        assertEquals("[ 42 ]", directive.sourceText)
    }

    @Test
    fun `captures start position`() {
        val directive = parse("[42]")

        assertEquals(0, directive.startPosition)
    }

    @Test
    fun `captures expression position`() {
        val directive = parse("[42]")

        assertEquals(1, directive.expression.position)
    }

    @Test
    fun `captures expression position with whitespace`() {
        val directive = parse("[ 42 ]")

        assertEquals(2, directive.expression.position)
    }

    // endregion

    // region Error cases

    @Test
    fun `throws on missing opening bracket`() {
        val exception = assertThrows(ParseException::class.java) {
            val tokens = Lexer("42]").tokenize()
            Parser(tokens, "42]").parseDirective()
        }
        assertTrue(exception.message!!.contains("Expected '['"))
    }

    @Test
    fun `throws on missing closing bracket`() {
        val exception = assertThrows(ParseException::class.java) {
            parse("[42")
        }
        assertTrue(exception.message!!.contains("Expected ']'"))
    }

    @Test
    fun `throws on empty directive`() {
        val exception = assertThrows(ParseException::class.java) {
            parse("[]")
        }
        assertTrue(exception.message!!.contains("Expected expression"))
    }

    // endregion

    // region Function calls (Milestone 2)

    @Test
    fun `parses single identifier as zero-arg call`() {
        val directive = parse("[date]")

        assertTrue(directive.expression is CallExpr)
        val call = directive.expression as CallExpr
        assertEquals("date", call.name)
        assertEquals(0, call.args.size)
    }

    @Test
    fun `parses two identifiers with right-to-left nesting`() {
        val directive = parse("[format date]")

        // Should parse as: format(date())
        assertTrue(directive.expression is CallExpr)
        val outer = directive.expression as CallExpr
        assertEquals("format", outer.name)
        assertEquals(1, outer.args.size)

        assertTrue(outer.args[0] is CallExpr)
        val inner = outer.args[0] as CallExpr
        assertEquals("date", inner.name)
        assertEquals(0, inner.args.size)
    }

    @Test
    fun `parses three identifiers with right-to-left nesting`() {
        val directive = parse("[a b c]")

        // Should parse as: a(b(c()))
        assertTrue(directive.expression is CallExpr)
        val a = directive.expression as CallExpr
        assertEquals("a", a.name)
        assertEquals(1, a.args.size)

        assertTrue(a.args[0] is CallExpr)
        val b = a.args[0] as CallExpr
        assertEquals("b", b.name)
        assertEquals(1, b.args.size)

        assertTrue(b.args[0] is CallExpr)
        val c = b.args[0] as CallExpr
        assertEquals("c", c.name)
        assertEquals(0, c.args.size)
    }

    @Test
    fun `captures identifier position`() {
        val directive = parse("[date]")

        val call = directive.expression as CallExpr
        assertEquals(1, call.position)  // Position after the '['
    }

    @Test
    fun `captures source text for function call`() {
        val directive = parse("[format date]")

        assertEquals("[format date]", directive.sourceText)
    }

    // endregion
}
