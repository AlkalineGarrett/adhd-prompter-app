import { booleanVal } from '../runtime/DslValue'
import type { BuiltinFunction } from '../runtime/BuiltinRegistry'

export function getPatternFunctions(): BuiltinFunction[] {
  return [
    { name: 'matches', call: (args) => {
      args.requireExactCount(2, 'matches')
      const str = args.requireString(0, 'matches', 'first argument')
      const pat = args.requirePattern(1, 'matches', 'second argument')
      return booleanVal(pat.compiledRegex.test(str.value))
    }},
  ]
}
