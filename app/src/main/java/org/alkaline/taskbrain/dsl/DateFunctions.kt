package org.alkaline.taskbrain.dsl

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Date and time builtin functions.
 *
 * Milestone 2: date, datetime, time
 */
object DateFunctions {

    fun register(registry: BuiltinRegistry) {
        registry.register(dateFunction)
        registry.register(datetimeFunction)
        registry.register(timeFunction)
    }

    /**
     * date - Returns the current date (no time component).
     * Dynamic: returns a different value each day.
     * Example: [date] -> 2026-01-25
     */
    private val dateFunction = BuiltinFunction(
        name = "date",
        isDynamic = true
    ) { args, _ ->
        if (args.isNotEmpty()) {
            throw ExecutionException("'date' takes no arguments, got ${args.size}")
        }
        DateVal(LocalDate.now())
    }

    /**
     * datetime - Returns the current date and time.
     * Dynamic: returns a different value each call.
     * Example: [datetime] -> 2026-01-25, 14:30:00
     */
    private val datetimeFunction = BuiltinFunction(
        name = "datetime",
        isDynamic = true
    ) { args, _ ->
        if (args.isNotEmpty()) {
            throw ExecutionException("'datetime' takes no arguments, got ${args.size}")
        }
        DateTimeVal(LocalDateTime.now())
    }

    /**
     * time - Returns the current time (no date component).
     * Dynamic: returns a different value each call.
     * Example: [time] -> 14:30:00
     */
    private val timeFunction = BuiltinFunction(
        name = "time",
        isDynamic = true
    ) { args, _ ->
        if (args.isNotEmpty()) {
            throw ExecutionException("'time' takes no arguments, got ${args.size}")
        }
        TimeVal(LocalTime.now())
    }
}
