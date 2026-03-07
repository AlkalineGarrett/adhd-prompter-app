import {
  buttonVal,
  scheduleVal,
  stringVal,
  lambdaVal,
  ScheduleFrequency,
  scheduleFrequencyFromId,
} from '../runtime/DslValue'
import type { BuiltinFunction } from '../runtime/BuiltinRegistry'
import { ExecutionException } from '../runtime/ExecutionException'
import type { BooleanVal } from '../runtime/DslValue'

export function getActionFunctions(): BuiltinFunction[] {
  return [
    buttonFunction,
    scheduleFunction,
    // Schedule frequency constants
    dailyConstant,
    hourlyConstant,
    weeklyConstant,
    dailyAtFunction,
    weeklyAtFunction,
  ]
}

const buttonFunction: BuiltinFunction = {
  name: 'button',
  call: (args) => {
    const label = args.requireString(0, 'button', 'label')
    const action = args.requireLambda(1, 'button', 'action')
    return buttonVal(label.value, action)
  },
}

const scheduleFunction: BuiltinFunction = {
  name: 'schedule',
  call: (args) => {
    const frequencyArg = args.require(0, 'frequency')
    const action = args.requireLambda(1, 'schedule', 'action')

    if (frequencyArg.kind === 'ScheduleVal') {
      return scheduleVal(frequencyArg.frequency, action, frequencyArg.atTime, frequencyArg.precise)
    }
    if (frequencyArg.kind === 'StringVal') {
      const freq = scheduleFrequencyFromId(frequencyArg.value)
      if (!freq) {
        throw new ExecutionException(
          `Unknown schedule frequency '${frequencyArg.value}'. Valid options: ${Object.values(ScheduleFrequency).join(', ')}, or use daily_at("HH:mm"), weekly_at("HH:mm")`,
        )
      }
      return scheduleVal(freq, action)
    }
    throw new ExecutionException(
      `schedule() frequency must be a schedule identifier (daily, hourly, weekly) or time-specific (daily_at, weekly_at), got ${frequencyArg.kind}`,
    )
  },
}

const dailyConstant: BuiltinFunction = {
  name: 'daily',
  call: (args) => {
    args.requireNoArgs('daily')
    return stringVal(ScheduleFrequency.DAILY)
  },
}

const hourlyConstant: BuiltinFunction = {
  name: 'hourly',
  call: (args) => {
    args.requireNoArgs('hourly')
    return stringVal(ScheduleFrequency.HOURLY)
  },
}

const weeklyConstant: BuiltinFunction = {
  name: 'weekly',
  call: (args) => {
    args.requireNoArgs('weekly')
    return stringVal(ScheduleFrequency.WEEKLY)
  },
}

const dailyAtFunction: BuiltinFunction = {
  name: 'daily_at',
  call: (args) => {
    const timeStr = args.requireString(0, 'daily_at', 'time')
    validateTimeFormat(timeStr.value, 'daily_at')
    const precise = (args.getNamed('precise') as BooleanVal | null)?.value ?? false
    const placeholder = lambdaVal([], { kind: 'StringLiteral', value: 'placeholder', position: 0 }, null)
    return scheduleVal(ScheduleFrequency.DAILY, placeholder, timeStr.value, precise)
  },
}

const weeklyAtFunction: BuiltinFunction = {
  name: 'weekly_at',
  call: (args) => {
    const timeStr = args.requireString(0, 'weekly_at', 'time')
    validateTimeFormat(timeStr.value, 'weekly_at')
    const precise = (args.getNamed('precise') as BooleanVal | null)?.value ?? false
    const placeholder = lambdaVal([], { kind: 'StringLiteral', value: 'placeholder', position: 0 }, null)
    return scheduleVal(ScheduleFrequency.WEEKLY, placeholder, timeStr.value, precise)
  },
}

function validateTimeFormat(time: string, funcName: string): void {
  if (!/^([01]?\d|2[0-3]):([0-5]\d)$/.test(time)) {
    throw new ExecutionException(
      `${funcName}() time must be in HH:mm format (24-hour), got "${time}". Examples: "09:00", "14:30", "00:00"`,
    )
  }
}
