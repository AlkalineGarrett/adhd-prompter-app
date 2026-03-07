import type { Expression } from './Expression'
import { BuiltinRegistry } from '../runtime/BuiltinRegistry'

/**
 * Analyzes AST expressions for dynamic function calls.
 * Dynamic functions can return different results on each call (e.g., date, time).
 */
export function containsDynamicCalls(expr: Expression): boolean {
  switch (expr.kind) {
    case 'NumberLiteral':
    case 'StringLiteral':
    case 'PatternExpr':
    case 'CurrentNoteRef':
    case 'VariableRef':
      return false

    case 'PropertyAccess':
      return containsDynamicCalls(expr.target)

    case 'LambdaExpr':
      return containsDynamicCalls(expr.body)

    case 'LambdaInvocation':
      return (
        containsDynamicCalls(expr.lambda.body) ||
        expr.args.some(containsDynamicCalls) ||
        expr.namedArgs.some((na) => containsDynamicCalls(na.value))
      )

    case 'OnceExpr':
      // once[...] is NOT dynamic - it caches the result permanently
      return false

    case 'RefreshExpr':
      // refresh[...] IS dynamic
      return true

    case 'Assignment':
      return containsDynamicCalls(expr.target) || containsDynamicCalls(expr.value)

    case 'StatementList':
      return expr.statements.some(containsDynamicCalls)

    case 'MethodCall':
      return (
        containsDynamicCalls(expr.target) ||
        expr.args.some(containsDynamicCalls) ||
        expr.namedArgs.some((na) => containsDynamicCalls(na.value))
      )

    case 'CallExpr':
      return (
        BuiltinRegistry.isDynamic(expr.name) ||
        expr.args.some(containsDynamicCalls) ||
        expr.namedArgs.some((na) => containsDynamicCalls(na.value))
      )
  }
}
