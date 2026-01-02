package org.alkaline.taskbrain.data

/**
 * Utility functions for filtering and sorting notes.
 */
object NoteFilteringUtils {
    
    /**
     * Filters notes to exclude child notes and deleted notes.
     */
    fun filterTopLevelNotes(notes: List<Note>): List<Note> {
        return notes.filter { note ->
            note.parentNoteId == null && note.state != "deleted"
        }
    }

    /**
     * Sorts notes by updatedAt timestamp in descending order (most recently updated first).
     */
    fun sortByUpdatedAtDescending(notes: List<Note>): List<Note> {
        return notes.sortedByDescending { it.updatedAt }
    }

    /**
     * Filters and sorts notes in one operation.
     */
    fun filterAndSortNotes(notes: List<Note>): List<Note> {
        return sortByUpdatedAtDescending(filterTopLevelNotes(notes))
    }
}

