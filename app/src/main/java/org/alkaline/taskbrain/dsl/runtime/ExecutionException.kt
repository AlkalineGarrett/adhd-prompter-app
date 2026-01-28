package org.alkaline.taskbrain.dsl.runtime

/**
 * Exception thrown during Mindl execution.
 */
class ExecutionException(
    message: String,
    val position: Int? = null
) : RuntimeException(message)
