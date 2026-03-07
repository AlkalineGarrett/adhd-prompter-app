import { booleanVal, dateVal, dateTimeVal, numberVal, timeVal } from '../runtime/DslValue'
import type { BuiltinFunction } from '../runtime/BuiltinRegistry'
import { ExecutionException } from '../runtime/ExecutionException'

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

function currentDate(env: { getMockedTime(): Date | undefined }): string {
  const d = env.getMockedTime() ?? new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

function currentTime(env: { getMockedTime(): Date | undefined }): string {
  const d = env.getMockedTime() ?? new Date()
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

function currentDateTime(env: { getMockedTime(): Date | undefined }): string {
  const d = env.getMockedTime() ?? new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
}

function parseIsoDate(value: string): Date {
  const parts = value.split('-')
  if (parts.length !== 3) throw new ExecutionException(`Invalid date format: '${value}'`)
  return new Date(parseInt(parts[0]!), parseInt(parts[1]!) - 1, parseInt(parts[2]!))
}

export function getDateFunctions(): BuiltinFunction[] {
  return [
    { name: 'date', isDynamic: true, call: (args, env) => {
      args.requireNoArgs('date')
      return dateVal(currentDate(env))
    }},
    { name: 'datetime', isDynamic: true, call: (args, env) => {
      // No args: return current datetime (dynamic)
      // Two args: construct from date + time (static)
      if (args.size === 0) {
        return dateTimeVal(currentDateTime(env))
      }
      if (args.size === 2) {
        const dateArg = args.require(0, 'date')
        const timeArg = args.require(1, 'time')
        if (dateArg.kind !== 'DateVal') {
          throw new ExecutionException(`'datetime' first argument must be a date, got ${dateArg.kind}`)
        }
        if (timeArg.kind !== 'TimeVal') {
          throw new ExecutionException(`'datetime' second argument must be a time, got ${timeArg.kind}`)
        }
        return dateTimeVal(`${dateArg.value}T${timeArg.value}`)
      }
      throw new ExecutionException(`'datetime' takes 0 or 2 arguments, got ${args.size}`)
    }},
    { name: 'time', isDynamic: true, call: (args, env) => {
      args.requireNoArgs('time')
      return timeVal(currentTime(env))
    }},
    { name: 'parse_date', call: (args) => {
      args.requireExactCount(1, 'parse_date')
      const str = args.getPositional(0)
      if (!str || str.kind !== 'StringVal') {
        throw new ExecutionException(`'parse_date' argument must be a string, got ${str?.kind ?? 'nothing'}`)
      }
      if (!/^\d{4}-\d{2}-\d{2}$/.test(str.value)) {
        throw new ExecutionException(`'parse_date' failed to parse date: '${str.value}'`)
      }
      return dateVal(str.value)
    }},
    { name: 'parse_datetime', call: (args) => {
      args.requireExactCount(1, 'parse_datetime')
      const str = args.requireString(0, 'parse_datetime', 'datetime string')
      // Accept "YYYY-MM-DD HH:mm", "YYYY-MM-DD HH:mm:ss", or "YYYY-MM-DDTHH:mm:ss"
      const match = str.value.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}(?::\d{2})?)$/)
      if (!match) {
        throw new ExecutionException(`'parse_datetime' failed to parse: '${str.value}'. Expected format: "YYYY-MM-DD HH:mm" or "YYYY-MM-DDTHH:mm:ss"`)
      }
      const timePart = match[2]!.length === 5 ? `${match[2]}:00` : match[2]!
      return dateTimeVal(`${match[1]}T${timePart}`)
    }},
    { name: 'add_days', call: (args) => {
      args.requireExactCount(2, 'add_days')
      const dateArg = args.require(0, 'date')
      if (dateArg.kind !== 'DateVal') {
        throw new ExecutionException(`'add_days' first argument must be a date, got ${dateArg.kind}`)
      }
      const nArg = args.requireNumber(1, 'add_days', 'days')
      const d = parseIsoDate(dateArg.value)
      d.setDate(d.getDate() + nArg.value)
      return dateVal(`${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`)
    }},
    { name: 'diff_days', call: (args) => {
      args.requireExactCount(2, 'diff_days')
      const d1 = args.require(0, 'date1')
      const d2 = args.require(1, 'date2')
      if (d1.kind !== 'DateVal') {
        throw new ExecutionException(`'diff_days' first argument must be a date, got ${d1.kind}`)
      }
      if (d2.kind !== 'DateVal') {
        throw new ExecutionException(`'diff_days' second argument must be a date, got ${d2.kind}`)
      }
      const date1 = parseIsoDate(d1.value)
      const date2 = parseIsoDate(d2.value)
      const diffMs = date1.getTime() - date2.getTime()
      return numberVal(Math.round(diffMs / (1000 * 60 * 60 * 24)))
    }},
    { name: 'before', call: (args) => {
      args.requireExactCount(2, 'before')
      const d1 = args.require(0, 'date1')
      const d2 = args.require(1, 'date2')
      if (d1.kind !== 'DateVal') {
        throw new ExecutionException(`'before' first argument must be a date, got ${d1.kind}`)
      }
      if (d2.kind !== 'DateVal') {
        throw new ExecutionException(`'before' second argument must be a date, got ${d2.kind}`)
      }
      return booleanVal(d1.value < d2.value)
    }},
    { name: 'after', call: (args) => {
      args.requireExactCount(2, 'after')
      const d1 = args.require(0, 'date1')
      const d2 = args.require(1, 'date2')
      if (d1.kind !== 'DateVal') {
        throw new ExecutionException(`'after' first argument must be a date, got ${d1.kind}`)
      }
      if (d2.kind !== 'DateVal') {
        throw new ExecutionException(`'after' second argument must be a date, got ${d2.kind}`)
      }
      return booleanVal(d1.value > d2.value)
    }},
  ]
}
