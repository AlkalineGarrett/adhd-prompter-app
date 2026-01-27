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

    // region Variable assignment (Milestone 7)

    @Test
    fun `parses variable assignment`() {
        val directive = parse("[x: 5]")

        assertTrue(directive.expression is Assignment)
        val assignment = directive.expression as Assignment
        assertTrue(assignment.target is VariableRef)
        assertEquals("x", (assignment.target as VariableRef).name)
        assertTrue(assignment.value is NumberLiteral)
        assertEquals(5.0, (assignment.value as NumberLiteral).value, 0.0)
    }

    @Test
    fun `parses variable assignment with string value`() {
        val directive = parse("[name: \"hello\"]")

        assertTrue(directive.expression is Assignment)
        val assignment = directive.expression as Assignment
        assertEquals("name", (assignment.target as VariableRef).name)
        assertEquals("hello", (assignment.value as StringLiteral).value)
    }

    @Test
    fun `parses variable assignment with function call value`() {
        val directive = parse("[today: date]")

        assertTrue(directive.expression is Assignment)
        val assignment = directive.expression as Assignment
        assertEquals("today", (assignment.target as VariableRef).name)
        assertTrue(assignment.value is CallExpr)
        assertEquals("date", (assignment.value as CallExpr).name)
    }

    // endregion

    // region Property assignment (Milestone 7)

    @Test
    fun `parses property assignment on current note`() {
        val directive = parse("[.path: \"new/path\"]")

        assertTrue(directive.expression is Assignment)
        val assignment = directive.expression as Assignment
        assertTrue(assignment.target is PropertyAccess)
        val target = assignment.target as PropertyAccess
        assertTrue(target.target is CurrentNoteRef)
        assertEquals("path", target.property)
        assertEquals("new/path", (assignment.value as StringLiteral).value)
    }

    // endregion

    // region Statement list (Milestone 7)

    @Test
    fun `parses statement list with semicolons`() {
        val directive = parse("[a; b; c]")

        assertTrue(directive.expression is StatementList)
        val list = directive.expression as StatementList
        assertEquals(3, list.statements.size)
    }

    @Test
    fun `parses two statements`() {
        val directive = parse("[x: 5; x]")

        assertTrue(directive.expression is StatementList)
        val list = directive.expression as StatementList
        assertEquals(2, list.statements.size)
        assertTrue(list.statements[0] is Assignment)
        assertTrue(list.statements[1] is CallExpr)  // 'x' is parsed as CallExpr initially
    }

    @Test
    fun `single expression without semicolon is not StatementList`() {
        val directive = parse("[42]")

        assertTrue(directive.expression is NumberLiteral)
    }

    @Test
    fun `parses complex statement list`() {
        val directive = parse("[x: 5; y: 10; add(x, y)]")

        assertTrue(directive.expression is StatementList)
        val list = directive.expression as StatementList
        assertEquals(3, list.statements.size)
        assertTrue(list.statements[0] is Assignment)
        assertTrue(list.statements[1] is Assignment)
        assertTrue(list.statements[2] is CallExpr)
    }

    // endregion

    // region Method calls (Milestone 7)

    @Test
    fun `parses method call on current note`() {
        val directive = parse("[.append(\"text\")]")

        assertTrue(directive.expression is MethodCall)
        val call = directive.expression as MethodCall
        assertTrue(call.target is CurrentNoteRef)
        assertEquals("append", call.methodName)
        assertEquals(1, call.args.size)
        assertEquals("text", (call.args[0] as StringLiteral).value)
    }

    @Test
    fun `parses method call with no arguments`() {
        val directive = parse("[.someMethod()]")

        assertTrue(directive.expression is MethodCall)
        val call = directive.expression as MethodCall
        assertEquals("someMethod", call.methodName)
        assertEquals(0, call.args.size)
    }

    @Test
    fun `parses method call with multiple arguments`() {
        val directive = parse("[.method(1, 2, 3)]")

        assertTrue(directive.expression is MethodCall)
        val call = directive.expression as MethodCall
        assertEquals(3, call.args.size)
    }

    @Test
    fun `parses chained property access and method call`() {
        val directive = parse("[.path]")

        assertTrue(directive.expression is PropertyAccess)
        val access = directive.expression as PropertyAccess
        assertTrue(access.target is CurrentNoteRef)
        assertEquals("path", access.property)
    }

    // endregion
}
