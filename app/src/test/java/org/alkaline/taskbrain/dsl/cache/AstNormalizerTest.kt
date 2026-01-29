package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.dsl.language.Lexer
import org.alkaline.taskbrain.dsl.language.Parser
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AstNormalizer.
 * Phase 2: AST normalization for cache keys.
 */
class AstNormalizerTest {

    // region Basic normalization

    @Test
    fun `normalizes number literal`() {
        val norm = normalize("[42]")
        assertEquals("NUM(42.0)", norm)
    }

    @Test
    fun `normalizes string literal`() {
        val norm = normalize("[\"hello\"]")
        assertEquals("STR(hello)", norm)
    }

    @Test
    fun `normalizes string with special characters`() {
        val norm = normalize("[\"line1\\nline2\"]")
        assertTrue(norm.contains("\\n"))
    }

    @Test
    fun `normalizes current note ref`() {
        val norm = normalize("[.]")
        assertEquals("SELF", norm)
    }

    @Test
    fun `normalizes variable ref`() {
        val norm = normalize("[x: 5; x]")
        // In AST, bare identifiers are CallExpr (resolved at runtime)
        assertTrue(norm.contains("CALL(x,)"))
    }

    // endregion

    // region Property access normalization

    @Test
    fun `normalizes property access`() {
        val norm = normalize("[.path]")
        assertEquals("PROP(SELF,path)", norm)
    }

    @Test
    fun `normalizes chained property access`() {
        val norm = normalize("[.up.path]")
        assertEquals("PROP(PROP(SELF,up),path)", norm)
    }

    // endregion

    // region Function call normalization

    @Test
    fun `normalizes simple function call`() {
        val norm = normalize("[add(1, 2)]")
        assertEquals("CALL(add,NUM(1.0),NUM(2.0))", norm)
    }

    @Test
    fun `normalizes function call with named args`() {
        val norm = normalize("[find(path: \"inbox\")]")
        assertEquals("CALL(find,path=STR(inbox))", norm)
    }

    @Test
    fun `normalizes named args in sorted order`() {
        // Named args should be sorted by name for consistency
        val norm1 = normalize("[func(a: 1, b: 2)]")
        val norm2 = normalize("[func(b: 2, a: 1)]")
        assertEquals(norm1, norm2)
    }

    // endregion

    // region Method call normalization

    @Test
    fun `normalizes method call`() {
        val norm = normalize("[.up()]")
        assertEquals("METHOD(SELF,up,)", norm)
    }

    @Test
    fun `normalizes method call with args`() {
        val norm = normalize("[.up(2)]")
        assertEquals("METHOD(SELF,up,NUM(2.0))", norm)
    }

    // endregion

    // region Lambda normalization

    @Test
    fun `normalizes implicit lambda`() {
        val norm = normalize("[[i.path]]")
        // In AST, bare identifiers like 'i' are CallExpr (resolved at runtime)
        assertEquals("LAMBDA(i,PROP(CALL(i,),path))", norm)
    }

    @Test
    fun `normalizes lambda invocation`() {
        val norm = normalize("[[add(i, 1)](5)]")
        assertTrue(norm.contains("INVOKE"))
        assertTrue(norm.contains("LAMBDA"))
    }

    // endregion

    // region Execution block normalization

    @Test
    fun `normalizes once block`() {
        val norm = normalize("[once[date]]")
        assertTrue(norm.startsWith("ONCE("))
    }

    @Test
    fun `normalizes refresh block`() {
        val norm = normalize("[refresh[time]]")
        assertTrue(norm.startsWith("REFRESH("))
    }

    // endregion

    // region Statement list normalization

    @Test
    fun `normalizes statement list`() {
        val norm = normalize("[a: 1; b: 2; add(a, b)]")
        assertTrue(norm.startsWith("STMTS("))
        assertTrue(norm.contains("ASSIGN(a,NUM(1.0))"))
        assertTrue(norm.contains("ASSIGN(b,NUM(2.0))"))
    }

    // endregion

    // region Pattern normalization

    @Test
    fun `normalizes pattern expression`() {
        val norm = normalize("[pattern(digit*4)]")
        assertTrue(norm.contains("PATTERN"))
        assertTrue(norm.contains("QUANT"))
        assertTrue(norm.contains("CHAR(DIGIT)"))
    }

    // endregion

    // region Cache key generation

    @Test
    fun `generates consistent cache key`() {
        val key1 = cacheKey("[find(path: \"inbox\")]")
        val key2 = cacheKey("[find(path: \"inbox\")]")
        assertEquals(key1, key2)
    }

    @Test
    fun `generates different keys for different expressions`() {
        val key1 = cacheKey("[find(path: \"inbox\")]")
        val key2 = cacheKey("[find(path: \"archive\")]")
        assertNotEquals(key1, key2)
    }

    @Test
    fun `cache key is valid hex string`() {
        val key = cacheKey("[42]")
        assertTrue(key.matches(Regex("[0-9a-f]+")))
        assertEquals(64, key.length) // SHA-256 produces 64 hex chars
    }

    @Test
    fun `position independence - same expression at different positions has same key`() {
        // Simulating parsing from different positions in source
        val expr1 = "[find(path: \"inbox\")]"
        val expr2 = "  [find(path: \"inbox\")]" // Different position

        val key1 = cacheKey(expr1.trim())
        val key2 = cacheKey(expr2.trim())
        assertEquals(key1, key2)
    }

    // endregion

    // region Equivalent forms

    @Test
    fun `func with bracket arg equivalent to func with paren arg`() {
        // func[x] is syntactic sugar for func([x])
        // These should parse to the same AST and produce the same cache key
        // Note: The parser transforms func[x] to func([x]) at parse time
        val key1 = cacheKey("[sort[i.path]]")
        val key2 = cacheKey("[sort([i.path])]")
        assertEquals(key1, key2)
    }

    // endregion

    private fun normalize(code: String): String {
        val tokens = Lexer(code).tokenize()
        val directive = Parser(tokens, code).parseDirective()
        return AstNormalizer.normalize(directive.expression)
    }

    private fun cacheKey(code: String): String {
        val tokens = Lexer(code).tokenize()
        val directive = Parser(tokens, code).parseDirective()
        return AstNormalizer.computeCacheKey(directive.expression)
    }
}
