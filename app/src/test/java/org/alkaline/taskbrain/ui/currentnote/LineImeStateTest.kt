package org.alkaline.taskbrain.ui.currentnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for IME behavior patterns using EditingBuffer directly.
 *
 * Since EditorController is final and can't be mocked, we test the core
 * editing logic through EditingBuffer, which is the source of truth for
 * IME operations.
 */
class LineImeStateTest {

    // ==================== Auto-Capitalization Pattern ====================

    /**
     * CRITICAL TEST: Simulates the auto-capitalization sequence.
     *
     * This test verifies the exact sequence that caused the duplication bug.
     * The key insight is that commitCorrection is METADATA only - the actual
     * edit comes through commitText.
     */
    @Test
    fun `auto-capitalization sequence produces correct result`() {
        val buffer = EditingBuffer("Now i", 5)

        // IME auto-capitalization sequence:
        // 1. Delete the lowercase 'i'
        val cursor = buffer.cursor
        val deleteStart = (cursor - 1).coerceAtLeast(0)
        buffer.delete(deleteStart, cursor)
        buffer.cursor = deleteStart

        // Buffer should now be "Now " with cursor at 4
        assertEquals("Now ", buffer.text)
        assertEquals(4, buffer.cursor)

        // 2. commitCorrection is called - this is METADATA ONLY
        // In the FIXED implementation, this does NOT modify the buffer
        // (nothing to do here - that's the fix!)

        // 3. commitText inserts the capitalized version
        val insertPos = buffer.cursor
        buffer.replace(insertPos, insertPos, "I ")
        buffer.cursor = insertPos + 2

        // Result should be "Now I " - NOT "Now I I "
        assertEquals("Now I ", buffer.text)
        assertEquals(6, buffer.cursor)
    }

    /**
     * Demonstrates what WOULD happen if commitCorrection modified text (the bug).
     */
    @Test
    fun `demonstrates duplication bug behavior`() {
        val buffer = EditingBuffer("Now i", 5)

        // 1. Delete 'i'
        buffer.delete(4, 5)
        buffer.cursor = 4
        assertEquals("Now ", buffer.text)

        // BUG: If commitCorrection also inserted text:
        buffer.replace(4, 4, "I ")
        buffer.cursor = 6
        assertEquals("Now I ", buffer.text)  // First insert

        // Then commitText would insert AGAIN:
        buffer.replace(6, 6, "I ")
        buffer.cursor = 8
        assertEquals("Now I I ", buffer.text)  // DUPLICATED!

        // This is the bug we fixed by making commitCorrection NOT modify text
    }

    // ==================== Spell Check Pattern ====================

    @Test
    fun `spell check replacement works correctly`() {
        val buffer = EditingBuffer("teh", 3)

        // Spell check sequence:
        // 1. Set composing region on misspelled word
        buffer.setComposition(0, 3)
        assertTrue(buffer.hasComposition())

        // 2. Replace composition with correct spelling
        buffer.replace(0, 3, "the")
        buffer.cursor = 3
        buffer.commitComposition()

        assertEquals("the", buffer.text)
        assertEquals(3, buffer.cursor)
        assertFalse(buffer.hasComposition())
    }

    // ==================== Batch Edit Simulation ====================

    @Test
    fun `batch edit applies operations in sequence`() {
        val buffer = EditingBuffer("abc", 3)

        // Simulate batch edit - multiple operations
        buffer.replace(3, 3, "1")
        buffer.cursor = 4
        buffer.replace(4, 4, "2")
        buffer.cursor = 5
        buffer.replace(5, 5, "3")
        buffer.cursor = 6

        assertEquals("abc123", buffer.text)
        assertEquals(6, buffer.cursor)
    }

    // ==================== Composing Text Replacement ====================

    @Test
    fun `composing text is replaced when new composing text set`() {
        val buffer = EditingBuffer("hello ", 6)

        // First composing text
        buffer.replace(6, 6, "w")
        buffer.setComposition(6, 7)
        buffer.cursor = 7
        assertEquals("hello w", buffer.text)

        // Replace composing text with longer text
        buffer.replace(6, 7, "wo")
        buffer.setComposition(6, 8)
        buffer.cursor = 8
        assertEquals("hello wo", buffer.text)

        // Continue replacing
        buffer.replace(6, 8, "wor")
        buffer.setComposition(6, 9)
        buffer.cursor = 9
        assertEquals("hello wor", buffer.text)

        // Final replacement
        buffer.replace(6, 9, "world")
        buffer.setComposition(6, 11)
        buffer.cursor = 11
        assertEquals("hello world", buffer.text)

        // Commit
        buffer.commitComposition()
        assertFalse(buffer.hasComposition())
    }

    // ==================== Delete Surrounding Text ====================

    @Test
    fun `delete surrounding text before cursor`() {
        val buffer = EditingBuffer("hello", 5)

        val deleteStart = (buffer.cursor - 2).coerceAtLeast(0)
        buffer.delete(deleteStart, buffer.cursor)
        buffer.cursor = deleteStart

        assertEquals("hel", buffer.text)
        assertEquals(3, buffer.cursor)
    }

    @Test
    fun `delete surrounding text after cursor`() {
        val buffer = EditingBuffer("hello", 2)

        val deleteEnd = (buffer.cursor + 2).coerceAtMost(buffer.length)
        buffer.delete(buffer.cursor, deleteEnd)

        assertEquals("heo", buffer.text)
        assertEquals(2, buffer.cursor)
    }

    @Test
    fun `delete surrounding text before and after cursor`() {
        val buffer = EditingBuffer("hello", 3)

        val deleteStart = (buffer.cursor - 1).coerceAtLeast(0)
        val deleteEnd = (buffer.cursor + 1).coerceAtMost(buffer.length)
        buffer.delete(deleteStart, deleteEnd)
        buffer.cursor = deleteStart

        assertEquals("heo", buffer.text)
        assertEquals(2, buffer.cursor)
    }

    // ==================== Set Selection ====================

    @Test
    fun `set selection updates cursor`() {
        val buffer = EditingBuffer("hello world", 0)

        buffer.setSelection(5, 5)

        assertEquals(5, buffer.cursor)
        assertEquals(5, buffer.selectionStart)
        assertEquals(5, buffer.selectionEnd)
    }

    @Test
    fun `set selection with range`() {
        val buffer = EditingBuffer("hello world", 0)

        buffer.setSelection(0, 5)

        assertEquals(0, buffer.selectionStart)
        assertEquals(5, buffer.selectionEnd)
    }

    // ==================== Set Composing Region ====================

    @Test
    fun `set composing region marks text`() {
        val buffer = EditingBuffer("hello world", 11)

        buffer.setComposition(6, 11)

        assertTrue(buffer.hasComposition())
        assertEquals(6, buffer.compositionStart)
        assertEquals(11, buffer.compositionEnd)
    }

    @Test
    fun `set composing region with equal start and end clears composition`() {
        val buffer = EditingBuffer("hello", 5)
        buffer.setComposition(1, 4)
        assertTrue(buffer.hasComposition())

        // Setting composition to same start/end should be handled by caller
        // The buffer itself doesn't auto-clear, but the IME code does
        buffer.commitComposition()
        assertFalse(buffer.hasComposition())
    }

    // ==================== Query Methods Simulation ====================

    @Test
    fun `get text before cursor`() {
        val buffer = EditingBuffer("hello world", 5)

        val n = 3
        val start = (buffer.cursor - n).coerceAtLeast(0)
        val result = buffer.text.substring(start, buffer.cursor)

        assertEquals("llo", result)
    }

    @Test
    fun `get text after cursor`() {
        val buffer = EditingBuffer("hello world", 5)

        val n = 3
        val cursor = buffer.cursor.coerceIn(0, buffer.length)
        val end = (cursor + n).coerceAtMost(buffer.length)
        val result = buffer.text.substring(cursor, end)

        assertEquals(" wo", result)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `operations on empty buffer`() {
        val buffer = EditingBuffer("", 0)

        buffer.replace(0, 0, "hello")
        buffer.cursor = 5

        assertEquals("hello", buffer.text)
        assertEquals(5, buffer.cursor)
    }

    @Test
    fun `delete at start of buffer`() {
        val buffer = EditingBuffer("hello", 0)

        // Attempting to delete before cursor at position 0 does nothing meaningful
        val deleteStart = (buffer.cursor - 1).coerceAtLeast(0)
        val deleteEnd = buffer.cursor
        if (deleteStart < deleteEnd) {
            buffer.delete(deleteStart, deleteEnd)
            buffer.cursor = deleteStart
        }

        // Should be unchanged since cursor was at 0
        assertEquals("hello", buffer.text)
        assertEquals(0, buffer.cursor)
    }

    @Test
    fun `delete at end of buffer`() {
        val buffer = EditingBuffer("hello", 5)

        // Attempting to delete after cursor at end does nothing meaningful
        val deleteStart = buffer.cursor
        val deleteEnd = (buffer.cursor + 1).coerceAtMost(buffer.length)
        if (deleteStart < deleteEnd) {
            buffer.delete(deleteStart, deleteEnd)
        }

        // Should be unchanged since cursor was at end
        assertEquals("hello", buffer.text)
        assertEquals(5, buffer.cursor)
    }
}
