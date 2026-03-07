import { numberVal } from '../runtime/DslValue'
import type { BuiltinFunction } from '../runtime/BuiltinRegistry'
import { ExecutionException } from '../runtime/ExecutionException'

export function getArithmeticFunctions(): BuiltinFunction[] {
  return [
    { name: 'add', call: (args) => {
      args.requireExactCount(2, 'add')
      const a = args.requireNumber(0, 'add', 'first argument').value
      const b = args.requireNumber(1, 'add', 'second argument').value
      return numberVal(a + b)
    }},
    { name: 'sub', call: (args) => {
      args.requireExactCount(2, 'sub')
      const a = args.requireNumber(0, 'sub', 'first argument').value
      const b = args.requireNumber(1, 'sub', 'second argument').value
      return numberVal(a - b)
    }},
    { name: 'mul', call: (args) => {
      args.requireExactCount(2, 'mul')
      const a = args.requireNumber(0, 'mul', 'first argument').value
      const b = args.requireNumber(1, 'mul', 'second argument').value
      return numberVal(a * b)
    }},
    { name: 'div', call: (args) => {
      args.requireExactCount(2, 'div')
      const a = args.requireNumber(0, 'div', 'first argument').value
      const b = args.requireNumber(1, 'div', 'second argument').value
      if (b === 0) throw new ExecutionException('Division by zero')
      return numberVal(a / b)
    }},
    { name: 'mod', call: (args) => {
      args.requireExactCount(2, 'mod')
      const a = args.requireNumber(0, 'mod', 'first argument').value
      const b = args.requireNumber(1, 'mod', 'second argument').value
      if (b === 0) throw new ExecutionException('Modulo by zero')
      return numberVal(a % b)
    }},
    { name: 'neg', call: (args) => {
      args.requireExactCount(1, 'neg')
      const a = args.requireNumber(0, 'neg', 'argument').value
      return numberVal(-a)
    }},
  ]
}
