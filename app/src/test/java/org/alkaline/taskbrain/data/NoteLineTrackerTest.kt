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
    fun `complex scenario - positional matching`() {
        setLines(
            "Line 1" to parentId,
            "Line 2" to "child_1",
            "Line 3" to "child_2",
            "Line 4" to "child_3"
        )

        tracker.updateTrackedLines("Line 1\nModified Line 3\nNew Line\nLine 4")

        assertLines(
            "Line 1" to parentId,
            "Modified Line 3" to "child_1",
            "New Line" to "child_2",
            "Line 4" to "child_3"
        )
    }
}
