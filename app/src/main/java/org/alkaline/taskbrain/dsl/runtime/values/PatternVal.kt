package org.alkaline.taskbrain.dsl.runtime.values

import org.alkaline.taskbrain.dsl.language.CharClass
import org.alkaline.taskbrain.dsl.language.CharClassType
import org.alkaline.taskbrain.dsl.language.PatternElement
import org.alkaline.taskbrain.dsl.language.PatternLiteral
import org.alkaline.taskbrain.dsl.language.Quantified
import org.alkaline.taskbrain.dsl.language.Quantifier

/**
 * A pattern value for matching strings.
 * Created by the pattern(...) function.
 *
 * Milestone 4.
 *
 * @property elements The parsed pattern elements (for display and debugging)
 * @property compiledRegex The compiled regex for matching
 */
data class PatternVal(
    val elements: List<PatternElement>,
    val compiledRegex: Regex
) : DslValue() {
    override val typeName: String = "pattern"

    override fun toDisplayString(): String = "pattern(${compiledRegex.pattern})"

    override fun serializeValue(): Any = compiledRegex.pattern

    /**
     * Check if a string matches this pattern.
     */
    fun matches(input: String): Boolean = compiledRegex.matches(input)

    /**
     * Find all matches of this pattern in a string.
     */
    fun findAll(input: String): Sequence<MatchResult> = compiledRegex.findAll(input)

    companion object {
        /**
         * Compile pattern elements into a PatternVal.
         */
        fun compile(elements: List<PatternElement>): PatternVal {
            val regexPattern = elements.joinToString("") { elementToRegex(it) }
            return PatternVal(elements, Regex(regexPattern))
        }

        /**
         * Create a PatternVal from a regex string (for deserialization).
         * Note: We lose the original element structure, but retain matching functionality.
         */
        fun fromRegexString(regexString: String): PatternVal {
            return PatternVal(emptyList(), Regex(regexString))
        }

        /**
         * Convert a pattern element to its regex equivalent.
         */
        private fun elementToRegex(element: PatternElement): String = when (element) {
            is CharClass -> charClassToRegex(element.type)
            is PatternLiteral -> Regex.escape(element.value)
            is Quantified -> elementToRegex(element.element) + quantifierToRegex(element.quantifier)
        }

        /**
         * Convert a character class type to its regex equivalent.
         */
        private fun charClassToRegex(type: CharClassType): String = when (type) {
            CharClassType.DIGIT -> "\\d"
            CharClassType.LETTER -> "[a-zA-Z]"
            CharClassType.SPACE -> "\\s"
            CharClassType.PUNCT -> "[\\p{Punct}]"
            CharClassType.ANY -> "."
        }

        /**
         * Convert a quantifier to its regex equivalent.
         */
        private fun quantifierToRegex(quantifier: Quantifier): String = when (quantifier) {
            is Quantifier.Exact -> "{${quantifier.n}}"
            is Quantifier.Any -> "*"
            is Quantifier.Range -> {
                if (quantifier.max == null) {
                    "{${quantifier.min},}"
                } else {
                    "{${quantifier.min},${quantifier.max}}"
                }
            }
        }
    }
}
