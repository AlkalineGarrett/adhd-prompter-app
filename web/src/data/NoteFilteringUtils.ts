import type { Note } from './Note'

function isTopLevel(note: Note): boolean {
  return note.parentNoteId == null
}

function isNotDeleted(note: Note): boolean {
  return note.state !== 'deleted'
}

function timestampMillis(ts: { toMillis(): number } | null): number {
  return ts?.toMillis() ?? 0
}

export function filterTopLevelNotes(notes: Note[]): Note[] {
  return notes.filter((n) => isTopLevel(n) && isNotDeleted(n))
}

export function sortByUpdatedAtDescending(notes: Note[]): Note[] {
  return [...notes].sort(
    (a, b) => timestampMillis(b.updatedAt) - timestampMillis(a.updatedAt),
  )
}

export function sortByLastAccessedAtDescending(notes: Note[]): Note[] {
  return [...notes].sort(
    (a, b) =>
      timestampMillis(b.lastAccessedAt ?? b.updatedAt) -
      timestampMillis(a.lastAccessedAt ?? a.updatedAt),
  )
}

export function filterAndSortNotes(notes: Note[]): Note[] {
  return sortByUpdatedAtDescending(filterTopLevelNotes(notes))
}

export function filterAndSortNotesByLastAccessed(notes: Note[]): Note[] {
  return sortByLastAccessedAtDescending(filterTopLevelNotes(notes))
}

export function filterDeletedNotes(notes: Note[]): Note[] {
  return notes.filter((n) => isTopLevel(n) && n.state === 'deleted')
}

export function filterAndSortDeletedNotes(notes: Note[]): Note[] {
  return sortByUpdatedAtDescending(filterDeletedNotes(notes))
}
