import type { DslValue } from './DslValue'
import { booleanVal, dateVal, timeVal, dateTimeVal } from './DslValue'
import type { Arguments } from './Arguments'
import { ExecutionException } from './ExecutionException'
import { compareValues } from '../builtins/ComparisonFunctions'

/**
 * Handles method calls on all DslValue types (except NoteVal which has its own handler).
 */
export function callMethod(
  target: DslValue,
  methodName: string,
  args: Arguments,
  position: number,
): DslValue {
  // Try comparison methods (available on all comparable types)
  const compResult = tryComparisonMethod(target, methodName, args)
  if (compResult !== null) return compResult

  switch (target.kind) {
    case 'BooleanVal': return callBooleanMethod(target, methodName, args, position)
    case 'StringVal': return callStringMethod(target, methodName, args, position)
    case 'DateVal': return callDateMethod(target, methodName, args, position)
    case 'TimeVal': return callTimeMethod(target, methodName, args, position)
    case 'DateTimeVal': return callDateTimeMethod(target, methodName, args, position)
    case 'NumberVal': throw new ExecutionException(`Unknown method '${methodName}' on number`, position)
    default: throw new ExecutionException(`Cannot call method '${methodName}' on ${target.kind}`, position)
  }
}

function tryComparisonMethod(target: DslValue, methodName: string, args: Arguments): DslValue | null {
  if (args.size !== 1) return null
  const other = args.getPositional(0)
  if (!other) return null

  switch (methodName) {
    case 'eq': return booleanVal(compareValues(target, other) === 0)
    case 'ne': return booleanVal(compareValues(target, other) !== 0)
    case 'gt': return booleanVal(compareValues(target, other) > 0)
    case 'lt': return booleanVal(compareValues(target, other) < 0)
    case 'gte': return booleanVal(compareValues(target, other) >= 0)
    case 'lte': return booleanVal(compareValues(target, other) <= 0)
    default: return null
  }
}

function callBooleanMethod(target: DslValue & { kind: 'BooleanVal' }, methodName: string, args: Arguments, position: number): DslValue {
  switch (methodName) {
    case 'and': {
      args.requireExactCount(1, 'and')
      const other = args.requireBoolean(0, 'and', 'argument')
      return booleanVal(target.value && other.value)
    }
    case 'or': {
      args.requireExactCount(1, 'or')
      const other = args.requireBoolean(0, 'or', 'argument')
      return booleanVal(target.value || other.value)
    }
    default: throw new ExecutionException(`Unknown method '${methodName}' on boolean`, position)
  }
}

function callStringMethod(target: DslValue & { kind: 'StringVal' }, methodName: string, args: Arguments, position: number): DslValue {
  switch (methodName) {
    case 'startsWith': {
      args.requireExactCount(1, 'startsWith')
      const prefix = args.requireString(0, 'startsWith', 'prefix')
      return booleanVal(target.value.startsWith(prefix.value))
    }
    case 'endsWith': {
      args.requireExactCount(1, 'endsWith')
      const suffix = args.requireString(0, 'endsWith', 'suffix')
      return booleanVal(target.value.endsWith(suffix.value))
    }
    case 'contains': {
      args.requireExactCount(1, 'contains')
      const substring = args.requireString(0, 'contains', 'substring')
      return booleanVal(target.value.includes(substring.value))
    }
    default: throw new ExecutionException(`Unknown method '${methodName}' on string`, position)
  }
}

function callDateMethod(target: DslValue & { kind: 'DateVal' }, methodName: string, args: Arguments, position: number): DslValue {
  if (methodName !== 'plus') throw new ExecutionException(`Unknown method '${methodName}' on date`, position)
  const days = args.getNamed('days')
  if (!days) throw new ExecutionException("date.plus() requires 'days' parameter", position)
  if (days.kind !== 'NumberVal') throw new ExecutionException("date.plus() 'days' must be a number", position)
  const d = new Date(target.value + 'T00:00:00')
  d.setDate(d.getDate() + Math.floor(days.value))
  return dateVal(d.toISOString().slice(0, 10))
}

function callTimeMethod(target: DslValue & { kind: 'TimeVal' }, methodName: string, args: Arguments, position: number): DslValue {
  if (methodName !== 'plus') throw new ExecutionException(`Unknown method '${methodName}' on time`, position)
  const hours = args.getNamed('hours')
  const minutes = args.getNamed('minutes')
  if (!hours && !minutes) throw new ExecutionException('time.plus() requires at least one of: hours, minutes', position)

  // Parse time
  const parts = target.value.split(':').map(Number)
  let totalMinutes = (parts[0] ?? 0) * 60 + (parts[1] ?? 0)
  const secs = parts[2] ?? 0

  if (hours) {
    if (hours.kind !== 'NumberVal') throw new ExecutionException("time.plus() 'hours' must be a number", position)
    totalMinutes += Math.floor(hours.value) * 60
  }
  if (minutes) {
    if (minutes.kind !== 'NumberVal') throw new ExecutionException("time.plus() 'minutes' must be a number", position)
    totalMinutes += Math.floor(minutes.value)
  }

  // Normalize to 24-hour range
  totalMinutes = ((totalMinutes % 1440) + 1440) % 1440
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return timeVal(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(secs).padStart(2, '0')}`)
}

function callDateTimeMethod(target: DslValue & { kind: 'DateTimeVal' }, methodName: string, args: Arguments, position: number): DslValue {
  if (methodName !== 'plus') throw new ExecutionException(`Unknown method '${methodName}' on datetime`, position)
  const days = args.getNamed('days')
  const hours = args.getNamed('hours')
  const minutes = args.getNamed('minutes')
  if (!days && !hours && !minutes) {
    throw new ExecutionException('datetime.plus() requires at least one of: days, hours, minutes', position)
  }

  const d = new Date(target.value)
  if (days) {
    if (days.kind !== 'NumberVal') throw new ExecutionException("datetime.plus() 'days' must be a number", position)
    d.setDate(d.getDate() + Math.floor(days.value))
  }
  if (hours) {
    if (hours.kind !== 'NumberVal') throw new ExecutionException("datetime.plus() 'hours' must be a number", position)
    d.setHours(d.getHours() + Math.floor(hours.value))
  }
  if (minutes) {
    if (minutes.kind !== 'NumberVal') throw new ExecutionException("datetime.plus() 'minutes' must be a number", position)
    d.setMinutes(d.getMinutes() + Math.floor(minutes.value))
  }

  return dateTimeVal(d.toISOString().slice(0, 19))
}
