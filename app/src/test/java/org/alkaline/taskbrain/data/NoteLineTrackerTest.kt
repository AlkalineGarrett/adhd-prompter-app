package org.alkaline.taskbrain.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NoteLineTrackerTest {

    private lateinit var tracker: NoteLineTracker
    private val parentId = "parent_123"

    @Before
    fun setUp() {
        tracker = NoteLineTracker(parentId)
    }

    private fun setLines(vararg lines: Pair<String, String?>) {
        tracker.setTrackedLines(lines.map { NoteLine(it.first, it.second) })
    }

    private fun assertLines(vararg expected: Pair<String, String?>) {
        assertEquals(
            expected.map { NoteLine(it.first, it.second) },
            tracker.getTrackedLines()
        )
    }

    @Test
    fun `initial state is empty`() {
        assertTrue(tracker.getTrackedLines().isEmpty())
    }

    @Test
    fun `empty content creates single empty line with parent ID`() {
        tracker.updateTrackedLines("")
        assertLines("" to parentId)
    }

    @Test
    fun `single line gets parent ID`() {
        tracker.updateTrackedLines("First line")
        assertLines("First line" to parentId)
    }

    @Test
    fun `multiple lines - only first gets parent ID`() {
        tracker.updateTrackedLines("Line 1\nLine 2\nLine 3")
        assertLines(
            "Line 1" to parentId,
            "Line 2" to null,
            "Line 3" to null
        )
    }

    @Test
    fun `exact match preserves all IDs`() {
        setLines("Line 1" to parentId, "Line 2" to "child_1", "Line 3" to "child_2")

        tracker.updateTrackedLines("Line 1\nLine 2\nLine 3")

        assertLines(
            "Line 1" to parentId,
            "Line 2" to "child_1",
            "Line 3" to "child_2"
        )
    }

    @Test
    fun `insertion creates lines without IDs`() {
        setLines("Line 1" to parentId, "Line 3" to "child_1")

        tracker.updateTrackedLines("Line 1\nLine 2\nLine 3")

        assertLines(
            "Line 1" to parentId,
            "Line 2" to null,
            "Line 3" to "child_1"
        )
    }

    @Test
    fun `deletion preserves remaining IDs`() {
        setLines(
            "Line 1" to parentId,
            "Line 2" to "child_1",
            "Line 3" to "child_2",
            "Line 4" to "child_3"
        )

        tracker.updateTrackedLines("Line 1\nLine 3\nLine 4")

        assertLines(
            "Line 1" to parentId,
            "Line 3" to "child_2",
            "Line 4" to "child_3"
        )
    }

    @Test
    fun `modification preserves ID`() {
        setLines("Line 1" to parentId, "Original" to "child_1")

        tracker.updateTrackedLines("Line 1\nModified")

        assertLines("Line 1" to parentId, "Modified" to "child_1")
    }

    @Test
    fun `first line always keeps parent ID after modification`() {
        setLines("Original first" to parentId, "Line 2" to "child_1")

        tracker.updateTrackedLines("Modified first\nLine 2")

        assertEquals(parentId, tracker.getTrackedLines()[0].noteId)
    }

    @Test
    fun `multiple insertions all get null IDs`() {
        setLines("Line 1" to parentId, "Line 4" to "child_1")

        tracker.updateTrackedLines("Line 1\nLine 2\nLine 3\nLine 4")

        assertLines(
            "Line 1" to parentId,
            "Line 2" to null,
            "Line 3" to null,
            "Line 4" to "child_1"
        )
    }

    @Test
    fun `updateLineNoteId updates specific line`() {
        setLines("Line 1" to parentId, "Line 2" to null)

        tracker.updateLineNoteId(1, "new_child_id")

        assertEquals("new_child_id", tracker.getTrackedLines()[1].noteId)
    }

    @Test
    fun `updateLineNoteId ignores invalid index`() {
        setLines("Line 1" to parentId)

        tracker.updateLineNoteId(10, "new_id")

        assertEquals(1, tracker.getTrackedLines().size)
    }

    @Test
    fun `modification preserves ID at same position`() {
        setLines(
            "Line 1" to parentId,
            "Line 2" to "child_1",
            "Line 3" to "child_2",
            "Line 4" to "child_3"
        )

        tracker.updateTrackedLines("Line 1\nModified Line 2\nLine 3\nLine 4")

        assertLines(
            "Line 1" to parentId,
            "Modified Line 2" to "child_1",
            "Line 3" to "child_2",
            "Line 4" to "child_3"
        )
    }

    // Requirement: IDs should follow content, not position
    @Test
    fun `swapping two lines preserves IDs with content`() {
        setLines(
            "Line 1" to parentId,
            "Line 2" to "child_1",
            "Line 3" to "child_2"
        )

        // User swaps Line 2 and Line 3
        tracker.updateTrackedLines("Line 1\nLine 3\nLine 2")

        assertLines(
            "Line 1" to parentId,
            "Line 3" to "child_2",  // ID follows content
            "Line 2" to "child_1"   // ID follows content
        )
    }

    @Test
    fun `reordering multiple lines preserves IDs with content`() {
        setLines(
            "Line 1" to parentId,
            "AAA" to "child_a",
            "BBB" to "child_b",
            "CCC" to "child_c"
        )

        // User reorders to CCC, AAA, BBB
        tracker.updateTrackedLines("Line 1\nCCC\nAAA\nBBB")

        assertLines(
            "Line 1" to parentId,
            "CCC" to "child_c",
            "AAA" to "child_a",
            "BBB" to "child_b"
        )
    }

    // Requirement: If first line deleted, second becomes parent content
    @Test
    fun `deleting first line promotes second line to parent`() {
        setLines(
            "Line 1" to parentId,
            "Line 2" to "child_1",
            "Line 3" to "child_2"
        )

        // User deletes first line
        tracker.updateTrackedLines("Line 2\nLine 3")

        // Line 2 becomes new first line with parent ID
        // Line 3 keeps its ID
        assertLines(
            "Line 2" to parentId,
            "Line 3" to "child_2"
        )
        // Note: child_1 should be soft-deleted by repository (not tracker's job)
    }

    @Test
    fun `whitespace-only line is NOT treated as empty`() {
        setLines("Line 1" to parentId)

        tracker.updateTrackedLines("Line 1\n   \nLine 3")

        val lines = tracker.getTrackedLines()
        assertEquals(3, lines.size)
        assertEquals("   ", lines[1].content)  // Whitespace preserved
        assertNull(lines[1].noteId)  // New line, needs ID
    }
}
