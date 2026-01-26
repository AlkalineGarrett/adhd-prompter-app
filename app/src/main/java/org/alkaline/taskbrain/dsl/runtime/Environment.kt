package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.data.Note

/**
 * Execution environment for DSL evaluation.
 * Holds variable bindings, context data, and resources for directive execution.
 *
 * Milestone 1: Minimal implementation with variables and scopes.
 * Milestone 5: Adds note list for find() operations.
 * Milestone 6: Adds current note for [.] reference and property access.
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
    private val currentNote: Note? = null
) {
    private val variables = mutableMapOf<String, DslValue>()

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
     * Inherits the notes and current note from the parent.
     */
    fun child(): Environment = Environment(
        parent = this,
        notes = getNotes(),
        currentNote = getCurrentNoteRaw()
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
    }
}
