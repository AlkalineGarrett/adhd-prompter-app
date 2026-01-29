package org.alkaline.taskbrain.dsl.builtins

import org.alkaline.taskbrain.dsl.runtime.BuiltinFunction
import org.alkaline.taskbrain.dsl.runtime.BuiltinRegistry
import org.alkaline.taskbrain.dsl.runtime.ButtonVal
import org.alkaline.taskbrain.dsl.runtime.ExecutionException
import org.alkaline.taskbrain.dsl.runtime.ScheduleFrequency
import org.alkaline.taskbrain.dsl.runtime.ScheduleVal
import org.alkaline.taskbrain.dsl.runtime.StringVal

/**
 * Action-related builtin functions: button and schedule.
 *
 * Phase 0f: Part of view caching plan - button and schedule support.
 */
object ActionFunctions {

    fun register(registry: BuiltinRegistry) {
        registry.register(buttonFunction)
        registry.register(scheduleFunction)

        // Register schedule frequency constants
        ScheduleConstants.register(registry)
    }

    /**
     * button(label, action) - Creates a button that executes action when clicked.
     *
     * Usage:
     * - button("Create Note", [new(path: "inbox")])
     * - button("Done", [.append(" done")])
     *
     * @param label String label for the button
     * @param action Lambda/deferred block to execute on click
     */
    private val buttonFunction = BuiltinFunction(name = "button") { args, _ ->
        val label = args.requireString(0, "button", "label")
        val action = args.requireLambda(1, "button", "action")
        ButtonVal(label.value, action)
    }

    /**
     * schedule(frequency, action) - Creates a scheduled action that runs at specified intervals.
     *
     * Usage:
     * - schedule(daily, [new(path: date)])
     * - schedule(hourly, [refresh_data])
     *
     * @param frequency Schedule frequency (daily, hourly, weekly) - can be ScheduleVal or identifier
     * @param action Lambda/deferred block to execute on schedule
     */
    private val scheduleFunction = BuiltinFunction(name = "schedule") { args, _ ->
        val frequencyArg = args.require(0, "frequency")
        val action = args.requireLambda(1, "schedule", "action")

        val frequency = when (frequencyArg) {
            is ScheduleVal -> frequencyArg.frequency
            is StringVal -> {
                ScheduleFrequency.fromIdentifier(frequencyArg.value)
                    ?: throw ExecutionException(
                        "Unknown schedule frequency '${frequencyArg.value}'. " +
                            "Valid options: ${ScheduleFrequency.entries.joinToString { it.identifier }}"
                    )
            }
            else -> throw ExecutionException(
                "schedule() frequency must be a schedule identifier (daily, hourly, weekly), " +
                    "got ${frequencyArg.typeName}"
            )
        }

        ScheduleVal(frequency, action)
    }
}

/**
 * Schedule frequency constants: daily, hourly, weekly.
 *
 * These are registered as zero-arg functions that return the frequency identifier.
 */
object ScheduleConstants {

    fun register(registry: BuiltinRegistry) {
        registry.register(dailyConstant)
        registry.register(hourlyConstant)
        registry.register(weeklyConstant)
    }

    private val dailyConstant = BuiltinFunction(name = "daily") { args, _ ->
        args.requireNoArgs("daily")
        StringVal(ScheduleFrequency.DAILY.identifier)
    }

    private val hourlyConstant = BuiltinFunction(name = "hourly") { args, _ ->
        args.requireNoArgs("hourly")
        StringVal(ScheduleFrequency.HOURLY.identifier)
    }

    private val weeklyConstant = BuiltinFunction(name = "weekly") { args, _ ->
        args.requireNoArgs("weekly")
        StringVal(ScheduleFrequency.WEEKLY.identifier)
    }
}
