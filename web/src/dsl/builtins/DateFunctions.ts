import { dateVal, dateTimeVal, timeVal } from '../runtime/DslValue'
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

export function getDateFunctions(): BuiltinFunction[] {
  return [
    { name: 'date', isDynamic: true, call: (args, env) => {
      args.requireNoArgs('date')
      return dateVal(currentDate(env))
    }},
    { name: 'datetime', isDynamic: true, call: (args, env) => {
      args.requireNoArgs('datetime')
      return dateTimeVal(currentDateTime(env))
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
  ]
}
