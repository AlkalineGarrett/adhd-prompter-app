package org.alkaline.taskbrain.ui.currentnote

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorGestureHandlerTest {

    // ==================== findWordBoundaries ====================

    @Test
    fun `findWordBoundaries finds word at start`() {
        val (start, end) = findWordBoundaries("hello world", 0)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries finds word at middle of word`() {
        val (start, end) = findWordBoundaries("hello world", 2)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries finds word at end of word`() {
        val (start, end) = findWordBoundaries("hello world", 5)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries finds second word`() {
        val (start, end) = findWordBoundaries("hello world", 8)
        assertEquals(6, start)
        assertEquals(11, end)
    }

    @Test
    fun `findWordBoundaries at space returns space boundaries`() {
        val (start, end) = findWordBoundaries("hello world", 5)
        // At position 5 (end of "hello"), should find "hello"
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries handles empty string`() {
        val (start, end) = findWordBoundaries("", 0)
        assertEquals(0, start)
        assertEquals(0, end)
    }

    @Test
    fun `findWordBoundaries clamps offset beyond length`() {
        val (start, end) = findWordBoundaries("hello", 10)
        // Offset clamped to 5 (end of text), finds "hello"
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries clamps negative offset`() {
        val (start, end) = findWordBoundaries("hello", -1)
        // Offset clamped to 0, finds "hello"
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries handles single word`() {
        val (start, end) = findWordBoundaries("hello", 2)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries handles multiple spaces`() {
        val (start, end) = findWordBoundaries("hello   world", 8)
        assertEquals(8, start)
        assertEquals(13, end)
    }

    @Test
    fun `findWordBoundaries handles tabs as whitespace`() {
        val (start, end) = findWordBoundaries("hello\tworld", 8)
        assertEquals(6, start)
        assertEquals(11, end)
    }

    @Test
    fun `findWordBoundaries handles newlines as whitespace`() {
        val (start, end) = findWordBoundaries("hello\nworld", 8)
        assertEquals(6, start)
        assertEquals(11, end)
    }

    @Test
    fun `findWordBoundaries excludes punctuation from word`() {
        // Standard behavior: punctuation is not part of word
        val (start, end) = findWordBoundaries("hello, world", 3)
        assertEquals(0, start)
        assertEquals(5, end) // "hello" only, comma excluded
    }

    @Test
    fun `findWordBoundaries at punctuation finds adjacent word`() {
        // At the comma position, should find the previous word
        val (start, end) = findWordBoundaries("hello, world", 5)
        assertEquals(0, start)
        assertEquals(5, end) // "hello" - cursor at end of word
    }

    @Test
    fun `findWordBoundaries with punctuation between words`() {
        val (start, end) = findWordBoundaries("hello,world", 5)
        // At comma, cursor is between words - finds "hello" (previous word char)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries selects word after punctuation`() {
        val (start, end) = findWordBoundaries("hello,world", 6)
        // At 'w' in "world"
        assertEquals(6, start)
        assertEquals(11, end)
    }

    @Test
    fun `findWordBoundaries at beginning of second word`() {
        val (start, end) = findWordBoundaries("hello world", 6)
        assertEquals(6, start)
        assertEquals(11, end)
    }

    @Test
    fun `findWordBoundaries at end of text`() {
        val (start, end) = findWordBoundaries("hello world", 11)
        assertEquals(6, start)
        assertEquals(11, end)
    }

    // ==================== findLineIndexAtY ====================

    @Test
    fun `findLineIndexAtY finds first line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null),
            LineLayoutInfo(2, 100f, 50f, null)
        )
        assertEquals(0, findLineIndexAtY(25f, layouts, 2))
    }

    @Test
    fun `findLineIndexAtY finds middle line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null),
            LineLayoutInfo(2, 100f, 50f, null)
        )
        assertEquals(1, findLineIndexAtY(75f, layouts, 2))
    }

    @Test
    fun `findLineIndexAtY finds last line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null),
            LineLayoutInfo(2, 100f, 50f, null)
        )
        assertEquals(2, findLineIndexAtY(125f, layouts, 2))
    }

    @Test
    fun `findLineIndexAtY at line boundary returns upper line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null)
        )
        // At exactly 50f (start of second line)
        assertEquals(1, findLineIndexAtY(50f, layouts, 1))
    }

    @Test
    fun `findLineIndexAtY returns 0 for negative maxLineIndex`() {
        val layouts = listOf(LineLayoutInfo(0, 0f, 50f, null))
        assertEquals(0, findLineIndexAtY(25f, layouts, -1))
    }

    @Test
    fun `findLineIndexAtY clamps to maxLineIndex`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null),
            LineLayoutInfo(2, 100f, 50f, null)
        )
        // Position in line 2, but maxLineIndex is 1
        assertEquals(1, findLineIndexAtY(125f, layouts, 1))
    }

    @Test
    fun `findLineIndexAtY handles empty layouts with default height`() {
        val layouts = emptyList<LineLayoutInfo>()
        // With default height of 50, y=75 should estimate line 1
        assertEquals(1, findLineIndexAtY(75f, layouts, 5, 50f))
    }

    @Test
    fun `findLineIndexAtY handles layouts with zero height`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 0f, null), // Zero height
            LineLayoutInfo(1, 50f, 50f, null)
        )
        // Should skip zero-height line and find line 1
        assertEquals(1, findLineIndexAtY(75f, layouts, 1))
    }

    @Test
    fun `findLineIndexAtY beyond all lines returns last line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null)
        )
        // Position way beyond all lines
        assertEquals(1, findLineIndexAtY(500f, layouts, 1))
    }

    @Test
    fun `findLineIndexAtY at y zero returns first line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null)
        )
        assertEquals(0, findLineIndexAtY(0f, layouts, 1))
    }

    @Test
    fun `findLineIndexAtY with varying line heights`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 30f, null),    // 0-30
            LineLayoutInfo(1, 30f, 100f, null),  // 30-130 (tall line)
            LineLayoutInfo(2, 130f, 30f, null)   // 130-160
        )
        assertEquals(0, findLineIndexAtY(15f, layouts, 2))
        assertEquals(1, findLineIndexAtY(80f, layouts, 2))
        assertEquals(2, findLineIndexAtY(145f, layouts, 2))
    }
}
