package org.alkaline.taskbrain.dsl

/**
 * Utility for finding directives in note content.
 *
 * A directive is text enclosed in square brackets: [...]
 * Milestone 1: Simple non-nested matching with \[.*?\]
 */
object DirectiveFinder {
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
     * @return DirectiveResult containing either the value or an error
     */
    fun executeDirective(sourceText: String): DirectiveResult {
        return try {
            val tokens = Lexer(sourceText).tokenize()
            val directive = Parser(tokens, sourceText).parseDirective()
            val value = Executor().execute(directive)
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
     * Find, parse, and execute all directives in the content.
     *
     * @param content The note content
     * @return Map of directive hash to execution result
     */
    fun executeAllDirectives(content: String): Map<String, DirectiveResult> {
        return findDirectives(content).associate { found ->
            found.hash() to executeDirective(found.sourceText)
        }
    }
}
