package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.data.Note

/**
 * Bundles the note-related context needed for Mindl execution.
 *
 * This encapsulates the commonly-grouped parameters:
 * - notes: List of notes for find() operations
 * - currentNote: The note containing the directive (for [.] reference)
 * - noteOperations: Interface for mutations
 * - executor: The executor instance for lambda invocation
 * - viewStack: Stack of note IDs being viewed (for circular dependency detection)
 *
 * All fields are optional since different execution contexts may have
 * different subsets available.
 *
 * Milestone 8: Added executor for lambda invocation.
 * Milestone 10: Added viewStack for circular dependency detection in view().
 */
data class NoteContext(
    val notes: List<Note>? = null,
    val currentNote: Note? = null,
    val noteOperations: NoteOperations? = null,
    val executor: Executor? = null,
    val viewStack: List<String> = emptyList()
) {
    companion object {
        val EMPTY = NoteContext()
    }
}
