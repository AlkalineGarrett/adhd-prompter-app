import type { Note } from '@/data/Note'

/**
 * Interface for note mutation operations in the DSL.
 * Abstraction layer allowing DSL to perform note modifications without
 * direct dependency on the repository, enabling easier testing.
 */
export interface NoteOperations {
  createNote(path: string, content: string): Promise<Note>
  getNoteById(noteId: string): Promise<Note | null>
  findByPath(path: string): Promise<Note | null>
  noteExistsAtPath(path: string): Promise<boolean>
  updatePath(noteId: string, newPath: string): Promise<Note>
  updateContent(noteId: string, newContent: string): Promise<Note>
  appendToNote(noteId: string, text: string): Promise<Note>
}

export class NoteOperationException extends Error {
  constructor(message: string, cause?: Error) {
    super(message)
    if (cause) this.cause = cause
  }
}
