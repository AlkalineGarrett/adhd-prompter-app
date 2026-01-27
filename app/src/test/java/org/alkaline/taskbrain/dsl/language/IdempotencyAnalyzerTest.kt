package org.alkaline.taskbrain.dsl.language

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for IdempotencyAnalyzer.
 *
 * Tests verify that:
 * - Pure computations and views are idempotent
 * - Property assignments (.path, .name) are idempotent
 * - maybe_new() is idempotent
 * - .append() is non-idempotent
 * - new() is non-idempotent
 * - Non-idempotency propagates through statement lists
 */
class IdempotencyAnalyzerTest {

    private fun parse(source: String): Expression {
        val tokens = Lexer(source).tokenize()
        return Parser(tokens, source).parseDirective().expression
    }

    private fun isIdempotent(source: String): Boolean {
        return IdempotencyAnalyzer.analyze(parse(source)).isIdempotent
    }

    private fun getNonIdempotentReason(source: String): String? {
        return IdempotencyAnalyzer.analyze(parse(source)).nonIdempotentReason
    }

    // region Pure computations are idempotent

    @Test
    fun `number literal is idempotent`() {
        assertTrue(isIdempotent("[42]"))
    }

    @Test
    fun `string literal is idempotent`() {
        assertTrue(isIdempotent("[\"hello\"]"))
    }

    @Test
    fun `function call is idempotent`() {
        assertTrue(isIdempotent("[add(1, 2)]"))
    }

    @Test
    fun `date function is idempotent`() {
        assertTrue(isIdempotent("[now]"))
    }

    @Test
    fun `find function is idempotent`() {
        assertTrue(isIdempotent("[find(path: \"test\")]"))
    }

    @Test
    fun `pattern is idempotent`() {
        assertTrue(isIdempotent("[pattern(digit*4)]"))
    }

    // endregion

    // region Property access is idempotent

    @Test
    fun `current note reference is idempotent`() {
        assertTrue(isIdempotent("[.]"))
    }

    @Test
    fun `property access is idempotent`() {
        assertTrue(isIdempotent("[.path]"))
    }

    @Test
    fun `chained property access is idempotent`() {
        assertTrue(isIdempotent("[.root.path]"))
    }

    // endregion

    // region Idempotent mutations

    @Test
    fun `path assignment is idempotent`() {
        assertTrue(isIdempotent("[.path: \"new/path\"]"))
    }

    @Test
    fun `name assignment is idempotent`() {
        assertTrue(isIdempotent("[.name: \"New Name\"]"))
    }

    @Test
    fun `maybe_new is idempotent`() {
        assertTrue(isIdempotent("[maybe_new(path: \"test\")]"))
    }

    @Test
    fun `maybe_new with content is idempotent`() {
        assertTrue(isIdempotent("[maybe_new(path: \"test\", maybe_content: \"Hello\")]"))
    }

    // endregion

    // region Non-idempotent mutations

    @Test
    fun `append method is non-idempotent`() {
        assertFalse(isIdempotent("[.append(\"text\")]"))
        val reason = getNonIdempotentReason("[.append(\"text\")]")
        assertTrue(reason?.contains("append") == true)
        assertTrue(reason?.contains("button") == true || reason?.contains("schedule") == true)
    }

    @Test
    fun `new function is non-idempotent`() {
        assertFalse(isIdempotent("[new(path: \"test\")]"))
        val reason = getNonIdempotentReason("[new(path: \"test\")]")
        assertTrue(reason?.contains("new") == true)
        assertTrue(reason?.contains("button") == true || reason?.contains("schedule") == true)
    }

    @Test
    fun `chained append is non-idempotent`() {
        assertFalse(isIdempotent("[.root.append(\"text\")]"))
    }

    // endregion

    // region Propagation through statement lists

    @Test
    fun `statement list with only idempotent ops is idempotent`() {
        assertTrue(isIdempotent("[x: 5; y: 10; add(x, y)]"))
    }

    @Test
    fun `statement list with append is non-idempotent`() {
        assertFalse(isIdempotent("[x: \"text\"; .append(x)]"))
    }

    @Test
    fun `statement list with new is non-idempotent`() {
        assertFalse(isIdempotent("[path: \"test\"; new(path: path)]"))
    }

    @Test
    fun `non-idempotent in middle of list makes whole list non-idempotent`() {
        assertFalse(isIdempotent("[x: 1; .append(\"middle\"); y: 2]"))
    }

    // endregion

    // region Variable assignment with non-idempotent value

    @Test
    fun `variable assigned to non-idempotent value is non-idempotent`() {
        assertFalse(isIdempotent("[note: new(path: \"test\")]"))
    }

    @Test
    fun `variable assigned to idempotent value is idempotent`() {
        assertTrue(isIdempotent("[x: add(1, 2)]"))
    }

    // endregion
}
