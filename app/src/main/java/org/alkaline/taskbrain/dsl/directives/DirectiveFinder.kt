package org.alkaline.taskbrain.dsl.directives

import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.dsl.language.Lexer
import org.alkaline.taskbrain.dsl.language.LexerException
import org.alkaline.taskbrain.dsl.language.ParseException
import org.alkaline.taskbrain.dsl.language.Parser
import org.alkaline.taskbrain.dsl.runtime.Environment
import org.alkaline.taskbrain.dsl.runtime.ExecutionException
import org.alkaline.taskbrain.dsl.runtime.Executor

/**
 * Utility for finding directives in note content.
 *
 * A directive is text enclosed in square brackets: [...]
 * Milestone 1: Simple non-nested matching with \[.*?\]
 * Milestone 6: Adds current note context for [.] reference.
 */
object DirectiveFinder {

    /**
     * Creates a unique key for a directive based on its position.
     * This ensures each directive instance has its own cached result,
     * even if multiple directives have the same text (e.g., two [now] directives).
     *
     * @param lineIndex The line number (0-indexed) where the directive appears
     * @param startOffset The character offset within the line where the directive starts
     * @return A string key like "3:15" for line 3, offset 15
     */
    fun directiveKey(lineIndex: Int, startOffset: Int): String = "$lineIndex:$startOffset"
    // Non-greedy match for [...] - will need to handle nesting in later milestones
    private val DIRECTIVE_PATTERN = Regex("""\[[^\[\]]*\]""")

    /**
     * A located directive in note content.
     *
     * @property sourceText The full directive text including brackets
     * @property startOffset The character offset where the directive starts
     * @property endOffset The character offset where the directive ends (exclusive)
     */
    data class FoundDirective(
        val sourceText: String,
        val startOffset: Int,
        val endOffset: Int
    ) {
        /**
         * Compute a hash for this directive for storage lookups.
         */
        fun hash(): String = DirectiveResult.hashDirective(sourceText)
    }

    /**
     * Find all directives in the given content.
     *
     * @param content The note content to search
     * @return List of found directives in order of appearance
     */
    fun findDirectives(content: String): List<FoundDirective> {
        return DIRECTIVE_PATTERN.findAll(content).map { match ->
            FoundDirective(
                sourceText = match.value,
                startOffset = match.range.first,
                endOffset = match.range.last + 1
            )
        }.toList()
    }

    /**
     * Check if the given text contains any directives.
     */
    fun containsDirectives(content: String): Boolean {
        return DIRECTIVE_PATTERN.containsMatchIn(content)
    }

    /**
     * Parse and execute a single directive, returning the result.
     *
     * @param sourceText The directive source text (including brackets)
     * @param notes Optional list of notes for find() operations
     * @param currentNote Optional current note for [.] reference (Milestone 6)
     * @return DirectiveResult containing either the value or an error
     */
    fun executeDirective(
        sourceText: String,
        notes: List<Note>? = null,
        currentNote: Note? = null
    ): DirectiveResult {
        return try {
            val tokens = Lexer(sourceText).tokenize()
            val directive = Parser(tokens, sourceText).parseDirective()
            val env = createEnvironment(notes, currentNote)
            val value = Executor().execute(directive, env)
            DirectiveResult.success(value)
        } catch (e: LexerException) {
            DirectiveResult.failure("Lexer error: ${e.message}")
        } catch (e: ParseException) {
            DirectiveResult.failure("Parse error: ${e.message}")
        } catch (e: ExecutionException) {
            DirectiveResult.failure("Execution error: ${e.message}")
        } catch (e: Exception) {
            DirectiveResult.failure("Unexpected error: ${e.message}")
        }
    }

    /**
     * Create an environment with the appropriate context.
     */
    private fun createEnvironment(notes: List<Note>?, currentNote: Note?): Environment {
        return when {
            notes != null && currentNote != null ->
                Environment.withNotesAndCurrentNote(notes, currentNote)
            notes != null ->
                Environment.withNotes(notes)
            currentNote != null ->
                Environment.withCurrentNote(currentNote)
            else ->
                Environment()
        }
    }

    /**
     * Find, parse, and execute all directives in the content.
     *
     * @param content The note content (single line)
     * @param lineIndex The line index (for position-based keys)
     * @param notes Optional list of notes for find() operations
     * @param currentNote Optional current note for [.] reference (Milestone 6)
     * @return Map of directive position key to execution result
     */
    fun executeAllDirectives(
        content: String,
        lineIndex: Int,
        notes: List<Note>? = null,
        currentNote: Note? = null
    ): Map<String, DirectiveResult> {
        return findDirectives(content).associate { found ->
            directiveKey(lineIndex, found.startOffset) to
                executeDirective(found.sourceText, notes, currentNote)
        }
    }
}
