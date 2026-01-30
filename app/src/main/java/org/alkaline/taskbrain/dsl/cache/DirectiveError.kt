package org.alkaline.taskbrain.dsl.cache

/**
 * Represents an error that occurred during directive execution.
 *
 * Errors are categorized by whether they are deterministic (will always occur
 * given the same input) or non-deterministic (might succeed on retry).
 *
 * Phase 4: Error classification for caching.
 *
 * Deterministic errors are cached because re-execution will produce the same error.
 * Non-deterministic errors are NOT cached - retry on next staleness check.
 */
sealed class DirectiveError {
    abstract val message: String
    abstract val position: Int?
    abstract val isDeterministic: Boolean

    /**
     * Returns a user-friendly description of this error.
     */
    open fun describe(): String {
        val posInfo = position?.let { " at position $it" } ?: ""
        return "$message$posInfo"
    }
}

// region Deterministic Errors (Cached)

/**
 * Syntax error in directive code.
 * Always deterministic - same code will always produce same syntax error.
 */
data class SyntaxError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = true
}

/**
 * Type error - wrong type used in operation.
 * Always deterministic - same types will always produce same error.
 */
data class TypeError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = true
}

/**
 * Argument error - missing or invalid arguments.
 * Always deterministic - same arguments will always produce same error.
 */
data class ArgumentError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = true
}

/**
 * Field access error - invalid property or method access.
 * Always deterministic - same access will always produce same error.
 */
data class FieldAccessError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = true
}

/**
 * Validation error - directive violates validation rules.
 * Examples: bare time values, top-level mutations without button/schedule.
 * Always deterministic - same code will always fail validation.
 */
data class ValidationError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = true
}

/**
 * Unknown function or variable error.
 * Always deterministic - same name will always be unknown.
 */
data class UnknownIdentifierError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = true
}

/**
 * Circular dependency error (e.g., in view rendering).
 * Always deterministic - same structure will always cause cycle.
 */
data class CircularDependencyError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = true
}

/**
 * Division by zero or similar arithmetic error.
 * Deterministic for constant expressions, but could be non-deterministic
 * if divisor comes from external data. Treated as deterministic for simplicity.
 */
data class ArithmeticError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = true
}

// endregion

// region Non-Deterministic Errors (Not Cached)

/**
 * Network error - connection failed, timeout, etc.
 * Non-deterministic - might succeed on retry.
 */
data class NetworkError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = false
}

/**
 * Timeout error - operation took too long.
 * Non-deterministic - might succeed faster on retry.
 */
data class TimeoutError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = false
}

/**
 * Resource unavailable error - note not found, file not accessible, etc.
 * Non-deterministic - resource might be created/available later.
 */
data class ResourceUnavailableError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = false
}

/**
 * Permission error - user lacks permission for operation.
 * Non-deterministic - permissions might be granted later.
 */
data class PermissionError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = false
}

/**
 * External service error - third-party service failed.
 * Non-deterministic - service might recover.
 */
data class ExternalServiceError(
    override val message: String,
    override val position: Int? = null
) : DirectiveError() {
    override val isDeterministic: Boolean = false
}

// endregion

// region Conversion Utilities

/**
 * Factory methods for creating DirectiveError from exceptions.
 */
object DirectiveErrorFactory {

    /**
     * Convert a Kotlin exception to an appropriate DirectiveError.
     * Analyzes the exception type and message to classify it.
     */
    fun fromException(e: Throwable, position: Int? = null): DirectiveError {
        val message = e.message ?: e::class.simpleName ?: "Unknown error"

        return when {
            // Check exception type first
            e is java.net.UnknownHostException ||
            e is java.net.ConnectException ||
            e is java.net.SocketException -> NetworkError(message, position)

            e is java.net.SocketTimeoutException ||
            e is java.util.concurrent.TimeoutException -> TimeoutError(message, position)

            e is java.io.FileNotFoundException ||
            e is java.util.NoSuchElementException -> ResourceUnavailableError(message, position)

            e is SecurityException ||
            e is java.nio.file.AccessDeniedException -> PermissionError(message, position)

            e is IllegalArgumentException -> ArgumentError(message, position)

            e is ArithmeticException -> ArithmeticError(message, position)

            // Analyze message for classification hints
            message.contains("syntax", ignoreCase = true) ||
            message.contains("parse", ignoreCase = true) ||
            message.contains("unexpected", ignoreCase = true) -> SyntaxError(message, position)

            message.contains("type", ignoreCase = true) ||
            message.contains("must be a", ignoreCase = true) ||
            message.contains("expected", ignoreCase = true) -> TypeError(message, position)

            message.contains("argument", ignoreCase = true) ||
            message.contains("missing", ignoreCase = true) ||
            message.contains("requires", ignoreCase = true) -> ArgumentError(message, position)

            message.contains("unknown", ignoreCase = true) ||
            message.contains("not found", ignoreCase = true) &&
                !message.contains("note", ignoreCase = true) -> UnknownIdentifierError(message, position)

            message.contains("circular", ignoreCase = true) ||
            message.contains("cycle", ignoreCase = true) -> CircularDependencyError(message, position)

            message.contains("property", ignoreCase = true) ||
            message.contains("field", ignoreCase = true) ||
            message.contains("method", ignoreCase = true) -> FieldAccessError(message, position)

            message.contains("validation", ignoreCase = true) ||
            message.contains("bare time", ignoreCase = true) ||
            message.contains("requires button", ignoreCase = true) ||
            message.contains("requires schedule", ignoreCase = true) -> ValidationError(message, position)

            message.contains("network", ignoreCase = true) ||
            message.contains("connection", ignoreCase = true) -> NetworkError(message, position)

            message.contains("timeout", ignoreCase = true) -> TimeoutError(message, position)

            message.contains("permission", ignoreCase = true) ||
            message.contains("access denied", ignoreCase = true) -> PermissionError(message, position)

            message.contains("not found", ignoreCase = true) ||
            message.contains("does not exist", ignoreCase = true) -> ResourceUnavailableError(message, position)

            // Default to deterministic error (conservative - will cache)
            // This is safe because re-execution will still produce an error
            else -> TypeError(message, position)
        }
    }

    /**
     * Create a syntax error from a parse exception.
     */
    fun fromParseException(message: String, position: Int? = null): SyntaxError {
        return SyntaxError(message, position)
    }

    /**
     * Create an appropriate error from an execution exception.
     * Analyzes the message to determine the error category.
     */
    fun fromExecutionException(message: String, position: Int? = null): DirectiveError {
        return when {
            message.contains("Unknown function or variable") -> UnknownIdentifierError(message, position)
            // Check ArgumentError before TypeError since "requires X arguments, got Y" is an argument error
            message.contains("requires") && message.contains("argument") -> ArgumentError(message, position)
            message.contains("must be a") || message.contains("got ") -> TypeError(message, position)
            message.contains("missing") -> ArgumentError(message, position)
            message.contains("Unknown property") || message.contains("Unknown method") -> FieldAccessError(message, position)
            message.contains("Circular") -> CircularDependencyError(message, position)
            message.contains("Division by zero") || message.contains("Modulo by zero") -> ArithmeticError(message, position)
            message.contains("Note not found") || message.contains("does not exist") -> ResourceUnavailableError(message, position)
            message.contains("Failed to create note") -> ExternalServiceError(message, position)
            else -> TypeError(message, position)  // Default to deterministic
        }
    }
}

// endregion
