package org.alkaline.taskbrain.dsl.directives

import org.alkaline.taskbrain.dsl.runtime.NumberVal
import org.alkaline.taskbrain.dsl.runtime.StringVal
import org.junit.Assert.*
import org.junit.Test

class DirectiveFinderTest {

    // region Finding directives

    @Test
    fun `finds single directive in content`() {
        val content = "Some text [42] more text"
        val directives = DirectiveFinder.findDirectives(content)

        assertEquals(1, directives.size)
        assertEquals("[42]", directives[0].sourceText)
        assertEquals(10, directives[0].startOffset)
        assertEquals(14, directives[0].endOffset)
    }

    @Test
    fun `finds multiple directives in content`() {
        val content = "[1] and [2] and [3]"
        val directives = DirectiveFinder.findDirectives(content)

        assertEquals(3, directives.size)
        assertEquals("[1]", directives[0].sourceText)
        assertEquals("[2]", directives[1].sourceText)
        assertEquals("[3]", directives[2].sourceText)
    }

    @Test
    fun `finds string directive`() {
        val content = "Result: [\"hello\"]"
        val directives = DirectiveFinder.findDirectives(content)

        assertEquals(1, directives.size)
        assertEquals("[\"hello\"]", directives[0].sourceText)
    }

    @Test
    fun `returns empty list for no directives`() {
        val content = "No directives here"
        val directives = DirectiveFinder.findDirectives(content)

        assertTrue(directives.isEmpty())
    }

    @Test
    fun `containsDirectives returns true when directives exist`() {
        assertTrue(DirectiveFinder.containsDirectives("Has [42] directive"))
    }

    @Test
    fun `containsDirectives returns false when no directives`() {
        assertFalse(DirectiveFinder.containsDirectives("No directives"))
    }

    @Test
    fun `finds directive at start of content`() {
        val content = "[42] at start"
        val directives = DirectiveFinder.findDirectives(content)

        assertEquals(1, directives.size)
        assertEquals(0, directives[0].startOffset)
    }

    @Test
    fun `finds directive at end of content`() {
        val content = "at end [42]"
        val directives = DirectiveFinder.findDirectives(content)

        assertEquals(1, directives.size)
        assertEquals(7, directives[0].startOffset)
        assertEquals(11, directives[0].endOffset)
    }

    @Test
    fun `handles empty content`() {
        val directives = DirectiveFinder.findDirectives("")
        assertTrue(directives.isEmpty())
    }

    // endregion

    // region Executing directives

    @Test
    fun `executes number directive successfully`() {
        val result = DirectiveFinder.executeDirective("[42]")

        assertNull(result.error)
        assertNotNull(result.result)
        assertEquals(42.0, (result.toValue() as NumberVal).value, 0.0)
    }

    @Test
    fun `executes string directive successfully`() {
        val result = DirectiveFinder.executeDirective("[\"hello\"]")

        assertNull(result.error)
        assertEquals("hello", (result.toValue() as StringVal).value)
    }

    @Test
    fun `returns error for invalid directive`() {
        val result = DirectiveFinder.executeDirective("[invalid@syntax]")

        assertNotNull(result.error)
        assertTrue(result.error!!.contains("error"))
    }

    @Test
    fun `returns error for empty directive`() {
        val result = DirectiveFinder.executeDirective("[]")

        assertNotNull(result.error)
    }

    @Test
    fun `returns error for unclosed string`() {
        val result = DirectiveFinder.executeDirective("[\"unclosed]")

        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Lexer error"))
    }

    // endregion

    // region Execute all directives

    @Test
    fun `executeAllDirectives returns results for all directives`() {
        val content = "First [42] second [\"hello\"]"
        val results = DirectiveFinder.executeAllDirectives(content, 0)

        assertEquals(2, results.size)
    }

    @Test
    fun `executeAllDirectives handles mixed success and error`() {
        val content = "[42] and [invalid@]"
        val results = DirectiveFinder.executeAllDirectives(content, 0)

        assertEquals(2, results.size)

        // Find the successful one
        val successResult = results.values.find { it.error == null }
        assertNotNull(successResult)
        assertEquals(42.0, (successResult!!.toValue() as NumberVal).value, 0.0)

        // Find the failed one
        val errorResult = results.values.find { it.error != null }
        assertNotNull(errorResult)
    }

    @Test
    fun `executeAllDirectives returns empty map for no directives`() {
        val results = DirectiveFinder.executeAllDirectives("No directives here", 0)
        assertTrue(results.isEmpty())
    }

    // endregion

    // region Directive hashing

    @Test
    fun `same directive text produces same hash`() {
        val content = "[42] and [42]"
        val directives = DirectiveFinder.findDirectives(content)

        assertEquals(2, directives.size)
        assertEquals(directives[0].hash(), directives[1].hash())
    }

    @Test
    fun `different directive text produces different hash`() {
        val content = "[42] and [43]"
        val directives = DirectiveFinder.findDirectives(content)

        assertEquals(2, directives.size)
        assertNotEquals(directives[0].hash(), directives[1].hash())
    }

    // endregion
}
