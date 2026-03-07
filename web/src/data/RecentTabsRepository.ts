import {
  collection,
  doc,
  getDocs,
  setDoc,
  deleteDoc,
  updateDoc,
  query,
  orderBy,
  limit,
  writeBatch,
  serverTimestamp,
  type Firestore,
} from 'firebase/firestore'
import type { Auth } from 'firebase/auth'
import type { Timestamp } from 'firebase/firestore'

export interface RecentTab {
  noteId: string
  displayText: string
  lastAccessedAt: Timestamp | null
}

const MAX_TABS = 5

export class RecentTabsRepository {
  constructor(
    private readonly db: Firestore,
    private readonly auth: Auth,
  ) {}

  private requireUserId(): string {
    const uid = this.auth.currentUser?.uid
    if (!uid) throw new Error('User not signed in')
    return uid
  }

  private openTabsCollection(userId: string) {
    return collection(this.db, 'users', userId, 'openTabs')
  }

  private tabRef(userId: string, noteId: string) {
    return doc(this.openTabsCollection(userId), noteId)
  }

  async addOrUpdateTab(noteId: string, displayText: string): Promise<void> {
    const userId = this.requireUserId()
    await setDoc(this.tabRef(userId, noteId), {
      noteId,
      displayText,
      lastAccessedAt: serverTimestamp(),
    })
    await this.enforceTabLimit(userId)
  }

  async getOpenTabs(): Promise<RecentTab[]> {
    const userId = this.requireUserId()
    const q = query(
      this.openTabsCollection(userId),
      orderBy('lastAccessedAt', 'desc'),
      limit(MAX_TABS),
    )
    const snapshot = await getDocs(q)
    return snapshot.docs.map((d) => {
      const data = d.data()
      return {
        noteId: (data.noteId as string) ?? '',
        displayText: (data.displayText as string) ?? '',
        lastAccessedAt: (data.lastAccessedAt as Timestamp) ?? null,
      }
    })
  }

  async removeTab(noteId: string): Promise<void> {
    const userId = this.requireUserId()
    await deleteDoc(this.tabRef(userId, noteId))
  }

  async updateTabDisplayText(noteId: string, displayText: string): Promise<void> {
    const userId = this.requireUserId()
    const ref = this.tabRef(userId, noteId)
    try {
      await updateDoc(ref, { displayText })
    } catch {
      // Tab may not exist, ignore
    }
  }

  private async enforceTabLimit(userId: string): Promise<void> {
    const q = query(
      this.openTabsCollection(userId),
      orderBy('lastAccessedAt', 'desc'),
    )
    const snapshot = await getDocs(q)

    if (snapshot.size > MAX_TABS) {
      const batch = writeBatch(this.db)
      snapshot.docs.slice(MAX_TABS).forEach((d) => {
        batch.delete(d.ref)
      })
      await batch.commit()
    }
  }
}
