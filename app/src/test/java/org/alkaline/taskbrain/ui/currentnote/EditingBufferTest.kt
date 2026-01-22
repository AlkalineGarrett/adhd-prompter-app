package org.alkaline.taskbrain.ui.currentnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for EditingBuffer - the core text buffer used by IME operations.
 */
class EditingBufferTest {

    private lateinit var buffer: EditingBuffer

    @Before
    fun setUp() {
        buffer = EditingBuffer()
    }

    // ==================== Initialization ====================

    @Test
    fun `empty buffer has correct initial state`() {
        assertEquals("", buffer.text)
        assertEquals(0, buffer.length)
        assertEquals(0, buffer.cursor)
        assertEquals(0, buffer.selectionStart)
        assertEquals(0, buffer.selectionEnd)
        assertFalse(buffer.hasComposition())
    }

    @Test
    fun `buffer initialized with text has correct state`() {
        buffer = EditingBuffer("hello", 3)
        assertEquals("hello", buffer.text)
        assertEquals(5, buffer.length)
        assertEquals(3, buffer.cursor)
    }

    @Test
    fun `buffer clamps cursor to valid range on init`() {
        buffer = EditingBuffer("hi", 100)
        assertEquals(2, buffer.cursor) // clamped to length
    }

    @Test
    fun `buffer clamps negative cursor on init`() {
        buffer = EditingBuffer("hi", -5)
        assertEquals(0, buffer.cursor)
    }

    // ==================== Cursor Operations ====================

    @Test
    fun `setting cursor updates selection`() {
        buffer = EditingBuffer("hello", 0)
        buffer.cursor = 3
        assertEquals(3, buffer.cursor)
        assertEquals(3, buffer.selectionStart)
        assertEquals(3, buffer.selectionEnd)
    }

    @Test
    fun `cursor is clamped to buffer length`() {
        buffer = EditingBuffer("hi", 0)
        buffer.cursor = 100
        assertEquals(2, buffer.cursor)
    }

    @Test
    fun `cursor is clamped to zero`() {
        buffer = EditingBuffer("hi", 1)
        buffer.cursor = -10
        assertEquals(0, buffer.cursor)
    }

    // ==================== Selection Operations ====================

    @Test
    fun `setSelection sets both start and end`() {
        buffer = EditingBuffer("hello world", 0)
        buffer.setSelection(2, 7)
        assertEquals(2, buffer.selectionStart)
        assertEquals(7, buffer.selectionEnd)
    }

    @Test
    fun `setSelection clamps to valid range`() {
        buffer = EditingBuffer("hi", 0)
        buffer.setSelection(-5, 100)
        assertEquals(0, buffer.selectionStart)
        assertEquals(2, buffer.selectionEnd)
    }

    // ==================== Composition Operations ====================

    @Test
    fun `setComposition sets composing region`() {
        buffer = EditingBuffer("hello", 0)
        buffer.setComposition(1, 4)
        assertTrue(buffer.hasComposition())
        assertEquals(1, buffer.compositionStart)
        assertEquals(4, buffer.compositionEnd)
    }

    @Test
    fun `commitComposition clears composing region`() {
        buffer = EditingBuffer("hello", 0)
        buffer.setComposition(1, 4)
        assertTrue(buffer.hasComposition())

        buffer.commitComposition()
        assertFalse(buffer.hasComposition())
        assertEquals(-1, buffer.compositionStart)
        assertEquals(-1, buffer.compositionEnd)
    }

    @Test
    fun `hasComposition returns false when no composition`() {
        buffer = EditingBuffer("hello", 0)
        assertFalse(buffer.hasComposition())
    }

    // ==================== Replace Operations ====================

    @Test
    fun `replace inserts text at position`() {
        buffer = EditingBuffer("hello", 0)
        buffer.replace(2, 2, "XX")
        assertEquals("heXXllo", buffer.text)
    }

    @Test
    fun `replace replaces text in range`() {
        buffer = EditingBuffer("hello", 0)
        buffer.replace(1, 4, "X")
        assertEquals("hXo", buffer.text)
    }

    @Test
    fun `replace at start of buffer`() {
        buffer = EditingBuffer("hello", 0)
        buffer.replace(0, 0, "XX")
        assertEquals("XXhello", buffer.text)
    }

    @Test
    fun `replace at end of buffer`() {
        buffer = EditingBuffer("hello", 0)
        buffer.replace(5, 5, " world")
        assertEquals("hello world", buffer.text)
    }

    @Test
    fun `replace entire buffer`() {
        buffer = EditingBuffer("hello", 0)
        buffer.replace(0, 5, "goodbye")
        assertEquals("goodbye", buffer.text)
    }

    @Test
    fun `replace shifts composition after replacement`() {
        buffer = EditingBuffer("hello world", 0)
        buffer.setComposition(6, 11) // "world"

        buffer.replace(0, 0, "XX") // insert at start
        assertEquals("XXhello world", buffer.text)
        assertEquals(8, buffer.compositionStart) // shifted by 2
        assertEquals(13, buffer.compositionEnd)
    }

    @Test
    fun `replace clears composition when overlapping`() {
        buffer = EditingBuffer("hello world", 0)
        buffer.setComposition(3, 8) // "lo wo"

        buffer.replace(4, 6, "XX") // overlaps composition
        assertFalse(buffer.hasComposition())
    }

    @Test
    fun `replace does not affect composition before replacement`() {
        buffer = EditingBuffer("hello world", 0)
        buffer.setComposition(0, 2) // "he"

        buffer.replace(6, 11, "X") // replace "world"
        assertTrue(buffer.hasComposition())
        assertEquals(0, buffer.compositionStart)
        assertEquals(2, buffer.compositionEnd)
    }

    // ==================== Delete Operations ====================

    @Test
    fun `delete removes text`() {
        buffer = EditingBuffer("hello", 0)
        buffer.delete(1, 4)
        assertEquals("ho", buffer.text)
    }

    @Test
    fun `delete at start`() {
        buffer = EditingBuffer("hello", 0)
        buffer.delete(0, 2)
        assertEquals("llo", buffer.text)
    }

    @Test
    fun `delete at end`() {
        buffer = EditingBuffer("hello", 0)
        buffer.delete(3, 5)
        assertEquals("hel", buffer.text)
    }

    // ==================== Reset Operations ====================

    @Test
    fun `reset clears buffer and sets new content`() {
        buffer = EditingBuffer("old content", 5)
        buffer.setComposition(2, 6)

        buffer.reset("new", 2)

        assertEquals("new", buffer.text)
        assertEquals(2, buffer.cursor)
        assertFalse(buffer.hasComposition())
    }

    @Test
    fun `reset clamps cursor to new content length`() {
        buffer = EditingBuffer("long content", 10)
        buffer.reset("hi", 100)
        assertEquals(2, buffer.cursor) // clamped
    }

    // ==================== Snapshot ====================

    @Test
    fun `snapshot captures current state`() {
        buffer = EditingBuffer("hello", 3)
        buffer.setComposition(1, 4)

        val snapshot = buffer.snapshot()

        assertEquals("hello", snapshot.text)
        assertEquals(3, snapshot.cursor)
        assertEquals(1, snapshot.compositionStart)
        assertEquals(4, snapshot.compositionEnd)
    }

    // ==================== toString ====================

    @Test
    fun `toString provides readable state`() {
        buffer = EditingBuffer("hi", 1)
        buffer.setComposition(0, 2)
        val str = buffer.toString()
        assertTrue(str.contains("hi"))
        assertTrue(str.contains("cursor=1"))
        assertTrue(str.contains("comp=0..2"))
    }
}
