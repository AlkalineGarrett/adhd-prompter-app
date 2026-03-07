import type { Expression } from './Expression'

export interface AnalysisResult {
  isIdempotent: boolean
  nonIdempotentReason: string | null
}

const IDEMPOTENT: AnalysisResult = { isIdempotent: true, nonIdempotentReason: null }

function nonIdempotent(reason: string): AnalysisResult {
  return { isIdempotent: false, nonIdempotentReason: reason }
}

const NON_IDEMPOTENT_METHODS = new Set(['append'])
const NON_IDEMPOTENT_FUNCTIONS = new Set(['new'])
const ACTION_WRAPPER_FUNCTIONS = new Set(['button', 'schedule'])
const IDEMPOTENT_PROPERTIES = new Set(['path', 'name'])

export function analyze(expr: Expression): AnalysisResult {
  switch (expr.kind) {
    case 'NumberLiteral':
    case 'StringLiteral':
    case 'VariableRef':
    case 'CurrentNoteRef':
    case 'PatternExpr':
    case 'OnceExpr':
    case 'RefreshExpr':
      return IDEMPOTENT

    case 'PropertyAccess':
      return analyze(expr.target)

    case 'LambdaExpr':
      return analyze(expr.body)

    case 'LambdaInvocation': {
      const lr = analyze(expr.lambda.body)
      if (!lr.isIdempotent) return lr
      for (const arg of expr.args) {
        const ar = analyze(arg)
        if (!ar.isIdempotent) return ar
      }
      for (const na of expr.namedArgs) {
        const ar = analyze(na.value)
        if (!ar.isIdempotent) return ar
      }
      return IDEMPOTENT
    }

    case 'CallExpr': {
      if (NON_IDEMPOTENT_FUNCTIONS.has(expr.name)) {
        return nonIdempotent(
          `${expr.name}() creates new data and requires an explicit trigger. Wrap in button() or schedule() to execute.`,
        )
      }
      if (ACTION_WRAPPER_FUNCTIONS.has(expr.name)) {
        const firstArg = expr.args[0]
        if (firstArg) {
          const fr = analyze(firstArg)
          if (!fr.isIdempotent) return fr
        }
        return IDEMPOTENT
      }
      for (const arg of expr.args) {
        const ar = analyze(arg)
        if (!ar.isIdempotent) return ar
      }
      for (const na of expr.namedArgs) {
        const ar = analyze(na.value)
        if (!ar.isIdempotent) return ar
      }
      return IDEMPOTENT
    }

    case 'MethodCall': {
      if (NON_IDEMPOTENT_METHODS.has(expr.methodName)) {
        return nonIdempotent(
          `.${expr.methodName}() modifies data and requires an explicit trigger. Wrap in button() or schedule() to execute.`,
        )
      }
      const tr = analyze(expr.target)
      if (!tr.isIdempotent) return tr
      for (const arg of expr.args) {
        const ar = analyze(arg)
        if (!ar.isIdempotent) return ar
      }
      for (const na of expr.namedArgs) {
        const ar = analyze(na.value)
        if (!ar.isIdempotent) return ar
      }
      return IDEMPOTENT
    }

    case 'Assignment': {
      if (expr.target.kind === 'PropertyAccess' && IDEMPOTENT_PROPERTIES.has(expr.target.property)) {
        return analyze(expr.value)
      }
      return analyze(expr.value)
    }

    case 'StatementList':
      for (const stmt of expr.statements) {
        const r = analyze(stmt)
        if (!r.isIdempotent) return r
      }
      return IDEMPOTENT
  }
}

export function checkUnwrappedMutations(expr: Expression): AnalysisResult {
  switch (expr.kind) {
    case 'OnceExpr':
      return IDEMPOTENT

    case 'Assignment': {
      if (expr.target.kind === 'PropertyAccess' && IDEMPOTENT_PROPERTIES.has(expr.target.property)) {
        const targetPath = formatPropertyPath(expr.target)
        return nonIdempotent(
          `Property assignment ${targetPath}: requires once[] wrapper. Use: once[${formatAssignment(expr)}] to prevent unintended re-execution.`,
        )
      }
      return checkUnwrappedMutations(expr.value)
    }

    case 'StatementList':
      for (const stmt of expr.statements) {
        const r = checkUnwrappedMutations(stmt)
        if (!r.isIdempotent) return r
      }
      return IDEMPOTENT

    case 'RefreshExpr':
      return checkUnwrappedMutations(expr.body)

    default:
      return IDEMPOTENT
  }
}

function formatPropertyPath(expr: Expression): string {
  if (expr.kind !== 'PropertyAccess') return '...'
  let prefix: string
  switch (expr.target.kind) {
    case 'CurrentNoteRef': prefix = '.'; break
    case 'PropertyAccess': prefix = formatPropertyPath(expr.target) + '.'; break
    case 'MethodCall': prefix = formatMethodCall(expr.target) + '.'; break
    default: prefix = '...'; break
  }
  return prefix + expr.property
}

function formatMethodCall(expr: Expression): string {
  if (expr.kind !== 'MethodCall') return '...'
  let prefix: string
  switch (expr.target.kind) {
    case 'CurrentNoteRef': prefix = '.'; break
    case 'PropertyAccess': prefix = formatPropertyPath(expr.target) + '.'; break
    case 'MethodCall': prefix = formatMethodCall(expr.target) + '.'; break
    default: prefix = '...'; break
  }
  const args = expr.args.map(formatValue).join(', ')
  return `${prefix}${expr.methodName}(${args})`
}

function formatValue(expr: Expression): string {
  switch (expr.kind) {
    case 'StringLiteral': return `"${expr.value}"`
    case 'NumberLiteral': return String(expr.value)
    default: return '...'
  }
}

function formatAssignment(expr: Expression): string {
  if (expr.kind !== 'Assignment') return '...'
  const target = expr.target.kind === 'PropertyAccess' ? formatPropertyPath(expr.target) : '...'
  const value = formatValue(expr.value)
  return `${target}: ${value}`
}
