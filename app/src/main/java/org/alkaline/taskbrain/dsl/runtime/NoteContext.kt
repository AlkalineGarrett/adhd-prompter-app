package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.data.Note

/**
 * Bundles the note-related context needed for DSL execution.
 *
 * This encapsulates the three commonly-grouped parameters:
 * - notes: List of notes for find() operations
 * - currentNote: The note containing the directive (for [.] reference)
 * - noteOperations: Interface for mutations
 *
 * All fields are optional since different execution contexts may have
 * different subsets available.
 */
data class NoteContext(
    val notes: List<Note>? = null,
    val currentNote: Note? = null,
    val noteOperations: NoteOperations? = null
) {
    companion object {
        val EMPTY = NoteContext()
    }
}
