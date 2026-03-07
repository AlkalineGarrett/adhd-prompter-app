import type { DslValue } from '../runtime/DslValue'
import { booleanVal, dateVal, timeVal, dateTimeVal } from '../runtime/DslValue'
import type { BuiltinFunction } from '../runtime/BuiltinRegistry'
import { ExecutionException } from '../runtime/ExecutionException'

export function getComparisonFunctions(): BuiltinFunction[] {
  return [
    { name: 'if', call: (args) => {
      args.requireExactCount(3, 'if')
      const condition = args.requireBoolean(0, 'if', 'condition')
      const thenValue = args.require(1, 'then value')
      const elseValue = args.require(2, 'else value')
      return condition.value ? thenValue : elseValue
    }},
    { name: 'eq', call: (args) => {
      args.requireExactCount(2, 'eq')
      return booleanVal(compareValues(args.require(0, 'first argument'), args.require(1, 'second argument')) === 0)
    }},
    { name: 'ne', call: (args) => {
      args.requireExactCount(2, 'ne')
      return booleanVal(compareValues(args.require(0, 'first argument'), args.require(1, 'second argument')) !== 0)
    }},
    { name: 'gt', call: (args) => {
      args.requireExactCount(2, 'gt')
      return booleanVal(compareValues(args.require(0, 'first argument'), args.require(1, 'second argument')) > 0)
    }},
    { name: 'lt', call: (args) => {
      args.requireExactCount(2, 'lt')
      return booleanVal(compareValues(args.require(0, 'first argument'), args.require(1, 'second argument')) < 0)
    }},
    { name: 'gte', call: (args) => {
      args.requireExactCount(2, 'gte')
      return booleanVal(compareValues(args.require(0, 'first argument'), args.require(1, 'second argument')) >= 0)
    }},
    { name: 'lte', call: (args) => {
      args.requireExactCount(2, 'lte')
      return booleanVal(compareValues(args.require(0, 'first argument'), args.require(1, 'second argument')) <= 0)
    }},
    { name: 'and', call: (args) => {
      args.requireExactCount(2, 'and')
      const a = args.requireBoolean(0, 'and', 'first argument')
      const b = args.requireBoolean(1, 'and', 'second argument')
      return booleanVal(a.value && b.value)
    }},
    { name: 'or', call: (args) => {
      args.requireExactCount(2, 'or')
      const a = args.requireBoolean(0, 'or', 'first argument')
      const b = args.requireBoolean(1, 'or', 'second argument')
      return booleanVal(a.value || b.value)
    }},
    { name: 'not', call: (args) => {
      args.requireExactCount(1, 'not')
      const a = args.requireBoolean(0, 'not', 'argument')
      return booleanVal(!a.value)
    }},
  ]
}

/**
 * Compare two DslValues. Handles string coercion to temporal types.
 */
export function compareValues(a: DslValue, b: DslValue): number {
  const [normA, normB] = normalizeForComparison(a, b)

  if (normA.kind === 'NumberVal' && normB.kind === 'NumberVal') return cmp(normA.value, normB.value)
  if (normA.kind === 'StringVal' && normB.kind === 'StringVal') return normA.value < normB.value ? -1 : normA.value > normB.value ? 1 : 0
  if (normA.kind === 'BooleanVal' && normB.kind === 'BooleanVal') return cmp(Number(normA.value), Number(normB.value))
  if (normA.kind === 'DateVal' && normB.kind === 'DateVal') return normA.value < normB.value ? -1 : normA.value > normB.value ? 1 : 0
  if (normA.kind === 'TimeVal' && normB.kind === 'TimeVal') return normA.value < normB.value ? -1 : normA.value > normB.value ? 1 : 0
  if (normA.kind === 'DateTimeVal' && normB.kind === 'DateTimeVal') return normA.value < normB.value ? -1 : normA.value > normB.value ? 1 : 0

  throw new ExecutionException(`Cannot compare ${a.kind} with ${b.kind}`)
}

function cmp(a: number, b: number): number {
  return a < b ? -1 : a > b ? 1 : 0
}

function normalizeForComparison(a: DslValue, b: DslValue): [DslValue, DslValue] {
  if (a.kind === 'DateVal' && b.kind === 'StringVal') return [a, parseAsDate(b.value)]
  if (a.kind === 'StringVal' && b.kind === 'DateVal') return [parseAsDate(a.value), b]
  if (a.kind === 'TimeVal' && b.kind === 'StringVal') return [a, parseAsTime(b.value)]
  if (a.kind === 'StringVal' && b.kind === 'TimeVal') return [parseAsTime(a.value), b]
  if (a.kind === 'DateTimeVal' && b.kind === 'StringVal') return [a, parseAsDateTime(b.value)]
  if (a.kind === 'StringVal' && b.kind === 'DateTimeVal') return [parseAsDateTime(a.value), b]
  return [a, b]
}

function parseAsDate(str: string): DslValue {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(str)) throw new ExecutionException(`Cannot parse '${str}' as a date`)
  return dateVal(str)
}

function parseAsTime(str: string): DslValue {
  if (!/^\d{2}:\d{2}(:\d{2})?$/.test(str)) throw new ExecutionException(`Cannot parse '${str}' as a time`)
  return timeVal(str.length === 5 ? `${str}:00` : str)
}

function parseAsDateTime(str: string): DslValue {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(str)) {
    throw new ExecutionException(`Cannot parse '${str}' as a datetime`)
  }
  return dateTimeVal(str.length === 16 ? `${str}:00` : str)
}
