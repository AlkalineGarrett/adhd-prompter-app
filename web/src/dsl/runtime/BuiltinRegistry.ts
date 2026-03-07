import type { DslValue } from './DslValue'
import type { Arguments } from './Arguments'
import type { Environment } from './Environment'

export interface BuiltinFunction {
  name: string
  isDynamic?: boolean
  call: (args: Arguments, env: Environment) => DslValue | Promise<DslValue>
}

const functions = new Map<string, BuiltinFunction>()
let initialized = false

function ensureInitialized(): void {
  if (initialized) return
  initialized = true

  // Lazy imports to avoid circular dependency issues
  const { getDateFunctions } = require('../builtins/DateFunctions')
  const { getCharacterConstants } = require('../builtins/CharacterConstants')
  const { getArithmeticFunctions } = require('../builtins/ArithmeticFunctions')
  const { getComparisonFunctions } = require('../builtins/ComparisonFunctions')
  const { getPatternFunctions } = require('../builtins/PatternFunctions')
  const { getNoteFunctions } = require('../builtins/NoteFunctions')
  const { getListFunctions } = require('../builtins/ListFunctions')
  const { getSortConstants } = require('../builtins/SortConstants')
  const { getActionFunctions } = require('../builtins/ActionFunctions')

  const allFunctions: BuiltinFunction[] = [
    ...getDateFunctions(),
    ...getCharacterConstants(),
    ...getArithmeticFunctions(),
    ...getComparisonFunctions(),
    ...getPatternFunctions(),
    ...getNoteFunctions(),
    ...getListFunctions(),
    ...getSortConstants(),
    ...getActionFunctions(),
  ]

  for (const fn of allFunctions) {
    functions.set(fn.name, fn)
  }
}

export const BuiltinRegistry = {
  get(name: string): BuiltinFunction | null {
    ensureInitialized()
    return functions.get(name) ?? null
  },

  has(name: string): boolean {
    ensureInitialized()
    return functions.has(name)
  },

  isDynamic(name: string): boolean {
    ensureInitialized()
    return functions.get(name)?.isDynamic ?? false
  },

  allNames(): Set<string> {
    ensureInitialized()
    return new Set(functions.keys())
  },
}
