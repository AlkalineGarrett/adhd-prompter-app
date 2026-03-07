import type { Note } from '@/data/Note'

export enum MutationType {
  PATH_CHANGED = 'PATH_CHANGED',
  CONTENT_CHANGED = 'CONTENT_CHANGED',
  CONTENT_APPENDED = 'CONTENT_APPENDED',
}

export interface NoteMutation {
  noteId: string
  updatedNote: Note
  mutationType: MutationType
  appendedText?: string
}
