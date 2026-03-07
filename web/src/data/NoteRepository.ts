import {
  collection,
  doc,
  getDoc,
  getDocs,
  query,
  where,
  addDoc,
  runTransaction,
  serverTimestamp,
  writeBatch,
  updateDoc,
  type Firestore,
  type DocumentReference,
  type Transaction,
} from 'firebase/firestore'
import type { Auth } from 'firebase/auth'
import { noteFromFirestore, type Note, type NoteLine } from './Note'

export class NoteRepository {
  private readonly notesRef

  constructor(
    private readonly db: Firestore,
    private readonly auth: Auth,
  ) {
    this.notesRef = collection(db, 'notes')
  }

  private requireUserId(): string {
    const uid = this.auth.currentUser?.uid
    if (!uid) throw new Error('User not signed in')
    return uid
  }

  private noteRef(noteId: string): DocumentReference {
    return doc(this.notesRef, noteId)
  }

  private baseNoteData(userId: string, content: string) {
    return {
      userId,
      content,
      updatedAt: serverTimestamp(),
    }
  }

  private newNoteData(userId: string, content: string, parentNoteId?: string) {
    return {
      userId,
      content,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      parentNoteId: parentNoteId ?? null,
    }
  }

  // --- Load operations ---

  async loadNoteWithChildren(noteId: string): Promise<NoteLine[]> {
    this.requireUserId()
    const docSnap = await getDoc(this.noteRef(noteId))

    if (!docSnap.exists()) {
      return [{ content: '', noteId }]
    }

    const data = docSnap.data()
    const content = (data.content as string) ?? ''
    const containedNotes = (data.containedNotes as string[]) ?? []

    const parentLine: NoteLine = { content, noteId }
    const childLines = await this.loadChildNotes(containedNotes)
    const allLines = [parentLine, ...childLines]

    // Append empty line for typing, unless note is a single empty line
    if (allLines.length === 1 && allLines[0]!.content === '') {
      return allLines
    }
    return [...allLines, { content: '', noteId: null }]
  }

  private async loadChildNotes(childIds: string[]): Promise<NoteLine[]> {
    return Promise.all(childIds.map((id) => this.loadChildNote(id)))
  }

  private async loadChildNote(childId: string): Promise<NoteLine> {
    if (childId === '') return { content: '', noteId: null }

    try {
      const childDoc = await getDoc(this.noteRef(childId))
      if (childDoc.exists()) {
        const content = (childDoc.data().content as string) ?? ''
        return { content, noteId: childId }
      }
      return { content: '', noteId: null }
    } catch {
      return { content: '', noteId: null }
    }
  }

  async loadUserNotes(): Promise<Note[]> {
    const userId = this.requireUserId()
    const q = query(this.notesRef, where('userId', '==', userId))
    const snapshot = await getDocs(q)

    return snapshot.docs
      .map((d) => noteFromFirestore(d.id, d.data()))
      .filter((n) => n.parentNoteId == null && n.state !== 'deleted')
  }

  async loadAllUserNotes(): Promise<Note[]> {
    const userId = this.requireUserId()
    const q = query(this.notesRef, where('userId', '==', userId))
    const snapshot = await getDocs(q)

    return snapshot.docs
      .map((d) => noteFromFirestore(d.id, d.data()))
      .filter((n) => n.parentNoteId == null)
  }

  async loadNoteById(noteId: string): Promise<Note | null> {
    this.requireUserId()
    const docSnap = await getDoc(this.noteRef(noteId))
    if (!docSnap.exists()) return null
    return noteFromFirestore(docSnap.id, docSnap.data())
  }

  async loadNotesWithFullContent(): Promise<Note[]> {
    const userId = this.requireUserId()
    const q = query(this.notesRef, where('userId', '==', userId))
    const snapshot = await getDocs(q)

    const topLevelNotes = snapshot.docs
      .map((d) => noteFromFirestore(d.id, d.data()))
      .filter((n) => n.parentNoteId == null && n.state !== 'deleted')

    return Promise.all(topLevelNotes.map((n) => this.reconstructNoteContent(n)))
  }

  private async reconstructNoteContent(note: Note): Promise<Note> {
    if (note.containedNotes.length === 0) return note

    const childContents = await this.loadChildNotes(note.containedNotes)
    const fullContent = [
      note.content,
      ...childContents.map((c) => c.content),
    ].join('\n')
    return { ...note, content: fullContent }
  }

  // --- Write operations ---

  async saveNoteWithChildren(
    noteId: string,
    trackedLines: NoteLine[],
  ): Promise<Map<number, string>> {
    const userId = this.requireUserId()
    const parentRef = this.noteRef(noteId)
    const parentContent = trackedLines[0]?.content ?? ''

    return runTransaction(this.db, async (transaction) => {
      const oldChildIds = this.getExistingChildIds(transaction, parentRef)
      const idsToDelete = new Set(
        (await oldChildIds).filter((id) => id !== ''),
      )

      // Drop trailing empty lines before saving
      const childLines = dropLastWhile(trackedLines.slice(1), (l) => l.content === '')

      const { containedNotes, createdIds } = await this.processChildLines(
        transaction,
        userId,
        noteId,
        childLines,
        idsToDelete,
      )

      this.updateParentNote(transaction, parentRef, userId, parentContent, containedNotes)
      this.softDeleteRemovedNotes(transaction, idsToDelete)

      return createdIds
    })
  }

  private async getExistingChildIds(
    transaction: Transaction,
    parentRef: DocumentReference,
  ): Promise<string[]> {
    const snapshot = await transaction.get(parentRef)
    if (!snapshot.exists()) return []
    return (snapshot.data().containedNotes as string[]) ?? []
  }

  private processChildLines(
    transaction: Transaction,
    userId: string,
    parentNoteId: string,
    childLines: NoteLine[],
    idsToDelete: Set<string>,
  ): { containedNotes: string[]; createdIds: Map<number, string> } {
    const containedNotes: string[] = []
    const createdIds = new Map<number, string>()

    childLines.forEach((line, index) => {
      const lineIndex = index + 1
      const childId = this.processChildLine(
        transaction, userId, parentNoteId, line, idsToDelete,
      )
      containedNotes.push(childId)
      if (line.noteId == null && line.content !== '') {
        createdIds.set(lineIndex, childId)
      }
    })

    return { containedNotes, createdIds }
  }

  private processChildLine(
    transaction: Transaction,
    userId: string,
    parentNoteId: string,
    line: NoteLine,
    idsToDelete: Set<string>,
  ): string {
    if (line.content === '') return ''

    if (line.noteId != null) {
      this.updateExistingChild(transaction, line.noteId, line.content)
      idsToDelete.delete(line.noteId)
      return line.noteId
    }
    return this.createNewChild(transaction, userId, parentNoteId, line.content)
  }

  private updateExistingChild(
    transaction: Transaction,
    noteId: string,
    content: string,
  ) {
    transaction.set(
      this.noteRef(noteId),
      { content, updatedAt: serverTimestamp() },
      { merge: true },
    )
  }

  private createNewChild(
    transaction: Transaction,
    userId: string,
    parentNoteId: string,
    content: string,
  ): string {
    const newRef = doc(this.notesRef)
    transaction.set(newRef, this.newNoteData(userId, content, parentNoteId))
    return newRef.id
  }

  private updateParentNote(
    transaction: Transaction,
    parentRef: DocumentReference,
    userId: string,
    content: string,
    containedNotes: string[],
  ) {
    transaction.set(
      parentRef,
      { ...this.baseNoteData(userId, content), containedNotes },
      { merge: true },
    )
  }

  private softDeleteRemovedNotes(
    transaction: Transaction,
    idsToDelete: Set<string>,
  ) {
    for (const id of idsToDelete) {
      transaction.update(this.noteRef(id), {
        state: 'deleted',
        updatedAt: serverTimestamp(),
      })
    }
  }

  async createNote(): Promise<string> {
    const userId = this.requireUserId()
    const ref = await addDoc(this.notesRef, this.newNoteData(userId, ''))
    return ref.id
  }

  async createMultiLineNote(content: string): Promise<string> {
    const userId = this.requireUserId()
    const lines = content.split('\n')
    const firstLine = lines[0] ?? ''
    const childLines = lines.slice(1)

    const batch = writeBatch(this.db)
    const parentRef = doc(this.notesRef)

    const childIds = childLines.map((line) => {
      if (line !== '') {
        const childRef = doc(this.notesRef)
        batch.set(childRef, this.newNoteData(userId, line, parentRef.id))
        return childRef.id
      }
      return ''
    })

    batch.set(parentRef, {
      ...this.newNoteData(userId, firstLine),
      containedNotes: childIds,
    })
    await batch.commit()
    return parentRef.id
  }

  async softDeleteNote(noteId: string): Promise<void> {
    this.requireUserId()
    await updateDoc(this.noteRef(noteId), {
      state: 'deleted',
      updatedAt: serverTimestamp(),
    })
  }

  async undeleteNote(noteId: string): Promise<void> {
    this.requireUserId()
    await updateDoc(this.noteRef(noteId), {
      state: null,
      updatedAt: serverTimestamp(),
    })
  }

  async updateLastAccessed(noteId: string): Promise<void> {
    this.requireUserId()
    await updateDoc(this.noteRef(noteId), {
      lastAccessedAt: serverTimestamp(),
    })
  }

  async saveNoteWithFullContent(noteId: string, newContent: string): Promise<void> {
    this.requireUserId()

    const existingNote = await this.loadNoteById(noteId)
    if (!existingNote) throw new Error(`Note not found: ${noteId}`)

    const existingLines = await this.buildExistingLines(noteId, existingNote)
    const newLinesContent = newContent.split('\n')
    const trackedLines = matchLinesToIds(noteId, existingLines, newLinesContent)

    await this.saveNoteWithChildren(noteId, trackedLines)
  }

  private async buildExistingLines(noteId: string, note: Note): Promise<NoteLine[]> {
    const lines: NoteLine[] = [{ content: note.content, noteId }]
    if (note.containedNotes.length > 0) {
      const childLines = await this.loadChildNotes(note.containedNotes)
      lines.push(...childLines)
    }
    return lines
  }

  async isNoteDeleted(noteId: string): Promise<boolean> {
    this.requireUserId()
    const docSnap = await getDoc(this.noteRef(noteId))
    if (!docSnap.exists()) return false
    return docSnap.data().state === 'deleted'
  }
}

// --- Utility functions ---

function dropLastWhile<T>(arr: T[], predicate: (item: T) => boolean): T[] {
  let end = arr.length
  while (end > 0 && predicate(arr[end - 1]!)) {
    end--
  }
  return arr.slice(0, end)
}

/**
 * Two-phase line matching algorithm:
 * 1. Exact content matches (preserves IDs when lines are reordered)
 * 2. Positional fallback (for modified lines)
 */
export function matchLinesToIds(
  parentNoteId: string,
  existingLines: NoteLine[],
  newLinesContent: string[],
): NoteLine[] {
  if (existingLines.length === 0) {
    return newLinesContent.map((content, index) => ({
      content,
      noteId: index === 0 ? parentNoteId : null,
    }))
  }

  // Map content to list of indices in existing lines
  const contentToOldIndices = new Map<string, number[]>()
  existingLines.forEach((line, index) => {
    const indices = contentToOldIndices.get(line.content)
    if (indices) {
      indices.push(index)
    } else {
      contentToOldIndices.set(line.content, [index])
    }
  })

  const newIds: (string | null)[] = new Array(newLinesContent.length).fill(null) as (string | null)[]
  const oldConsumed = new Array(existingLines.length).fill(false) as boolean[]

  // Phase 1: Exact matches
  newLinesContent.forEach((content, index) => {
    const indices = contentToOldIndices.get(content)
    if (indices && indices.length > 0) {
      const oldIdx = indices.shift()!
      newIds[index] = existingLines[oldIdx]!.noteId
      oldConsumed[oldIdx] = true
    }
  })

  // Phase 2: Positional matches for modifications
  newLinesContent.forEach((_, index) => {
    if (newIds[index] == null) {
      if (index < existingLines.length && !oldConsumed[index]) {
        newIds[index] = existingLines[index]!.noteId
        oldConsumed[index] = true
      }
    }
  })

  const trackedLines = newLinesContent.map((content, index) => ({
    content,
    noteId: newIds[index] ?? null,
  }))

  // Ensure first line always has parent ID
  if (trackedLines.length > 0 && trackedLines[0]!.noteId !== parentNoteId) {
    trackedLines[0] = { ...trackedLines[0]!, noteId: parentNoteId }
  }

  return trackedLines
}
