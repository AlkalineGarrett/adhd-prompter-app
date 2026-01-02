package org.alkaline.taskbrain.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NoteLineTrackerTest {

    private lateinit var tracker: NoteLineTracker
    private val parentNoteId = "parent_123"

    @Before
    fun setUp() {
        tracker = NoteLineTracker(parentNoteId)
    }

    @Test
    fun `initial tracked lines should be empty`() {
        assertEquals(0, tracker.getTrackedLines().size)
    }

    @Test
    fun `updateTrackedLines with empty content should create empty line with parent ID`() {
        tracker.updateTrackedLines("")
        
        val lines = tracker.getTrackedLines()
        assertEquals(1, lines.size)
        assertEquals("", lines[0].content)
        assertEquals(parentNoteId, lines[0].noteId)
    }

    @Test
    fun `updateTrackedLines with single line should assign parent ID`() {
        tracker.updateTrackedLines("First line")
        
        val lines = tracker.getTrackedLines()
        assertEquals(1, lines.size)
        assertEquals("First line", lines[0].content)
        assertEquals(parentNoteId, lines[0].noteId)
    }

    @Test
    fun `updateTrackedLines with multiple lines should assign parent ID to first line only`() {
        tracker.updateTrackedLines("Line 1\nLine 2\nLine 3")
        
        val lines = tracker.getTrackedLines()
        assertEquals(3, lines.size)
        assertEquals(parentNoteId, lines[0].noteId)
        assertNull(lines[1].noteId)
        assertNull(lines[2].noteId)
    }

    @Test
    fun `exact match should preserve note IDs`() {
        // Set initial tracked lines
        val initialLines = listOf(
            NoteLine("Line 1", parentNoteId),
            NoteLine("Line 2", "child_1"),
            NoteLine("Line 3", "child_2")
        )
        tracker.setTrackedLines(initialLines)
        
        // Update with same content
        tracker.updateTrackedLines("Line 1\nLine 2\nLine 3")
        
        val lines = tracker.getTrackedLines()
        assertEquals(3, lines.size)
        assertEquals(parentNoteId, lines[0].noteId)
        assertEquals("child_1", lines[1].noteId)
        assertEquals("child_2", lines[2].noteId)
    }

    @Test
    fun `insertion should create new lines without IDs`() {
        val initialLines = listOf(
            NoteLine("Line 1", parentNoteId),
            NoteLine("Line 3", "child_1")
        )
        tracker.setTrackedLines(initialLines)
        
        // Insert a line between Line 1 and Line 3
        tracker.updateTrackedLines("Line 1\nLine 2\nLine 3")
        
        val lines = tracker.getTrackedLines()
        assertEquals(3, lines.size)
        assertEquals(parentNoteId, lines[0].noteId)
        assertNull(lines[1].noteId) // New line should have no ID
        assertEquals("child_1", lines[2].noteId)
    }

    @Test
    fun `deletion should remove lines and preserve remaining IDs`() {
        val initialLines = listOf(
            NoteLine("Line 1", parentNoteId),
            NoteLine("Line 2", "child_1"),
            NoteLine("Line 3", "child_2"),
            NoteLine("Line 4", "child_3")
        )
        tracker.setTrackedLines(initialLines)
        
        // Delete Line 2
        tracker.updateTrackedLines("Line 1\nLine 3\nLine 4")
        
        val lines = tracker.getTrackedLines()
        assertEquals(3, lines.size)
        assertEquals(parentNoteId, lines[0].noteId)
        assertEquals("child_2", lines[1].noteId) // Line 3 keeps its ID
        assertEquals("child_3", lines[2].noteId) // Line 4 keeps its ID
    }

    @Test
    fun `modification should preserve note ID`() {
        val initialLines = listOf(
            NoteLine("Line 1", parentNoteId),
            NoteLine("Original", "child_1")
        )
        tracker.setTrackedLines(initialLines)
        
        // Modify the second line
        tracker.updateTrackedLines("Line 1\nModified")
        
        val lines = tracker.getTrackedLines()
        assertEquals(2, lines.size)
        assertEquals(parentNoteId, lines[0].noteId)
        assertEquals("child_1", lines[1].noteId) // ID preserved even though content changed
    }

    @Test
    fun `first line should always have parent ID even after modification`() {
        val initialLines = listOf(
            NoteLine("Original first", parentNoteId),
            NoteLine("Line 2", "child_1")
        )
        tracker.setTrackedLines(initialLines)
        
        // Modify first line
        tracker.updateTrackedLines("Modified first\nLine 2")
        
        val lines = tracker.getTrackedLines()
        assertEquals(parentNoteId, lines[0].noteId)
    }

    @Test
    fun `multiple insertions should all have null IDs`() {
        val initialLines = listOf(
            NoteLine("Line 1", parentNoteId),
            NoteLine("Line 4", "child_1")
        )
        tracker.setTrackedLines(initialLines)
        
        // Insert two lines
        tracker.updateTrackedLines("Line 1\nLine 2\nLine 3\nLine 4")
        
        val lines = tracker.getTrackedLines()
        assertEquals(4, lines.size)
        assertNull(lines[1].noteId)
        assertNull(lines[2].noteId)
        assertEquals("child_1", lines[3].noteId)
    }

    @Test
    fun `updateLineNoteId should update specific line`() {
        tracker.setTrackedLines(listOf(
            NoteLine("Line 1", parentNoteId),
            NoteLine("Line 2", null)
        ))
        
        tracker.updateLineNoteId(1, "new_child_id")
        
        val lines = tracker.getTrackedLines()
        assertEquals("new_child_id", lines[1].noteId)
    }

    @Test
    fun `updateLineNoteId with invalid index should not crash`() {
        tracker.setTrackedLines(listOf(
            NoteLine("Line 1", parentNoteId)
        ))
        
        // Try to update index that doesn't exist
        tracker.updateLineNoteId(10, "new_id")
        
        val lines = tracker.getTrackedLines()
        assertEquals(1, lines.size)
        assertEquals(parentNoteId, lines[0].noteId)
    }

    @Test
    fun `complex scenario with insertions deletions and modifications`() {
        val initialLines = listOf(
            NoteLine("Line 1", parentNoteId),
            NoteLine("Line 2", "child_1"),
            NoteLine("Line 3", "child_2"),
            NoteLine("Line 4", "child_3")
        )
        tracker.setTrackedLines(initialLines)

        // Positional matching: Line 2 -> Modified Line 3, Line 3 -> New Line, Line 4 kept
        tracker.updateTrackedLines("Line 1\nModified Line 3\nNew Line\nLine 4")

        val lines = tracker.getTrackedLines()
        assertEquals(4, lines.size)
        assertEquals(parentNoteId, lines[0].noteId)
        assertEquals("child_1", lines[1].noteId) // Position 1 modification keeps child_1's ID
        assertEquals("child_2", lines[2].noteId) // Position 2 modification keeps child_2's ID
        assertEquals("child_3", lines[3].noteId) // Line 4 exact match keeps its ID
    }
}

