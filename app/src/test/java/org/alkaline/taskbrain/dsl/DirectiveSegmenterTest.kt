package org.alkaline.taskbrain.dsl

import org.junit.Assert.*
import org.junit.Test

class DirectiveSegmenterTest {

    // region segmentLine tests

    @Test
    fun `segmentLine returns empty list for empty content`() {
        val segments = DirectiveSegmenter.segmentLine("", emptyMap())
        assertTrue(segments.isEmpty())
    }

    @Test
    fun `segmentLine returns single text segment for content without directives`() {
        val segments = DirectiveSegmenter.segmentLine("Hello world", emptyMap())

        assertEquals(1, segments.size)
        val segment = segments[0] as DirectiveSegment.Text
        assertEquals("Hello world", segment.content)
        assertEquals(0..10, segment.range)
    }

    @Test
    fun `segmentLine returns directive segment for single directive`() {
        val content = "[42]"
        val results = DirectiveFinder.executeAllDirectives(content)
        val segments = DirectiveSegmenter.segmentLine(content, results)

        assertEquals(1, segments.size)
        val segment = segments[0] as DirectiveSegment.Directive
        assertEquals("[42]", segment.sourceText)
        assertTrue(segment.isComputed)
        assertEquals("42", segment.displayText)
    }

    @Test
    fun `segmentLine handles text before and after directive`() {
        val content = "Hello [42] world"
        val results = DirectiveFinder.executeAllDirectives(content)
        val segments = DirectiveSegmenter.segmentLine(content, results)

        assertEquals(3, segments.size)

        val text1 = segments[0] as DirectiveSegment.Text
        assertEquals("Hello ", text1.content)

        val directive = segments[1] as DirectiveSegment.Directive
        assertEquals("[42]", directive.sourceText)
        assertEquals("42", directive.displayText)

        val text2 = segments[2] as DirectiveSegment.Text
        assertEquals(" world", text2.content)
    }

    @Test
    fun `segmentLine handles multiple directives`() {
        val content = "[1] and [2] and [3]"
        val results = DirectiveFinder.executeAllDirectives(content)
        val segments = DirectiveSegmenter.segmentLine(content, results)

        assertEquals(5, segments.size)

        val d1 = segments[0] as DirectiveSegment.Directive
        assertEquals("[1]", d1.sourceText)

        val t1 = segments[1] as DirectiveSegment.Text
        assertEquals(" and ", t1.content)

        val d2 = segments[2] as DirectiveSegment.Directive
        assertEquals("[2]", d2.sourceText)

        val t2 = segments[3] as DirectiveSegment.Text
        assertEquals(" and ", t2.content)

        val d3 = segments[4] as DirectiveSegment.Directive
        assertEquals("[3]", d3.sourceText)
    }

    @Test
    fun `segmentLine shows source text when no result available`() {
        val content = "[42]"
        // No results provided
        val segments = DirectiveSegmenter.segmentLine(content, emptyMap())

        assertEquals(1, segments.size)
        val segment = segments[0] as DirectiveSegment.Directive
        assertEquals("[42]", segment.sourceText)
        assertFalse(segment.isComputed)
        assertEquals("[42]", segment.displayText) // Source text when not computed
    }

    // endregion

    // region hasDirectives tests

    @Test
    fun `hasDirectives returns false for content without directives`() {
        assertFalse(DirectiveSegmenter.hasDirectives("Hello world"))
        assertFalse(DirectiveSegmenter.hasDirectives(""))
        assertFalse(DirectiveSegmenter.hasDirectives("No brackets here"))
    }

    @Test
    fun `hasDirectives returns true for content with directives`() {
        assertTrue(DirectiveSegmenter.hasDirectives("[42]"))
        assertTrue(DirectiveSegmenter.hasDirectives("Hello [42] world"))
        assertTrue(DirectiveSegmenter.hasDirectives("[\"string\"]"))
    }

    // endregion

    // region hasComputedDirectives tests

    @Test
    fun `hasComputedDirectives returns false for content without directives`() {
        assertFalse(DirectiveSegmenter.hasComputedDirectives("Hello world", emptyMap()))
    }

    @Test
    fun `hasComputedDirectives returns false when no results available`() {
        assertFalse(DirectiveSegmenter.hasComputedDirectives("[42]", emptyMap()))
    }

    @Test
    fun `hasComputedDirectives returns true when result is available`() {
        val content = "[42]"
        val results = DirectiveFinder.executeAllDirectives(content)
        assertTrue(DirectiveSegmenter.hasComputedDirectives(content, results))
    }

    @Test
    fun `hasComputedDirectives returns false when result has error`() {
        val content = "[42]"
        val hash = DirectiveResult.hashDirective("[42]")
        val errorResult = mapOf(hash to DirectiveResult.failure("Error"))
        assertFalse(DirectiveSegmenter.hasComputedDirectives(content, errorResult))
    }

    // endregion

    // region buildDisplayText tests

    @Test
    fun `buildDisplayText returns empty for empty content`() {
        val result = DirectiveSegmenter.buildDisplayText("", emptyMap())
        assertEquals("", result.displayText)
        assertTrue(result.segments.isEmpty())
        assertTrue(result.directiveDisplayRanges.isEmpty())
    }

    @Test
    fun `buildDisplayText returns original text for content without directives`() {
        val result = DirectiveSegmenter.buildDisplayText("Hello world", emptyMap())
        assertEquals("Hello world", result.displayText)
        assertTrue(result.directiveDisplayRanges.isEmpty())
    }

    @Test
    fun `buildDisplayText replaces directive with result`() {
        val content = "[42]"
        val results = DirectiveFinder.executeAllDirectives(content)
        val result = DirectiveSegmenter.buildDisplayText(content, results)

        assertEquals("42", result.displayText)
        assertEquals(1, result.directiveDisplayRanges.size)

        val range = result.directiveDisplayRanges[0]
        assertEquals(0..3, range.sourceRange)
        assertEquals(0..1, range.displayRange)
        assertEquals("[42]", range.sourceText)
        assertEquals("42", range.displayText)
        assertTrue(range.isComputed)
        assertFalse(range.hasError)
    }

    @Test
    fun `buildDisplayText handles mixed content`() {
        val content = "Hello [42] world"
        val results = DirectiveFinder.executeAllDirectives(content)
        val result = DirectiveSegmenter.buildDisplayText(content, results)

        // "[42]" (4 chars) replaced with "42" (2 chars)
        assertEquals("Hello 42 world", result.displayText)
        assertEquals(1, result.directiveDisplayRanges.size)

        val range = result.directiveDisplayRanges[0]
        assertEquals(6..9, range.sourceRange)  // "[42]" in original
        assertEquals(6..7, range.displayRange) // "42" in display
    }

    @Test
    fun `buildDisplayText handles uncomputed directive`() {
        val content = "[42]"
        val result = DirectiveSegmenter.buildDisplayText(content, emptyMap())

        // Should show source text when not computed
        assertEquals("[42]", result.displayText)
        assertEquals(1, result.directiveDisplayRanges.size)
        assertFalse(result.directiveDisplayRanges[0].isComputed)
    }

    @Test
    fun `buildDisplayText handles string directive`() {
        val content = "[\"hello\"]"
        val results = DirectiveFinder.executeAllDirectives(content)
        val result = DirectiveSegmenter.buildDisplayText(content, results)

        assertEquals("hello", result.displayText)
    }

    @Test
    fun `buildDisplayText handles multiple directives with different lengths`() {
        val content = "[100] [200]"
        val results = DirectiveFinder.executeAllDirectives(content)
        val result = DirectiveSegmenter.buildDisplayText(content, results)

        assertEquals("100 200", result.displayText)
        assertEquals(2, result.directiveDisplayRanges.size)

        // First directive
        val range1 = result.directiveDisplayRanges[0]
        assertEquals("[100]", range1.sourceText)
        assertEquals("100", range1.displayText)
        assertEquals(0..2, range1.displayRange)

        // Second directive
        val range2 = result.directiveDisplayRanges[1]
        assertEquals("[200]", range2.sourceText)
        assertEquals("200", range2.displayText)
        assertEquals(4..6, range2.displayRange)
    }

    // endregion
}
