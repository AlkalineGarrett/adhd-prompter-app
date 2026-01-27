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

/**
 * Execution environment for DSL evaluation.
 * Holds variable bindings, context data, and resources for directive execution.
 *
 * Milestone 1: Minimal implementation with variables and scopes.
 * Milestone 5: Adds note list for find() operations.
 * Milestone 6: Adds current note for [.] reference and property access.
 * Milestone 7: Adds note operations for mutations and hierarchy navigation.
 */
class Environment(
    private val parent: Environment? = null,
    /**
     * Optional list of notes available for find() operations.
     * This is set at the root environment and inherited by children.
     */
    private val notes: List<Note>? = null,
    /**
     * Optional current note for [.] reference and property access.
     * This is the note containing the directive being executed.
     *
     * Milestone 6.
     */
    private val currentNote: Note? = null,
    /**
     * Optional note operations interface for performing mutations.
     * This is set at the root environment and inherited by children.
     *
     * Milestone 7.
     */
    private val noteOperations: NoteOperations? = null
) {
    private val variables = mutableMapOf<String, DslValue>()

    /**
     * Mutations that occurred during directive execution.
     * Only tracked at the root environment; child environments delegate to parent.
     */
    private val mutations = mutableListOf<NoteMutation>()

    /**
     * Define a variable in the current scope.
     */
    fun define(name: String, value: DslValue) {
        variables[name] = value
    }

    /**
     * Look up a variable, searching parent scopes if not found locally.
     * @return The value, or null if not found
     */
    fun get(name: String): DslValue? {
        return variables[name] ?: parent?.get(name)
    }

    /**
     * Create a child environment for nested scopes.
     * Inherits the notes, current note, and note operations from the parent.
     *
     * Milestone 7: Added noteOperations propagation.
     */
    fun child(): Environment = Environment(
        parent = this,
        notes = getNotes(),
        currentNote = getCurrentNoteRaw(),
        noteOperations = getNoteOperations()
    )

    /**
     * Capture the current environment for closures.
     */
    fun capture(): Environment = this

    /**
     * Get the list of notes available for find() operations.
     * Searches up the parent chain if not set locally.
     */
    fun getNotes(): List<Note>? = notes ?: parent?.getNotes()

    /**
     * Get the current note as a NoteVal for [.] reference.
     * Searches up the parent chain if not set locally.
     *
     * Milestone 6.
     */
    fun getCurrentNote(): NoteVal? = getCurrentNoteRaw()?.let { NoteVal(it) }

    /**
     * Get the raw current note (without wrapping in NoteVal).
     * Used internally for environment propagation.
     */
    private fun getCurrentNoteRaw(): Note? = currentNote ?: parent?.getCurrentNoteRaw()

    /**
     * Get the note operations interface for performing mutations.
     * Searches up the parent chain if not set locally.
     *
     * Milestone 7.
     */
    fun getNoteOperations(): NoteOperations? = noteOperations ?: parent?.getNoteOperations()

    /**
     * Find a note by ID from the available notes.
     * Used for hierarchy navigation (.up, .root).
     *
     * Milestone 7.
     */
    fun getNoteById(noteId: String): Note? {
        return getNotes()?.find { it.id == noteId }
    }

    /**
     * Get the parent note of a given note.
     * Returns null if the note has no parent or parent is not found.
     * Used for hierarchy navigation (.up).
     *
     * Milestone 7.
     */
    fun getParentNote(note: Note): Note? {
        val parentId = note.parentNoteId ?: return null
        return getNoteById(parentId)
    }

    // region Mutation Tracking

    /**
     * Register a mutation that occurred during directive execution.
     * Mutations are tracked at the root environment for later retrieval.
     */
    fun registerMutation(mutation: NoteMutation) {
        if (parent != null) {
            // Delegate to root environment
            parent.registerMutation(mutation)
        } else {
            mutations.add(mutation)
        }
    }

    /**
     * Get all mutations that occurred during directive execution.
     * Should be called on the root environment after execution completes.
     */
    fun getMutations(): List<NoteMutation> {
        return if (parent != null) {
            parent.getMutations()
        } else {
            mutations.toList()
        }
    }

    /**
     * Clear all tracked mutations.
     * Called after mutations have been processed.
     */
    fun clearMutations() {
        if (parent != null) {
            parent.clearMutations()
        } else {
            mutations.clear()
        }
    }

    // endregion

    companion object {
        /**
         * Create an environment with a list of notes for find() operations.
         */
        fun withNotes(notes: List<Note>): Environment = Environment(notes = notes)

        /**
         * Create an environment with a current note for [.] reference.
         *
         * Milestone 6.
         */
        fun withCurrentNote(currentNote: Note): Environment = Environment(currentNote = currentNote)

        /**
         * Create an environment with both notes list and current note.
         *
         * Milestone 6.
         */
        fun withNotesAndCurrentNote(notes: List<Note>, currentNote: Note): Environment =
            Environment(notes = notes, currentNote = currentNote)

        /**
         * Create an environment with note operations for mutations.
         *
         * Milestone 7.
         */
        fun withNoteOperations(noteOperations: NoteOperations): Environment =
            Environment(noteOperations = noteOperations)

        /**
         * Create a fully configured environment with all resources.
         *
         * Milestone 7.
         */
        fun withAll(
            notes: List<Note>,
            currentNote: Note,
            noteOperations: NoteOperations
        ): Environment = Environment(
            notes = notes,
            currentNote = currentNote,
            noteOperations = noteOperations
        )
    }
}
