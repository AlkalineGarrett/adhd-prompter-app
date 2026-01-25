package org.alkaline.taskbrain.dsl

import org.junit.Assert.*
import org.junit.Test

class DirectiveInstanceTest {

    // region DirectiveInstance.create tests

    @Test
    fun `create generates unique UUIDs`() {
        val instance1 = DirectiveInstance.create(0, 0, "[datetime]")
        val instance2 = DirectiveInstance.create(0, 0, "[datetime]")

        assertNotEquals(instance1.uuid, instance2.uuid)
    }

    @Test
    fun `create preserves position and source text`() {
        val instance = DirectiveInstance.create(5, 10, "[date]")

        assertEquals(5, instance.lineIndex)
        assertEquals(10, instance.startOffset)
        assertEquals("[date]", instance.sourceText)
    }

    // endregion

    // region matchDirectiveInstances tests

    @Test
    fun `matchDirectiveInstances returns new UUIDs for empty existing list`() {
        val newDirectives = listOf(
            ParsedDirectiveLocation(0, 0, "[datetime]"),
            ParsedDirectiveLocation(1, 5, "[date]")
        )

        val result = matchDirectiveInstances(emptyList(), newDirectives)

        assertEquals(2, result.size)
        // All should have new UUIDs (no existing to match)
        assertNotEquals(result[0].uuid, result[1].uuid)
        assertEquals("[datetime]", result[0].sourceText)
        assertEquals("[date]", result[1].sourceText)
    }

    @Test
    fun `matchDirectiveInstances returns empty for no new directives`() {
        val existing = listOf(DirectiveInstance.create(0, 0, "[datetime]"))

        val result = matchDirectiveInstances(existing, emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `matchDirectiveInstances preserves UUID on exact match`() {
        val existing = listOf(DirectiveInstance.create(0, 5, "[datetime]"))
        val newDirectives = listOf(ParsedDirectiveLocation(0, 5, "[datetime]"))

        val result = matchDirectiveInstances(existing, newDirectives)

        assertEquals(1, result.size)
        assertEquals(existing[0].uuid, result[0].uuid)
    }

    @Test
    fun `matchDirectiveInstances preserves UUID when same line text shifts`() {
        val existing = listOf(DirectiveInstance.create(0, 5, "[datetime]"))
        // Same line, same text, different offset (text was inserted before it)
        val newDirectives = listOf(ParsedDirectiveLocation(0, 10, "[datetime]"))

        val result = matchDirectiveInstances(existing, newDirectives)

        assertEquals(1, result.size)
        assertEquals(existing[0].uuid, result[0].uuid)
        assertEquals(10, result[0].startOffset) // Updated position
    }

    @Test
    fun `matchDirectiveInstances preserves UUID when line moves with unique match`() {
        val existing = listOf(DirectiveInstance.create(2, 0, "[datetime]"))
        // Same text, different line (line was moved)
        val newDirectives = listOf(ParsedDirectiveLocation(5, 0, "[datetime]"))

        val result = matchDirectiveInstances(existing, newDirectives)

        assertEquals(1, result.size)
        assertEquals(existing[0].uuid, result[0].uuid)
        assertEquals(5, result[0].lineIndex) // Updated line
    }

    @Test
    fun `matchDirectiveInstances generates new UUID when no match`() {
        val existing = listOf(DirectiveInstance.create(0, 0, "[datetime]"))
        // Completely different directive
        val newDirectives = listOf(ParsedDirectiveLocation(0, 0, "[date]"))

        val result = matchDirectiveInstances(existing, newDirectives)

        assertEquals(1, result.size)
        assertNotEquals(existing[0].uuid, result[0].uuid) // New UUID
        assertEquals("[date]", result[0].sourceText)
    }

    @Test
    fun `matchDirectiveInstances handles multiple directives with same text`() {
        val existing = listOf(
            DirectiveInstance.create(0, 0, "[datetime]"),
            DirectiveInstance.create(1, 0, "[datetime]")
        )
        // Same text on same lines - should match by line
        val newDirectives = listOf(
            ParsedDirectiveLocation(0, 0, "[datetime]"),
            ParsedDirectiveLocation(1, 0, "[datetime]")
        )

        val result = matchDirectiveInstances(existing, newDirectives)

        assertEquals(2, result.size)
        // Line 0 directive should keep its UUID
        assertEquals(existing[0].uuid, result.find { it.lineIndex == 0 }?.uuid)
        // Line 1 directive should keep its UUID
        assertEquals(existing[1].uuid, result.find { it.lineIndex == 1 }?.uuid)
    }

    @Test
    fun `matchDirectiveInstances avoids ambiguous line move matches`() {
        val existing = listOf(
            DirectiveInstance.create(0, 0, "[datetime]"),
            DirectiveInstance.create(1, 0, "[datetime]")
        )
        // Both existing have same text, only one new directive - should NOT match (ambiguous)
        val newDirectives = listOf(ParsedDirectiveLocation(5, 0, "[datetime]"))

        val result = matchDirectiveInstances(existing, newDirectives)

        assertEquals(1, result.size)
        // Should be a new UUID because match is ambiguous (2 candidates)
        assertNotEquals(existing[0].uuid, result[0].uuid)
        assertNotEquals(existing[1].uuid, result[0].uuid)
    }

    @Test
    fun `matchDirectiveInstances handles mix of new and existing`() {
        val existing = listOf(DirectiveInstance.create(0, 0, "[datetime]"))
        val newDirectives = listOf(
            ParsedDirectiveLocation(0, 0, "[datetime]"),  // Should match existing
            ParsedDirectiveLocation(0, 10, "[date]") // Should get new UUID
        )

        val result = matchDirectiveInstances(existing, newDirectives)

        assertEquals(2, result.size)
        val nowResult = result.find { it.sourceText == "[datetime]" }
        val dateResult = result.find { it.sourceText == "[date]" }

        assertEquals(existing[0].uuid, nowResult?.uuid)
        assertNotNull(dateResult)
        assertNotEquals(existing[0].uuid, dateResult?.uuid)
    }

    // endregion

    // region parseAllDirectiveLocations tests

    @Test
    fun `parseAllDirectiveLocations finds all directives`() {
        val content = """
            Line with [datetime]
            Another [date] and [time]
            No directives here
        """.trimIndent()

        val locations = parseAllDirectiveLocations(content)

        assertEquals(3, locations.size)
        assertEquals(ParsedDirectiveLocation(0, 10, "[datetime]"), locations[0])
        assertEquals(ParsedDirectiveLocation(1, 8, "[date]"), locations[1])
        assertEquals(ParsedDirectiveLocation(1, 19, "[time]"), locations[2])
    }

    @Test
    fun `parseAllDirectiveLocations returns empty for no directives`() {
        val content = "No directives here\nJust plain text"

        val locations = parseAllDirectiveLocations(content)

        assertTrue(locations.isEmpty())
    }

    // endregion
}
