package org.alkaline.taskbrain.dsl.builtins

import org.alkaline.taskbrain.dsl.runtime.Arguments
import org.alkaline.taskbrain.dsl.runtime.BuiltinFunction
import org.alkaline.taskbrain.dsl.runtime.BuiltinRegistry
import org.alkaline.taskbrain.dsl.runtime.DslValue
import org.alkaline.taskbrain.dsl.runtime.ExecutionException
import org.alkaline.taskbrain.dsl.runtime.ListVal
import org.alkaline.taskbrain.dsl.runtime.NoteVal
import org.alkaline.taskbrain.dsl.runtime.PatternVal
import org.alkaline.taskbrain.dsl.runtime.StringVal

/**
 * Note-related builtin functions.
 *
 * Milestone 5: find(path: pattern, name: pattern)
 */
object NoteFunctions {

    fun register(registry: BuiltinRegistry) {
        registry.register(findFunction)
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
}
