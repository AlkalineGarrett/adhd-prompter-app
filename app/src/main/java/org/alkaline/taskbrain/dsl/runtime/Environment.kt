package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.data.Note
import java.time.LocalDateTime

/**
 * Execution environment for Mindl evaluation.
 * Holds variable bindings, context data, and resources for directive execution.
 *
 * Milestone 1: Minimal implementation with variables and scopes.
 * Milestone 5: Adds note list for find() operations.
 * Milestone 6: Adds current note for [.] reference and property access.
 * Milestone 7: Adds note operations for mutations and hierarchy navigation.
 * Milestone 10: Adds view stack for circular dependency detection.
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
     * Milestone 10: Propagates view stack for circular dependency detection.
     * Phase 0c: Propagates onceCache for once[...] expression caching.
     * Phase 3: Propagates mockedTime for trigger verification.
     */
    fun child(): Environment = Environment(
        parent = this,
        context = NoteContext(
            notes = getNotes(),
            currentNote = getCurrentNoteRaw(),
            noteOperations = getNoteOperations(),
            executor = getExecutor(),
            viewStack = getViewStack(),
            onceCache = getOnceCache(),
            mockedTime = getMockedTime()
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
     * Used internally for environment propagation and view rendering.
     */
    internal fun getCurrentNoteRaw(): Note? = context.currentNote ?: parent?.getCurrentNoteRaw()

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
            executor = executor,
            viewStack = getViewStack(),
            onceCache = getOnceCache(),
            mockedTime = getMockedTime()
        )
    )

    // region OnceCache (Phase 0c)

    /**
     * Get the once cache for caching once[...] expression results.
     * Searches up the parent chain if not set locally.
     *
     * Phase 0c.
     */
    fun getOnceCache(): OnceCache? = context.onceCache ?: parent?.getOnceCache()

    /**
     * Get the once cache, creating a default in-memory cache if none exists.
     * This ensures once[...] expressions always have a cache available.
     *
     * Phase 0c.
     */
    fun getOrCreateOnceCache(): OnceCache = getOnceCache() ?: InMemoryOnceCache()

    // endregion

    // region View Stack (Milestone 10)

    /**
     * Get the current view stack.
     * Used for circular dependency detection in view().
     *
     * Milestone 10.
     */
    fun getViewStack(): List<String> = context.viewStack.ifEmpty { parent?.getViewStack() ?: emptyList() }

    /**
     * Check if a note ID is already in the view stack.
     * Used for circular dependency detection.
     *
     * Milestone 10.
     */
    fun isInViewStack(noteId: String): Boolean = getViewStack().contains(noteId)

    /**
     * Get a formatted string of the view stack path for error messages.
     * Example: "note1 → note2 → note3"
     *
     * Milestone 10.
     */
    fun getViewStackPath(): String = getViewStack().joinToString(" → ")

    /**
     * Create a child environment with a note ID added to the view stack.
     * Used when entering a view to track the dependency chain.
     *
     * Milestone 10.
     */
    fun pushViewStack(noteId: String): Environment = Environment(
        parent = this,
        context = NoteContext(
            notes = getNotes(),
            currentNote = getCurrentNoteRaw(),
            noteOperations = getNoteOperations(),
            executor = getExecutor(),
            viewStack = getViewStack() + noteId,
            onceCache = getOnceCache(),
            mockedTime = getMockedTime()
        )
    )

    // endregion

    // region Mocked Time (Phase 3)

    /**
     * Get the mocked time for trigger verification.
     * Returns null if not mocking time (use real time).
     *
     * Phase 3.
     */
    fun getMockedTime(): LocalDateTime? = context.mockedTime ?: parent?.getMockedTime()

    /**
     * Create a child environment with mocked time for trigger verification.
     * When mocked time is set, date/time/datetime functions return values based on it.
     *
     * Phase 3.
     */
    fun withMockedTime(time: LocalDateTime): Environment = Environment(
        parent = this,
        context = NoteContext(
            notes = getNotes(),
            currentNote = getCurrentNoteRaw(),
            noteOperations = getNoteOperations(),
            executor = getExecutor(),
            viewStack = getViewStack(),
            onceCache = getOnceCache(),
            mockedTime = time
        )
    )

    // endregion

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
