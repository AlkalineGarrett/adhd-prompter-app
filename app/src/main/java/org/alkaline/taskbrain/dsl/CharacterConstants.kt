package org.alkaline.taskbrain.dsl

/**
 * Character constant functions for mobile-friendly string building.
 *
 * Since the DSL has no escape sequences in strings (to make typing on mobile
 * easier), special characters are inserted using these constant functions.
 *
 * Milestone 2: qt, nl, tab, ret
 */
object CharacterConstants {

    fun register(registry: BuiltinRegistry) {
        registry.register(qtFunction)
        registry.register(nlFunction)
        registry.register(tabFunction)
        registry.register(retFunction)
    }

    /**
     * qt - Returns a double quote character.
     * Example: [qt] -> "
     */
    private val qtFunction = BuiltinFunction("qt") { args, _ ->
        if (args.isNotEmpty()) {
            throw ExecutionException("'qt' takes no arguments, got ${args.size}")
        }
        StringVal("\"")
    }

    /**
     * nl - Returns a newline character.
     * Example: [nl] -> \n
     */
    private val nlFunction = BuiltinFunction("nl") { args, _ ->
        if (args.isNotEmpty()) {
            throw ExecutionException("'nl' takes no arguments, got ${args.size}")
        }
        StringVal("\n")
    }

    /**
     * tab - Returns a tab character.
     * Example: [tab] -> \t
     */
    private val tabFunction = BuiltinFunction("tab") { args, _ ->
        if (args.isNotEmpty()) {
            throw ExecutionException("'tab' takes no arguments, got ${args.size}")
        }
        StringVal("\t")
    }

    /**
     * ret - Returns a carriage return character.
     * Example: [ret] -> \r
     */
    private val retFunction = BuiltinFunction("ret") { args, _ ->
        if (args.isNotEmpty()) {
            throw ExecutionException("'ret' takes no arguments, got ${args.size}")
        }
        StringVal("\r")
    }
}
