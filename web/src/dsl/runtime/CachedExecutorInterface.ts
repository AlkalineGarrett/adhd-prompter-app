import type { Note } from '@/data/Note'
import type { NoteOperations } from './NoteOperations'

/**
 * Interface for cached directive execution.
 * Breaks the circular dependency between dsl.runtime and dsl.cache packages.
 */
export interface CachedExecutorInterface {
  executeCached(
    sourceText: string,
    notes: Note[],
    currentNote: Note | null,
    noteOperations?: NoteOperations,
    viewStack?: string[],
  ): CachedExecutionResultInterface
}

export interface CachedExecutionResultInterface {
  displayValue: string | null
  errorMessage: string | null
  cacheHit: boolean
  dependencies: unknown
}
