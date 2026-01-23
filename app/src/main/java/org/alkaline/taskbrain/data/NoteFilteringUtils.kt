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
     * Sorts notes by lastAccessedAt timestamp in descending order (most recently accessed first).
     * Falls back to updatedAt for notes that don't have lastAccessedAt set.
     */
    fun sortByLastAccessedAtDescending(notes: List<Note>): List<Note> {
        return notes.sortedByDescending { it.lastAccessedAt ?: it.updatedAt }
    }

    /**
     * Filters and sorts notes in one operation.
     */
    fun filterAndSortNotes(notes: List<Note>): List<Note> {
        return sortByUpdatedAtDescending(filterTopLevelNotes(notes))
    }

    /**
     * Filters and sorts notes by lastAccessedAt in one operation.
     * Used for the notes list to show recently viewed notes at top.
     */
    fun filterAndSortNotesByLastAccessed(notes: List<Note>): List<Note> {
        return sortByLastAccessedAtDescending(filterTopLevelNotes(notes))
    }

    /**
     * Filters notes to only include deleted top-level notes.
     */
    fun filterDeletedNotes(notes: List<Note>): List<Note> {
        return notes.filter { note ->
            note.parentNoteId == null && note.state == "deleted"
        }
    }

    /**
     * Filters and sorts deleted notes in one operation.
     */
    fun filterAndSortDeletedNotes(notes: List<Note>): List<Note> {
        return sortByUpdatedAtDescending(filterDeletedNotes(notes))
    }
}

