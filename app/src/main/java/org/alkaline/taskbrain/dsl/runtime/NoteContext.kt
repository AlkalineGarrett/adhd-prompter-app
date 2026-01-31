package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.data.Note
import java.time.LocalDateTime

/**
 * Interface for cached directive execution.
 * Used to break the circular dependency between dsl.runtime and dsl.cache packages.
 *
 * Phase 1 (Caching Audit): Added to enable view() to execute nested directives through cache.
 */
interface CachedExecutorInterface {
    /**
     * Execute a directive with caching support.
     *
     * @param sourceText The directive source text (including brackets)
     * @param notes All notes for find() operations
     * @param currentNote The note containing this directive
     * @param noteOperations Optional operations for mutations
     * @param viewStack View stack for circular dependency detection
     * @return Result with dependencies for transitive merging
     */
    fun executeCached(
        sourceText: String,
        notes: List<Note>,
        currentNote: Note?,
        noteOperations: NoteOperations? = null,
        viewStack: List<String> = emptyList()
    ): CachedExecutionResultInterface
}

/**
 * Interface for cached execution result.
 * Abstracts the result to avoid circular dependency on cache package types.
 *
 * Phase 1 (Caching Audit): Added for view() transitive dependency tracking.
 */
interface CachedExecutionResultInterface {
    /** The display value of the result, or null on error */
    val displayValue: String?
    /** The error message, or null on success */
    val errorMessage: String?
    /** Whether this was a cache hit */
    val cacheHit: Boolean
    /** Dependencies from this execution (opaque type) */
    val dependencies: Any
}

/**
 * Bundles the note-related context needed for Mindl execution.
 *
 * This encapsulates the commonly-grouped parameters:
 * - notes: List of notes for find() operations
 * - currentNote: The note containing the directive (for [.] reference)
 * - noteOperations: Interface for mutations
 * - executor: The executor instance for lambda invocation
 * - viewStack: Stack of note IDs being viewed (for circular dependency detection)
 * - onceCache: Cache for once[...] expression results
 * - mockedTime: Override for current time (used for trigger verification in Phase 3)
 * - cachedExecutor: Cached directive executor for nested view rendering
 *
 * All fields are optional since different execution contexts may have
 * different subsets available.
 *
 * Milestone 8: Added executor for lambda invocation.
 * Milestone 10: Added viewStack for circular dependency detection in view().
 * Phase 0c: Added onceCache for once[...] expression caching.
 * Phase 3: Added mockedTime for trigger verification.
 * Phase 1 (Caching Audit): Added cachedExecutor for transitive dependency tracking.
 */
data class NoteContext(
    val notes: List<Note>? = null,
    val currentNote: Note? = null,
    val noteOperations: NoteOperations? = null,
    val executor: Executor? = null,
    val viewStack: List<String> = emptyList(),
    val onceCache: OnceCache? = null,
    val mockedTime: LocalDateTime? = null,
    val cachedExecutor: CachedExecutorInterface? = null
) {
    companion object {
        val EMPTY = NoteContext()
    }
}
