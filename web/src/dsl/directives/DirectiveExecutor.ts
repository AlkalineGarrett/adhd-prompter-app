import type { Note } from '@/data/Note'
import type { NoteOperations } from '../runtime/NoteOperations'
import type { NoteMutation } from '../runtime/NoteMutation'
import type { DirectiveResult } from './DirectiveResult'
import { directiveResultSuccess, directiveResultFailure, directiveResultWarning, DirectiveWarningType } from './DirectiveResult'
import { findDirectives, directiveKey, hashDirective } from './DirectiveFinder'
import { Lexer } from '../language/Lexer'
import { Parser } from '../language/Parser'
import { Executor } from '../runtime/Executor'
import { Environment } from '../runtime/Environment'
import { ExecutionException } from '../runtime/ExecutionException'
import { IdempotencyAnalyzer } from '../language'

export interface DirectiveExecutionResult {
  result: DirectiveResult
  mutations: NoteMutation[]
}

/**
 * Execute a single directive and return its result along with any mutations.
 */
export function executeDirectiveWithMutations(
  sourceText: string,
  notes: Note[],
  currentNote: Note | null,
  noteOperations?: NoteOperations,
  viewStack: string[] = [],
): DirectiveExecutionResult {
  try {
    const lexer = new Lexer(sourceText)
    const tokens = lexer.tokenize()
    const parser = new Parser(tokens, sourceText)
    const directive = parser.parseDirective()

    const idempotencyResult = IdempotencyAnalyzer.analyze(directive.expression)
    if (!idempotencyResult.isIdempotent) {
      return { result: directiveResultFailure(idempotencyResult.nonIdempotentReason ?? 'Non-idempotent directive'), mutations: [] }
    }

    const mutationResult = IdempotencyAnalyzer.checkUnwrappedMutations(directive.expression)
    if (!mutationResult.isIdempotent) {
      return { result: directiveResultFailure(mutationResult.nonIdempotentReason ?? 'Unwrapped mutation'), mutations: [] }
    }

    const env = Environment.create({
      notes,
      currentNote: currentNote ?? undefined,
      noteOperations,
      viewStack,
    })
    const executor = new Executor()
    const result = executor.execute(directive, env)
    const mutations = env.getMutations()

    if (result.kind === 'LambdaVal') {
      return { result: directiveResultWarning(DirectiveWarningType.NO_EFFECT_LAMBDA), mutations }
    }
    if (result.kind === 'PatternVal') {
      return { result: directiveResultWarning(DirectiveWarningType.NO_EFFECT_PATTERN), mutations }
    }

    return { result: directiveResultSuccess(result), mutations }
  } catch (e) {
    const message = e instanceof ExecutionException ? e.message : (e instanceof Error ? e.message : String(e))
    return { result: directiveResultFailure(message ?? 'Unknown error'), mutations: [] }
  }
}

/**
 * Execute a single directive and return its result (without mutations).
 */
export function executeDirective(
  sourceText: string,
  notes: Note[],
  currentNote: Note | null,
  noteOperations?: NoteOperations,
  viewStack: string[] = [],
): DirectiveResult {
  return executeDirectiveWithMutations(sourceText, notes, currentNote, noteOperations, viewStack).result
}

export interface AllDirectivesResult {
  results: Map<string, DirectiveResult>
  mutations: NoteMutation[]
}

/**
 * Execute all directives in a note's content and return results keyed by position,
 * along with any mutations collected during execution.
 */
export function executeAllDirectives(
  content: string,
  notes: Note[],
  currentNote: Note | null,
  noteOperations?: NoteOperations,
): AllDirectivesResult {
  const results = new Map<string, DirectiveResult>()
  const allMutations: NoteMutation[] = []
  const lines = content.split('\n')

  for (let lineIndex = 0; lineIndex < lines.length; lineIndex++) {
    const directives = findDirectives(lines[lineIndex]!)
    for (const directive of directives) {
      const key = directiveKey(lineIndex, directive.startOffset)
      const { result, mutations } = executeDirectiveWithMutations(
        directive.sourceText, notes, currentNote, noteOperations,
      )
      results.set(key, result)
      allMutations.push(...mutations)
    }
  }

  return { results, mutations: allMutations }
}

/**
 * Execute all directives and return results keyed by source text hash (for Firestore storage).
 */
export async function executeAndHashDirectives(
  content: string,
  notes: Note[],
  currentNote: Note | null,
  noteOperations?: NoteOperations,
): Promise<Map<string, DirectiveResult>> {
  const results = new Map<string, DirectiveResult>()
  const lines = content.split('\n')

  for (let lineIndex = 0; lineIndex < lines.length; lineIndex++) {
    const directives = findDirectives(lines[lineIndex]!)
    for (const directive of directives) {
      const hash = await hashDirective(directive.sourceText)
      const result = executeDirective(directive.sourceText, notes, currentNote, noteOperations)
      results.set(hash, result)
    }
  }

  return results
}
