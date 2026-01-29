package org.alkaline.taskbrain.dsl.directives

import org.alkaline.taskbrain.data.Note
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ViewContentTracker.
 *
 * Milestone 10.
 */
class ViewContentTrackerTest {

    // region buildFromNotes

    @Test
    fun `buildFromNotes with empty list creates empty tracker`() {
        val tracker = ViewContentTracker.buildFromNotes(emptyList())

        assertTrue(tracker.isEmpty())
        assertEquals(0, tracker.totalLength)
    }

    @Test
    fun `buildFromNotes with single note creates single range`() {
        val note = Note(id = "note1", content = "Hello world")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note))

        assertFalse(tracker.isEmpty())
        val ranges = tracker.getRanges()
        assertEquals(1, ranges.size)

        val range = ranges[0]
        assertEquals(0, range.startOffset)
        assertEquals(11, range.endOffset)  // "Hello world".length
        assertEquals("note1", range.sourceNoteId)
        assertEquals("Hello world", range.originalContent)
    }

    @Test
    fun `buildFromNotes with multiple notes creates ranges with dividers`() {
        val note1 = Note(id = "note1", content = "First")
        val note2 = Note(id = "note2", content = "Second")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note1, note2))

        val ranges = tracker.getRanges()
        assertEquals(2, ranges.size)

        // First note: offset 0 to 5
        assertEquals(0, ranges[0].startOffset)
        assertEquals(5, ranges[0].endOffset)
        assertEquals("note1", ranges[0].sourceNoteId)

        // Second note: after divider "\n---\n" (5 chars)
        // First note ends at 5, divider is 5 chars, so second starts at 10
        assertEquals(10, ranges[1].startOffset)
        assertEquals(16, ranges[1].endOffset)  // "Second".length = 6, so 10+6=16
        assertEquals("note2", ranges[1].sourceNoteId)
    }

    // endregion

    // region mapOffsetToSource

    @Test
    fun `mapOffsetToSource returns null for offset outside all ranges`() {
        val note = Note(id = "note1", content = "Hello")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note))

        // After the content
        assertNull(tracker.mapOffsetToSource(10))

        // Before the content (shouldn't happen but test it)
        assertNull(tracker.mapOffsetToSource(-1))
    }

    @Test
    fun `mapOffsetToSource returns correct range for offset within range`() {
        val note = Note(id = "note1", content = "Hello world")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note))

        val range = tracker.mapOffsetToSource(5)
        assertNotNull(range)
        assertEquals("note1", range!!.sourceNoteId)
    }

    @Test
    fun `mapOffsetToSource handles offset in divider area`() {
        val note1 = Note(id = "note1", content = "First")
        val note2 = Note(id = "note2", content = "Second")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note1, note2))

        // Offset in divider area (5 to 9) should return null
        assertNull(tracker.mapOffsetToSource(7))
    }

    // endregion

    // region ViewRange.contains

    @Test
    fun `ViewRange contains returns true for offset within range`() {
        val range = ViewRange(
            startOffset = 10,
            endOffset = 20,
            sourceNoteId = "note1",
            sourceStartLine = 0,
            originalContent = "content"
        )

        assertTrue(range.contains(10))
        assertTrue(range.contains(15))
        assertTrue(range.contains(19))
    }

    @Test
    fun `ViewRange contains returns false for offset outside range`() {
        val range = ViewRange(
            startOffset = 10,
            endOffset = 20,
            sourceNoteId = "note1",
            sourceStartLine = 0,
            originalContent = "content"
        )

        assertFalse(range.contains(9))
        assertFalse(range.contains(20))  // End is exclusive
        assertFalse(range.contains(25))
    }

    // endregion

    // region updateRangesAfterEdit

    @Test
    fun `updateRangesAfterEdit shifts ranges after edit point`() {
        val note1 = Note(id = "note1", content = "Hello")
        val note2 = Note(id = "note2", content = "World")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note1, note2))

        // Original: "Hello" (0-5), divider (5-10), "World" (10-15)
        // Insert 3 chars at position 2 (within first note)
        tracker.updateRangesAfterEdit(2, 2, 3)

        val ranges = tracker.getRanges()
        // First range should expand
        assertEquals(0, ranges[0].startOffset)
        assertEquals(8, ranges[0].endOffset)  // 5 + 3 = 8

        // Second range should shift
        assertEquals(13, ranges[1].startOffset)  // 10 + 3 = 13
        assertEquals(18, ranges[1].endOffset)    // 15 + 3 = 18
    }

    @Test
    fun `updateRangesAfterEdit handles deletion`() {
        val note = Note(id = "note1", content = "Hello World")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note))

        // Delete 6 chars starting at position 5 ("Hello World" -> "Hello")
        tracker.updateRangesAfterEdit(5, 11, 0)

        val ranges = tracker.getRanges()
        assertEquals(0, ranges[0].startOffset)
        assertEquals(5, ranges[0].endOffset)
    }

    // endregion

    // region getModifiedRanges

    @Test
    fun `getModifiedRanges returns empty list when content unchanged`() {
        val note = Note(id = "note1", content = "Hello")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note))

        val modified = tracker.getModifiedRanges("Hello")
        assertTrue(modified.isEmpty())
    }

    @Test
    fun `getModifiedRanges returns range when content changed within range`() {
        val note = Note(id = "note1", content = "Hello")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note))

        // Modification within the original range (same length)
        val modified = tracker.getModifiedRanges("Hellx")
        assertEquals(1, modified.size)
        assertEquals("note1", modified[0].sourceNoteId)
    }

    @Test
    fun `getModifiedRanges detects content expansion after updateRangesAfterEdit`() {
        val note = Note(id = "note1", content = "Hello")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note))

        // Simulate appending "!" - insert at position 5, original range is 5-5, new length is 1
        tracker.updateRangesAfterEdit(5, 5, 1)

        // Now the range should be 0-6, and "Hello!" matches the extended range
        val ranges = tracker.getRanges()
        assertEquals(6, ranges[0].endOffset)

        // But original content is still "Hello", so it's detected as modified
        val modified = tracker.getModifiedRanges("Hello!")
        assertEquals(1, modified.size)
    }

    // endregion

    // region findRangesInSelection

    @Test
    fun `findRangesInSelection returns ranges that overlap selection`() {
        val note1 = Note(id = "note1", content = "First")
        val note2 = Note(id = "note2", content = "Second")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note1, note2))

        // Selection that spans both notes
        val ranges = tracker.findRangesInSelection(3, 12)
        assertEquals(2, ranges.size)
    }

    @Test
    fun `findRangesInSelection returns only overlapping ranges`() {
        val note1 = Note(id = "note1", content = "First")
        val note2 = Note(id = "note2", content = "Second")
        val tracker = ViewContentTracker.buildFromNotes(listOf(note1, note2))

        // Selection only in first note
        val ranges = tracker.findRangesInSelection(0, 3)
        assertEquals(1, ranges.size)
        assertEquals("note1", ranges[0].sourceNoteId)
    }

    // endregion
}
