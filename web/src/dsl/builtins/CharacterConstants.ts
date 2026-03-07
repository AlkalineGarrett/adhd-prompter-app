import { stringVal } from '../runtime/DslValue'
import type { BuiltinFunction } from '../runtime/BuiltinRegistry'
import { ExecutionException } from '../runtime/ExecutionException'

function charConstant(name: string, value: string): BuiltinFunction {
  return { name, call: (args) => {
    if (args.hasPositional()) throw new ExecutionException(`'${name}' takes no arguments, got ${args.size}`)
    return stringVal(value)
  }}
}

export function getCharacterConstants(): BuiltinFunction[] {
  return [
    charConstant('qt', '"'),
    charConstant('nl', '\n'),
    charConstant('tab', '\t'),
    charConstant('ret', '\r'),
  ]
}
