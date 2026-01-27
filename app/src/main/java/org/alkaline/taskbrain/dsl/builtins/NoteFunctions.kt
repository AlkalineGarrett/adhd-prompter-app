package org.alkaline.taskbrain.dsl.builtins

import kotlinx.coroutines.runBlocking
import org.alkaline.taskbrain.dsl.runtime.Arguments
import org.alkaline.taskbrain.dsl.runtime.BuiltinFunction
import org.alkaline.taskbrain.dsl.runtime.BuiltinRegistry
import org.alkaline.taskbrain.dsl.runtime.DslValue
import org.alkaline.taskbrain.dsl.runtime.ExecutionException
import org.alkaline.taskbrain.dsl.runtime.ListVal
import org.alkaline.taskbrain.dsl.runtime.NoteOperationException
import org.alkaline.taskbrain.dsl.runtime.NoteVal
import org.alkaline.taskbrain.dsl.runtime.PatternVal
import org.alkaline.taskbrain.dsl.runtime.StringVal

/**
 * Note-related builtin functions.
 *
 * Milestone 5: find(path: pattern, name: pattern)
 * Milestone 7: new(path:, content:), maybe_new(path:, maybe_content:)
 */
object NoteFunctions {

    fun register(registry: BuiltinRegistry) {
        registry.register(findFunction)
        registry.register(newFunction)
        registry.register(maybeNewFunction)
    }

    /**
     * find(path: ..., name: ...) - Search for notes matching criteria.
     *
     * Parameters:
     * - path: A string (exact match) or pattern to match against note paths
     * - name: A string (exact match) or pattern to match against note names (first line of content)
     * - where: A lambda predicate for filtering (future milestone)
     *
     * All parameters are optional. Multiple parameters are combined with AND logic.
     *
     * Returns: A list of matching notes (empty list if none found)
     *
     * Examples:
     *   [find(path: "2026-01-15")]                              - Exact path match
     *   [find(path: pattern(digit*4 "-" digit*2 "-" digit*2))]  - Pattern match on path
     *   [find(name: "Shopping List")]                           - Exact name match
     *   [find(name: pattern("Meeting" any*(0..)))]              - Names starting with "Meeting"
     *   [find(path: pattern("journal/" any*(1..)), name: pattern(digit*4 "-" digit*2 "-" digit*2))]
     *
     * Note: The notes must be pre-loaded and passed to the Environment before execution.
     * If no notes are available in the environment, returns an empty list.
     */
    private val findFunction = BuiltinFunction(
        name = "find",
        isDynamic = false  // Results are deterministic based on current note content
    ) { args, env ->
        val pathArg = args["path"]
        val nameArg = args["name"]
        // val whereArg = args["where"]  // Future milestone (lambda)

        // Get the list of notes from the environment
        val notes = env.getNotes()
        if (notes == null || notes.isEmpty()) {
            // No notes available - return empty list
            return@BuiltinFunction ListVal(emptyList())
        }

        // Filter notes based on path and name arguments (AND logic)
        val filtered = notes.filter { note ->
            val pathMatches = matchesFilter(pathArg, note.path, "path")
            val nameMatches = matchesFilter(nameArg, getNoteName(note.content), "name")
            pathMatches && nameMatches
            // Future: && whereArg?.let { invokeLambda(it, NoteVal(note)).asBoolean() } ?: true
        }

        ListVal(filtered.map { NoteVal(it) })
    }

    /**
     * Get the name of a note (first line of content).
     */
    private fun getNoteName(content: String): String {
        return content.lines().firstOrNull() ?: ""
    }

    /**
     * Check if a value matches a filter (string or pattern).
     * Returns true if filter is null (no filter).
     */
    private fun matchesFilter(filter: DslValue?, value: String, paramName: String): Boolean {
        return when (filter) {
            null -> true  // No filter, include all
            is StringVal -> value == filter.value  // Exact match
            is PatternVal -> filter.matches(value)  // Pattern match
            else -> throw ExecutionException(
                "'find' $paramName argument must be a string or pattern, got ${filter.typeName}"
            )
        }
    }

    /**
     * new(path:, content:) - Create a new note at the specified path.
     *
     * Parameters:
     * - path: String - The path for the new note (required)
     * - content: String - Initial content (optional, defaults to "")
     *
     * Returns: The created NoteVal
     * Throws: Error if a note already exists at the path
     *
     * Example:
     *   [new(path: "journal/2026-01-25", content: "# Today's Journal")]
     *
     * Milestone 7.
     */
    private val newFunction = BuiltinFunction(
        name = "new",
        isDynamic = true  // Creates a new note each time
    ) { args, env ->
        val pathArg = args["path"]
            ?: throw ExecutionException("'new' requires a 'path' argument")
        val path = (pathArg as? StringVal)?.value
            ?: throw ExecutionException("'new' path argument must be a string, got ${pathArg.typeName}")

        val contentArg = args["content"]
        val content = when (contentArg) {
            null -> ""
            is StringVal -> contentArg.value
            else -> throw ExecutionException("'new' content argument must be a string, got ${contentArg.typeName}")
        }

        val ops = env.getNoteOperations()
            ?: throw ExecutionException("'new' requires note operations to be available")

        // Check if note already exists
        val exists = runBlocking { ops.noteExistsAtPath(path) }
        if (exists) {
            throw ExecutionException("Note already exists at path: $path")
        }

        // Create the note
        try {
            val note = runBlocking { ops.createNote(path, content) }
            NoteVal(note)
        } catch (e: NoteOperationException) {
            throw ExecutionException("Failed to create note: ${e.message}")
        }
    }

    /**
     * maybe_new(path:, maybe_content:) - Idempotently ensure a note exists at the path.
     *
     * Parameters:
     * - path: String - The path for the note (required)
     * - maybe_content: String - Content to use if note is created (optional, defaults to "")
     *
     * Returns: The existing or newly created NoteVal
     *
     * Behavior:
     * - If a note exists at the path, returns it (ignores maybe_content)
     * - If no note exists, creates one with maybe_content
     *
     * Example:
     *   [maybe_new(path: date, maybe_content: string("# " date))]
     *
     * Milestone 7.
     */
    private val maybeNewFunction = BuiltinFunction(
        name = "maybe_new",
        isDynamic = true  // May create a new note
    ) { args, env ->
        val pathArg = args["path"]
            ?: throw ExecutionException("'maybe_new' requires a 'path' argument")
        val path = (pathArg as? StringVal)?.value
            ?: throw ExecutionException("'maybe_new' path argument must be a string, got ${pathArg.typeName}")

        val maybeContentArg = args["maybe_content"]
        val maybeContent = when (maybeContentArg) {
            null -> ""
            is StringVal -> maybeContentArg.value
            else -> throw ExecutionException("'maybe_new' maybe_content argument must be a string, got ${maybeContentArg.typeName}")
        }

        val ops = env.getNoteOperations()
            ?: throw ExecutionException("'maybe_new' requires note operations to be available")

        // Check for existing note first
        val existing = runBlocking { ops.findByPath(path) }
        if (existing != null) {
            return@BuiltinFunction NoteVal(existing)
        }

        // Create a new note
        try {
            val note = runBlocking { ops.createNote(path, maybeContent) }
            NoteVal(note)
        } catch (e: NoteOperationException) {
            throw ExecutionException("Failed to create note: ${e.message}")
        }
    }
}
