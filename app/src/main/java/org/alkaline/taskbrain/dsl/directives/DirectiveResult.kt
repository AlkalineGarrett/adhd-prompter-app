package org.alkaline.taskbrain.dsl.directives

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.dsl.runtime.DslValue
import java.security.MessageDigest

/**
 * Types of warnings that can occur during directive execution.
 * Warnings indicate the directive executed but produced no meaningful result.
 *
 * Milestone 8.
 */
enum class DirectiveWarningType(val displayMessage: String) {
    /** Lambda created but never called - has no effect */
    NO_EFFECT_LAMBDA("Uncalled lambda has no effect"),

    /** Pattern created but never used for matching */
    NO_EFFECT_PATTERN("Unused pattern has no effect")
}

/**
 * Represents a cached directive execution result stored in Firestore.
 *
 * Stored at: notes/{noteId}/directiveResults/{directiveHash}
 *
 * States:
 * - Success: result is non-null, error is null, warning is null
 * - Error: error is non-null
 * - Warning: warning is non-null (may also have result for display purposes)
 */
data class DirectiveResult(
    val result: Map<String, Any?>? = null,  // Serialized DslValue
    val executedAt: Timestamp? = null,
    val error: String? = null,
    val warning: DirectiveWarningType? = null,
    val collapsed: Boolean = true
) {
    /**
     * Deserialize the result to a DslValue.
     * @return The DslValue, or null if result is null or deserialization fails
     */
    fun toValue(): DslValue? {
        return result?.let {
            try {
                DslValue.deserialize(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Get the display string for this result.
     * @param fallback Text to display if result has no value (not an error, just not computed)
     * @return Error message, warning message, computed value, or fallback
     */
    fun toDisplayString(fallback: String = "..."): String {
        return when {
            error != null -> "Error: $error"
            warning != null -> "Warning: ${warning.displayMessage}"
            result != null -> toValue()?.toDisplayString() ?: "null"
            else -> fallback
        }
    }

    /** True if this result has a computed value (not an error, not pending) */
    val isComputed: Boolean
        get() = result != null && error == null

    /** True if this result has a warning */
    val hasWarning: Boolean
        get() = warning != null

    companion object {
        /**
         * Create a DirectiveResult from a successful execution.
         *
         * @param value The computed value
         * @param collapsed Whether the result is collapsed in UI
         */
        fun success(
            value: DslValue,
            collapsed: Boolean = true
        ): DirectiveResult {
            return DirectiveResult(
                result = value.serialize(),
                error = null,
                collapsed = collapsed
            )
        }

        /**
         * Create a DirectiveResult from a failed execution.
         */
        fun failure(errorMessage: String, collapsed: Boolean = true): DirectiveResult {
            return DirectiveResult(
                result = null,
                error = errorMessage,
                collapsed = collapsed
            )
        }

        /**
         * Create a DirectiveResult with a warning.
         * The directive executed but produced no meaningful effect.
         *
         * Milestone 8.
         */
        fun warning(warningType: DirectiveWarningType, collapsed: Boolean = true): DirectiveResult {
            return DirectiveResult(
                result = null,
                error = null,
                warning = warningType,
                collapsed = collapsed
            )
        }

        /**
         * Compute the hash of a directive's source text.
         * Used as the document ID in Firestore.
         */
        fun hashDirective(sourceText: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(sourceText.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
