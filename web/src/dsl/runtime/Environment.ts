import type { Note } from '@/data/Note'
import type { DslValue, NoteVal } from './DslValue'
import { noteVal } from './DslValue'
import type { NoteOperations } from './NoteOperations'
import type { OnceCache } from './OnceCache'
import { InMemoryOnceCache } from './OnceCache'
import type { NoteMutation } from './NoteMutation'
import type { CachedExecutorInterface } from './CachedExecutorInterface'
import type { Executor } from './Executor'

interface NoteContext {
  notes?: Note[]
  currentNote?: Note
  noteOperations?: NoteOperations
  executor?: Executor
  viewStack: string[]
  onceCache?: OnceCache
  mockedTime?: Date
  cachedExecutor?: CachedExecutorInterface
  /** The noteId of the specific line containing the directive being executed. */
  lineNoteId?: string
}

/**
 * Execution environment for DSL evaluation.
 * Holds variable bindings, context data, and resources for directive execution.
 */
export class Environment {
  private readonly variables = new Map<string, DslValue>()
  private readonly mutations: NoteMutation[] = []

  private constructor(
    private readonly parent: Environment | null,
    private readonly context: NoteContext,
  ) {}

  // ---- Factory methods ----

  static create(context?: Partial<NoteContext>): Environment {
    return new Environment(null, { viewStack: [], ...context })
  }

  static withNotes(notes: Note[]): Environment {
    return Environment.create({ notes })
  }

  static withCurrentNote(currentNote: Note): Environment {
    return Environment.create({ currentNote })
  }

  static withNotesAndCurrentNote(notes: Note[], currentNote: Note): Environment {
    return Environment.create({ notes, currentNote })
  }

  static withNoteOperations(noteOperations: NoteOperations): Environment {
    return Environment.create({ noteOperations })
  }

  static withAll(notes: Note[], currentNote: Note, noteOperations: NoteOperations): Environment {
    return Environment.create({ notes, currentNote, noteOperations })
  }

  // ---- Scoping ----

  child(): Environment {
    return new Environment(this, {
      notes: this.getNotes(),
      currentNote: this.getCurrentNoteRaw(),
      noteOperations: this.getNoteOperations(),
      executor: this.getExecutor(),
      viewStack: this.getViewStack(),
      onceCache: this.getOnceCache(),
      mockedTime: this.getMockedTime(),
      cachedExecutor: this.getCachedExecutor(),
      lineNoteId: this.getLineNoteId(),
    })
  }

  capture(): Environment {
    return this
  }

  // ---- Variables ----

  define(name: string, value: DslValue): void {
    this.variables.set(name, value)
  }

  get(name: string): DslValue | null {
    return this.variables.get(name) ?? this.parent?.get(name) ?? null
  }

  // ---- Notes ----

  getNotes(): Note[] | undefined {
    return this.context.notes ?? this.parent?.getNotes()
  }

  getCurrentNote(): NoteVal | null {
    const note = this.getCurrentNoteRaw()
    return note ? noteVal(note) : null
  }

  getCurrentNoteRaw(): Note | undefined {
    return this.context.currentNote ?? this.parent?.getCurrentNoteRaw()
  }

  getNoteOperations(): NoteOperations | undefined {
    return this.context.noteOperations ?? this.parent?.getNoteOperations()
  }

  getNoteById(noteId: string): Note | undefined {
    return this.getNotes()?.find((n) => n.id === noteId)
  }

  getParentNote(note: Note): Note | undefined {
    const parentId = note.parentNoteId
    if (!parentId) return undefined
    return this.getNoteById(parentId)
  }

  // ---- Executor ----

  getExecutor(): Executor | undefined {
    return this.context.executor ?? this.parent?.getExecutor()
  }

  withExecutor(executor: Executor): Environment {
    return new Environment(this, {
      notes: this.getNotes(),
      currentNote: this.getCurrentNoteRaw(),
      noteOperations: this.getNoteOperations(),
      executor,
      viewStack: this.getViewStack(),
      onceCache: this.getOnceCache(),
      mockedTime: this.getMockedTime(),
      cachedExecutor: this.getCachedExecutor(),
      lineNoteId: this.getLineNoteId(),
    })
  }

  // ---- Cached Executor ----

  getCachedExecutor(): CachedExecutorInterface | undefined {
    return this.context.cachedExecutor ?? this.parent?.getCachedExecutor()
  }

  withCachedExecutor(cachedExecutor: CachedExecutorInterface): Environment {
    return new Environment(this, {
      notes: this.getNotes(),
      currentNote: this.getCurrentNoteRaw(),
      noteOperations: this.getNoteOperations(),
      executor: this.getExecutor(),
      viewStack: this.getViewStack(),
      onceCache: this.getOnceCache(),
      mockedTime: this.getMockedTime(),
      cachedExecutor,
      lineNoteId: this.getLineNoteId(),
    })
  }

  // ---- Line Note ID ----

  getLineNoteId(): string | undefined {
    return this.context.lineNoteId ?? this.parent?.getLineNoteId()
  }

  // ---- OnceCache ----

  getOnceCache(): OnceCache | undefined {
    return this.context.onceCache ?? this.parent?.getOnceCache()
  }

  getOrCreateOnceCache(): OnceCache {
    return this.getOnceCache() ?? new InMemoryOnceCache()
  }

  // ---- View Stack ----

  getViewStack(): string[] {
    const stack = this.context.viewStack
    if (stack.length > 0) return stack
    return this.parent?.getViewStack() ?? []
  }

  isInViewStack(noteId: string): boolean {
    return this.getViewStack().includes(noteId)
  }

  getViewStackPath(): string {
    return this.getViewStack().join(' → ')
  }

  pushViewStack(noteId: string): Environment {
    return new Environment(this, {
      notes: this.getNotes(),
      currentNote: this.getCurrentNoteRaw(),
      noteOperations: this.getNoteOperations(),
      executor: this.getExecutor(),
      viewStack: [...this.getViewStack(), noteId],
      onceCache: this.getOnceCache(),
      mockedTime: this.getMockedTime(),
      cachedExecutor: this.getCachedExecutor(),
      lineNoteId: this.getLineNoteId(),
    })
  }

  // ---- Mocked Time ----

  getMockedTime(): Date | undefined {
    return this.context.mockedTime ?? this.parent?.getMockedTime()
  }

  withMockedTime(time: Date): Environment {
    return new Environment(this, {
      notes: this.getNotes(),
      currentNote: this.getCurrentNoteRaw(),
      noteOperations: this.getNoteOperations(),
      executor: this.getExecutor(),
      viewStack: this.getViewStack(),
      onceCache: this.getOnceCache(),
      mockedTime: time,
      cachedExecutor: this.getCachedExecutor(),
      lineNoteId: this.getLineNoteId(),
    })
  }

  // ---- Mutation Tracking ----

  registerMutation(mutation: NoteMutation): void {
    if (this.parent) {
      this.parent.registerMutation(mutation)
    } else {
      this.mutations.push(mutation)
    }
  }

  getMutations(): NoteMutation[] {
    if (this.parent) return this.parent.getMutations()
    return [...this.mutations]
  }

  clearMutations(): void {
    if (this.parent) {
      this.parent.clearMutations()
    } else {
      this.mutations.length = 0
    }
  }
}
