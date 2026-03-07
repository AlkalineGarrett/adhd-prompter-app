import { UNDEFINED, stringVal, toDisplayString } from '../runtime/DslValue'
import type { BuiltinFunction } from '../runtime/BuiltinRegistry'
import { ExecutionException } from '../runtime/ExecutionException'

export function getStringFunctions(): BuiltinFunction[] {
  return [
    stringFunction,
    maybeFunction,
    runFunction,
  ]
}

const stringFunction: BuiltinFunction = {
  name: 'string',
  call: (args) => {
    const parts = args.positional.map(toDisplayString)
    return stringVal(parts.join(''))
  },
}

const maybeFunction: BuiltinFunction = {
  name: 'maybe',
  call: (args) => {
    args.requireExactCount(1, 'maybe')
    const val = args.require(0, 'value')
    return val.kind === 'UndefinedVal' ? stringVal('') : val
  },
}

const runFunction: BuiltinFunction = {
  name: 'run',
  call: (args, env) => {
    args.requireExactCount(1, 'run')
    const val = args.require(0, 'deferred')
    if (val.kind === 'LambdaVal') {
      const executor = env.getExecutor()
      if (!executor) {
        throw new ExecutionException("'run' requires an executor context")
      }
      // Provide UNDEFINED for any expected params (e.g., implicit 'i' param)
      const fillerArgs = val.params.map(() => UNDEFINED)
      return executor.invokeLambda(val, fillerArgs)
    }
    return val
  },
}
