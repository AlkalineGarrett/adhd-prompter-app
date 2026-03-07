import { stringVal } from '../runtime/DslValue'
import type { BuiltinFunction } from '../runtime/BuiltinRegistry'
import { ExecutionException } from '../runtime/ExecutionException'

export const ASCENDING = 'ascending'
export const DESCENDING = 'descending'

export function getSortConstants(): BuiltinFunction[] {
  return [
    { name: 'ascending', call: (args) => {
      if (args.hasPositional()) throw new ExecutionException(`'ascending' takes no arguments, got ${args.size}`)
      return stringVal(ASCENDING)
    }},
    { name: 'descending', call: (args) => {
      if (args.hasPositional()) throw new ExecutionException(`'descending' takes no arguments, got ${args.size}`)
      return stringVal(DESCENDING)
    }},
  ]
}
