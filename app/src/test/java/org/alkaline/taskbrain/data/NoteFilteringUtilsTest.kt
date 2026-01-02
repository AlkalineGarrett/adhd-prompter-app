package org.alkaline.taskbrain.data

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class NoteFilteringUtilsTest {

    private fun createNote(
        id: String = "note_1",
        userId: String = "user_1",
        parentNoteId: String? = null,
        content: String = "Content",
        updatedAt: Timestamp? = Timestamp(Date()),
        state: String? = null
    ): Note {
        return Note(
            id = id,
            userId = userId,
            parentNoteId = parentNoteId,
            content = content,
            updatedAt = updatedAt,
            state = state
        )
    }

    @Test
    fun `filterTopLevelNotes should exclude child notes`() {
        val notes = listOf(
            createNote("note_1", parentNoteId = null),
            createNote("note_2", parentNoteId = "parent_1"), // Child note
            createNote("note_3", parentNoteId = null)
        )

        val filtered = NoteFilteringUtils.filterTopLevelNotes(notes)

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.parentNoteId == null })
        assertTrue(filtered.any { it.id == "note_1" })
        assertTrue(filtered.any { it.id == "note_3" })
    }

    @Test
    fun `filterTopLevelNotes should exclude deleted notes`() {
        val notes = listOf(
            createNote("note_1", state = null),
            createNote("note_2", state = "deleted"), // Deleted note
            createNote("note_3", state = null)
        )

        val filtered = NoteFilteringUtils.filterTopLevelNotes(notes)

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.state != "deleted" })
        assertTrue(filtered.any { it.id == "note_1" })
        assertTrue(filtered.any { it.id == "note_3" })
    }

    @Test
    fun `filterTopLevelNotes should exclude both child and deleted notes`() {
        val notes = listOf(
            createNote("note_1", parentNoteId = null, state = null),
            createNote("note_2", parentNoteId = "parent_1", state = null), // Child note
            createNote("note_3", parentNoteId = null, state = "deleted"), // Deleted note
            createNote("note_4", parentNoteId = null, state = null)
        )

        val filtered = NoteFilteringUtils.filterTopLevelNotes(notes)

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == "note_1" })
        assertTrue(filtered.any { it.id == "note_4" })
    }

    @Test
    fun `filterTopLevelNotes with empty list should return empty list`() {
        val filtered = NoteFilteringUtils.filterTopLevelNotes(emptyList())
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `sortByUpdatedAtDescending should sort most recent first`() {
        val now = Date()
        val notes = listOf(
            createNote("note_1", updatedAt = Timestamp(Date(now.time - 10000))), // Oldest
            createNote("note_2", updatedAt = Timestamp(Date(now.time - 5000))),
            createNote("note_3", updatedAt = Timestamp(now)) // Most recent
        )

        val sorted = NoteFilteringUtils.sortByUpdatedAtDescending(notes)

        assertEquals(3, sorted.size)
        assertEquals("note_3", sorted[0].id) // Most recent first
        assertEquals("note_2", sorted[1].id)
        assertEquals("note_1", sorted[2].id) // Oldest last
    }

    @Test
    fun `sortByUpdatedAtDescending with null timestamps should handle gracefully`() {
        val notes = listOf(
            createNote("note_1", updatedAt = null),
            createNote("note_2", updatedAt = Timestamp(Date())),
            createNote("note_3", updatedAt = null)
        )

        val sorted = NoteFilteringUtils.sortByUpdatedAtDescending(notes)

        assertEquals(3, sorted.size)
        // Notes with null timestamps should be sorted last
        assertEquals("note_2", sorted[0].id)
    }

    @Test
    fun `filterAndSortNotes should filter and sort correctly`() {
        val now = Date()
        val notes = listOf(
            createNote("note_1", parentNoteId = null, updatedAt = Timestamp(Date(now.time - 10000))),
            createNote("note_2", parentNoteId = "parent_1", updatedAt = Timestamp(now)), // Child - should be filtered
            createNote("note_3", parentNoteId = null, state = "deleted", updatedAt = Timestamp(now)), // Deleted - should be filtered
            createNote("note_4", parentNoteId = null, updatedAt = Timestamp(now)), // Most recent
            createNote("note_5", parentNoteId = null, updatedAt = Timestamp(Date(now.time - 5000)))
        )

        val result = NoteFilteringUtils.filterAndSortNotes(notes)

        assertEquals(3, result.size)
        assertEquals("note_4", result[0].id) // Most recent first (now)
        assertEquals("note_5", result[1].id) // Second (now - 5000)
        assertEquals("note_1", result[2].id) // Oldest (now - 10000)
    }

    @Test
    fun `filterAndSortNotes with all notes filtered should return empty list`() {
        val notes = listOf(
            createNote("note_1", parentNoteId = "parent_1"), // Child
            createNote("note_2", state = "deleted") // Deleted
        )

        val result = NoteFilteringUtils.filterAndSortNotes(notes)

        assertTrue(result.isEmpty())
    }
}

