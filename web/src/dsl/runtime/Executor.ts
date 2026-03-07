import type { Directive, Expression } from '../language/Expression'
import { containsDynamicCalls } from '../language/DynamicCallAnalyzer'
import type { DslValue, LambdaVal } from './DslValue'
import {
  numberVal,
  stringVal,
  lambdaVal,
  compilePattern,
  typeName,
} from './DslValue'
import { Arguments } from './Arguments'
import { Environment } from './Environment'
import { ExecutionException } from './ExecutionException'
import { BuiltinRegistry } from './BuiltinRegistry'
import { callMethod } from './MethodHandler'
import * as NotePropertyHandler from './NotePropertyHandler'
import { callNoteMethod } from './NoteMethodHandler'

/**
 * Evaluates DSL expressions and produces runtime values.
 */
export class Executor {
  /**
   * Execute a directive and return its result.
   */
  execute(directive: Directive, env: Environment = Environment.create()): DslValue {
    const execEnv = this.createExecutionEnvironment(env)
    const result = this.evaluate(directive.expression, execEnv)

    // Validate that temporal values are wrapped in once[...] or refresh[...]
    this.validateTemporalResult(result, directive.expression, directive.startPosition)

    return result
  }

  /**
   * Evaluate an expression to produce a value.
   */
  evaluate(expr: Expression, env: Environment): DslValue {
    switch (expr.kind) {
      case 'NumberLiteral': return numberVal(expr.value)
      case 'StringLiteral': return stringVal(expr.value)
      case 'CallExpr': return this.evaluateCall(expr, env)
      case 'PatternExpr': return compilePattern(expr.elements)
      case 'CurrentNoteRef': return this.evaluateCurrentNoteRef(expr, env)
      case 'PropertyAccess': return this.evaluatePropertyAccess(expr, env)
      case 'Assignment': return this.evaluateAssignment(expr, env)
      case 'StatementList': return this.evaluateStatementList(expr, env)
      case 'VariableRef': return this.evaluateVariableRef(expr, env)
      case 'MethodCall': return this.evaluateMethodCall(expr, env)
      case 'LambdaExpr': return this.evaluateLambda(expr, env)
      case 'LambdaInvocation': return this.evaluateLambdaInvocation(expr, env)
      case 'OnceExpr': return this.evaluateOnce(expr, env)
      case 'RefreshExpr': return this.evaluateRefresh(expr, env)
    }
  }

  /**
   * Invoke a lambda with the given arguments.
   */
  invokeLambda(lambda: LambdaVal, args: DslValue[]): DslValue {
    const localEnv = (lambda.capturedEnv as Environment).child()
    lambda.params.forEach((param, i) => {
      localEnv.define(param, args[i]!)
    })
    return this.evaluate(lambda.body, localEnv)
  }

  // ---- Private evaluation methods ----

  private createExecutionEnvironment(baseEnv: Environment): Environment {
    if (baseEnv.getExecutor()) return baseEnv
    return baseEnv.withExecutor(this)
  }

  private validateTemporalResult(result: DslValue, expr: Expression, position: number): void {
    if (!containsDynamicCalls(expr)) return
    if (result.kind === 'DateVal' || result.kind === 'TimeVal' || result.kind === 'DateTimeVal') {
      if (expr.kind !== 'OnceExpr' && expr.kind !== 'RefreshExpr') {
        throw new ExecutionException(
          `Bare ${typeName(result)} value is not allowed. Use once[${typeName(result)}] to capture a snapshot, or refresh[...] to specify when to update.`,
          position,
        )
      }
    }
  }

  private evaluateLambda(expr: Expression & { kind: 'LambdaExpr' }, env: Environment): DslValue {
    return lambdaVal(expr.params, expr.body, env.capture())
  }

  private evaluateLambdaInvocation(expr: Expression & { kind: 'LambdaInvocation' }, env: Environment): DslValue {
    const lv = this.evaluateLambda(expr.lambda, env) as LambdaVal
    const argValues = expr.args.map((a) => this.evaluate(a, env))
    if (argValues.length !== lv.params.length) {
      throw new ExecutionException(`Lambda requires ${lv.params.length} argument(s), got ${argValues.length}`, expr.position)
    }
    if (expr.namedArgs.length > 0) {
      throw new ExecutionException('Named arguments not supported for lambda invocation', expr.position)
    }
    return this.invokeLambda(lv, argValues)
  }

  private evaluateOnce(expr: Expression & { kind: 'OnceExpr' }, env: Environment): DslValue {
    const cacheKey = `once:${hashCode(expr.body)}`
    const cache = env.getOrCreateOnceCache()
    const cached = cache.get(cacheKey)
    if (cached) return cached
    const result = this.evaluate(expr.body, env)
    cache.put(cacheKey, result)
    return result
  }

  private evaluateRefresh(expr: Expression & { kind: 'RefreshExpr' }, env: Environment): DslValue {
    return this.evaluate(expr.body, env)
  }

  private evaluateCurrentNoteRef(expr: Expression & { kind: 'CurrentNoteRef' }, env: Environment): DslValue {
    const note = env.getCurrentNote()
    if (!note) throw new ExecutionException('No current note in context (use [.] only within a note)', expr.position)
    return note
  }

  private evaluatePropertyAccess(expr: Expression & { kind: 'PropertyAccess' }, env: Environment): DslValue {
    const target = this.evaluate(expr.target, env)
    if (target.kind === 'NoteVal') return NotePropertyHandler.getProperty(target, expr.property, env)
    throw new ExecutionException(`Cannot access property '${expr.property}' on ${typeName(target)}`, expr.position)
  }

  private evaluateAssignment(expr: Expression & { kind: 'Assignment' }, env: Environment): DslValue {
    const value = this.evaluate(expr.value, env)

    if (expr.target.kind === 'VariableRef') {
      env.define(expr.target.name, value)
    } else if (expr.target.kind === 'PropertyAccess') {
      const targetObj = this.evaluate(expr.target.target, env)
      if (targetObj.kind === 'NoteVal') {
        // Note: In the web port, setProperty is async but we call it synchronously here
        // for compatibility with the synchronous evaluation model.
        // The async operations will be handled at a higher level.
        NotePropertyHandler.setProperty(targetObj, expr.target.property, value, env)
      } else {
        throw new ExecutionException(`Cannot assign to property '${expr.target.property}' on ${typeName(targetObj)}`, expr.position)
      }
    } else if (expr.target.kind === 'CurrentNoteRef') {
      throw new ExecutionException('Cannot assign directly to current note. Use property assignment like [.path: value]', expr.position)
    } else {
      throw new ExecutionException('Invalid assignment target', expr.position)
    }

    return value
  }

  private evaluateStatementList(expr: Expression & { kind: 'StatementList' }, env: Environment): DslValue {
    let result: DslValue = stringVal('')
    for (const statement of expr.statements) {
      result = this.evaluate(statement, env)
    }
    return result
  }

  private evaluateVariableRef(expr: Expression & { kind: 'VariableRef' }, env: Environment): DslValue {
    const val = env.get(expr.name)
    if (val === null) throw new ExecutionException(`Undefined variable '${expr.name}'`, expr.position)
    return val
  }

  private evaluateMethodCall(expr: Expression & { kind: 'MethodCall' }, env: Environment): DslValue {
    const target = this.evaluate(expr.target, env)
    const positionalArgs = expr.args.map((a) => this.evaluate(a, env))
    const namedArgs = new Map(expr.namedArgs.map((na) => [na.name, this.evaluate(na.value, env)]))
    const args = new Arguments(positionalArgs, namedArgs)

    if (target.kind === 'NoteVal') {
      // Note: callNoteMethod is async for append/setProperty, but we call it here.
      // The async result is a Promise<DslValue> which would need to be awaited at a higher level.
      return callNoteMethod(target, expr.methodName, args, env, expr.position) as unknown as DslValue
    }
    return callMethod(target, expr.methodName, args, expr.position)
  }

  private evaluateCall(expr: Expression & { kind: 'CallExpr' }, env: Environment): DslValue {
    // Check if this is a variable
    const variableValue = env.get(expr.name)
    if (variableValue !== null) {
      if (variableValue.kind === 'LambdaVal') {
        const argValues = expr.args.map((a) => this.evaluate(a, env))
        if (argValues.length !== variableValue.params.length) {
          throw new ExecutionException(`Lambda requires ${variableValue.params.length} argument(s), got ${argValues.length}`, expr.position)
        }
        if (expr.namedArgs.length > 0) {
          throw new ExecutionException('Named arguments not supported for lambda invocation', expr.position)
        }
        return this.invokeLambda(variableValue, argValues)
      }
      // Non-lambda variable
      if (expr.args.length === 0 && expr.namedArgs.length === 0) return variableValue
      throw new ExecutionException(`Cannot call ${typeName(variableValue)} as a function`, expr.position)
    }

    // Look up builtin function
    const fn = BuiltinRegistry.get(expr.name)
    if (!fn) throw new ExecutionException(`Unknown function or variable '${expr.name}'`, expr.position)

    const positionalValues = expr.args.map((a) => this.evaluate(a, env))
    const namedValues = new Map(expr.namedArgs.map((na) => [na.name, this.evaluate(na.value, env)]))
    const args = new Arguments(positionalValues, namedValues)

    try {
      return fn.call(args, env) as DslValue
    } catch (e) {
      if (e instanceof ExecutionException) throw e
      throw new ExecutionException(`Error in '${expr.name}': ${(e as Error).message}`, expr.position)
    }
  }
}

/**
 * Simple hash code for an expression (used for once[] cache keys).
 */
function hashCode(expr: Expression): number {
  const str = JSON.stringify(expr)
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash + char) | 0
  }
  return hash
}
