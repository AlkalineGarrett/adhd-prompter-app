package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.data.Note

/**
 * Represents a mutation that occurred during directive execution.
 * Used to propagate changes back to the ViewModel for cache and UI updates.
 */
data class NoteMutation(
    val noteId: String,
    val updatedNote: Note,
    val mutationType: MutationType
)

/**
 * Types of mutations that can occur during directive execution.
 */
enum class MutationType {
    /** Note path was changed */
    PATH_CHANGED,
    /** Note content was changed (includes name changes) */
    CONTENT_CHANGED,
    /** Content was appended to the note */
    CONTENT_APPENDED
}
