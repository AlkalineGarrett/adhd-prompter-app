package org.alkaline.taskbrain.dsl

/**
 * Execution environment for DSL evaluation.
 * Holds variable bindings and context for directive execution.
 *
 * Milestone 1: Minimal implementation - will be expanded for variables and scopes.
 */
class Environment(
    private val parent: Environment? = null
) {
    private val variables = mutableMapOf<String, DslValue>()

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
     */
    fun child(): Environment = Environment(this)

    /**
     * Capture the current environment for closures.
     */
    fun capture(): Environment = this
}
