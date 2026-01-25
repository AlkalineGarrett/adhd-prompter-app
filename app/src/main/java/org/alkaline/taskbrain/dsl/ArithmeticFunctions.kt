package org.alkaline.taskbrain.dsl

/**
 * Arithmetic builtin functions.
 *
 * Milestone 3: add, sub, mul, div, mod
 */
object ArithmeticFunctions {

    fun register(registry: BuiltinRegistry) {
        registry.register(addFunction)
        registry.register(subFunction)
        registry.register(mulFunction)
        registry.register(divFunction)
        registry.register(modFunction)
    }

    /**
     * add(a, b) - Returns the sum of two numbers.
     * Example: [add(1, 2)] -> 3
     */
    private val addFunction = BuiltinFunction(name = "add") { args, _ ->
        requireTwoNumbers(args, "add")
        val a = (args[0] as NumberVal).value
        val b = (args[1] as NumberVal).value
        NumberVal(a + b)
    }

    /**
     * sub(a, b) - Returns the difference of two numbers (a - b).
     * Example: [sub(5, 3)] -> 2
     */
    private val subFunction = BuiltinFunction(name = "sub") { args, _ ->
        requireTwoNumbers(args, "sub")
        val a = (args[0] as NumberVal).value
        val b = (args[1] as NumberVal).value
        NumberVal(a - b)
    }

    /**
     * mul(a, b) - Returns the product of two numbers.
     * Example: [mul(3, 4)] -> 12
     */
    private val mulFunction = BuiltinFunction(name = "mul") { args, _ ->
        requireTwoNumbers(args, "mul")
        val a = (args[0] as NumberVal).value
        val b = (args[1] as NumberVal).value
        NumberVal(a * b)
    }

    /**
     * div(a, b) - Returns the quotient of two numbers (a / b).
     * Example: [div(10, 4)] -> 2.5
     * Throws on division by zero.
     */
    private val divFunction = BuiltinFunction(name = "div") { args, _ ->
        requireTwoNumbers(args, "div")
        val a = (args[0] as NumberVal).value
        val b = (args[1] as NumberVal).value
        if (b == 0.0) {
            throw ExecutionException("Division by zero")
        }
        NumberVal(a / b)
    }

    /**
     * mod(a, b) - Returns the remainder of a divided by b.
     * Example: [mod(10, 3)] -> 1
     * Throws on modulo by zero.
     */
    private val modFunction = BuiltinFunction(name = "mod") { args, _ ->
        requireTwoNumbers(args, "mod")
        val a = (args[0] as NumberVal).value
        val b = (args[1] as NumberVal).value
        if (b == 0.0) {
            throw ExecutionException("Modulo by zero")
        }
        NumberVal(a % b)
    }

    /**
     * Validates that exactly two number arguments are provided.
     */
    private fun requireTwoNumbers(args: Arguments, funcName: String) {
        if (args.size != 2) {
            throw ExecutionException("'$funcName' requires 2 arguments, got ${args.size}")
        }
        val first = args[0]
        val second = args[1]
        if (first !is NumberVal) {
            throw ExecutionException("'$funcName' first argument must be a number, got ${first?.typeName ?: "null"}")
        }
        if (second !is NumberVal) {
            throw ExecutionException("'$funcName' second argument must be a number, got ${second?.typeName ?: "null"}")
        }
    }
}
