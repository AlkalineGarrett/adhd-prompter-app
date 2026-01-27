package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.data.Note

/**
 * Execution environment for DSL evaluation.
 * Holds variable bindings, context data, and resources for directive execution.
 *
 * Milestone 1: Minimal implementation with variables and scopes.
 * Milestone 5: Adds note list for find() operations.
 * Milestone 6: Adds current note for [.] reference and property access.
 * Milestone 7: Adds note operations for mutations and hierarchy navigation.
 */
class Environment private constructor(
    private val parent: Environment?,
    private val context: NoteContext
) {
    /**
     * Create a root environment with the given context.
     */
    constructor(context: NoteContext = NoteContext.EMPTY) : this(null, context)

    /**
     * Create a root environment with individual parameters.
     * Convenience constructor for backward compatibility.
     */
    constructor(
        notes: List<Note>? = null,
        currentNote: Note? = null,
        noteOperations: NoteOperations? = null
    ) : this(NoteContext(notes, currentNote, noteOperations))

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
     * Inherits the note context from the parent.
     *
     * Milestone 8: Propagates executor for lambda invocation.
     */
    fun child(): Environment = Environment(
        parent = this,
        context = NoteContext(
            notes = getNotes(),
            currentNote = getCurrentNoteRaw(),
            noteOperations = getNoteOperations(),
            executor = getExecutor()
        )
    )

    /**
     * Capture the current environment for closures.
     */
    fun capture(): Environment = this

    /**
     * Get the list of notes available for find() operations.
     * Searches up the parent chain if not set locally.
     */
    fun getNotes(): List<Note>? = context.notes ?: parent?.getNotes()

    /**
     * Get the current note as a NoteVal for [.] reference.
     * Searches up the parent chain if not set locally.
     */
    fun getCurrentNote(): NoteVal? = getCurrentNoteRaw()?.let { NoteVal(it) }

    /**
     * Get the raw current note (without wrapping in NoteVal).
     * Used internally for environment propagation.
     */
    private fun getCurrentNoteRaw(): Note? = context.currentNote ?: parent?.getCurrentNoteRaw()

    /**
     * Get the note operations interface for performing mutations.
     * Searches up the parent chain if not set locally.
     */
    fun getNoteOperations(): NoteOperations? = context.noteOperations ?: parent?.getNoteOperations()

    /**
     * Get the executor for lambda invocation.
     * Searches up the parent chain if not set locally.
     *
     * Milestone 8.
     */
    fun getExecutor(): Executor? = context.executor ?: parent?.getExecutor()

    /**
     * Create a child environment with an executor set.
     * Used by Executor to inject itself for lambda invocation.
     *
     * Milestone 8.
     */
    fun withExecutor(executor: Executor): Environment = Environment(
        parent = this,
        context = NoteContext(
            notes = getNotes(),
            currentNote = getCurrentNoteRaw(),
            noteOperations = getNoteOperations(),
            executor = executor
        )
    )

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
         * Create an environment with the given note context.
         */
        fun withContext(context: NoteContext): Environment = Environment(context)

        // Convenience factory methods for common use cases

        fun withNotes(notes: List<Note>): Environment =
            Environment(notes = notes)

        fun withCurrentNote(currentNote: Note): Environment =
            Environment(currentNote = currentNote)

        fun withNotesAndCurrentNote(notes: List<Note>, currentNote: Note): Environment =
            Environment(notes = notes, currentNote = currentNote)

        fun withNoteOperations(noteOperations: NoteOperations): Environment =
            Environment(noteOperations = noteOperations)

        fun withAll(
            notes: List<Note>,
            currentNote: Note,
            noteOperations: NoteOperations
        ): Environment = Environment(notes = notes, currentNote = currentNote, noteOperations = noteOperations)
    }
}
