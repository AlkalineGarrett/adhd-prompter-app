import type { Note } from '@/data/Note'
import type { CachedDirectiveResult } from './CachedDirectiveResult'
import * as StalenessChecker from './StalenessChecker'

/**
 * Simple LRU cache using Map insertion order.
 */
class LruCache<V> {
  private readonly cache = new Map<string, V>()

  constructor(private readonly maxSize: number) {}

  get(key: string): V | undefined {
    const value = this.cache.get(key)
    if (value !== undefined) {
      // Move to end (most recently used)
      this.cache.delete(key)
      this.cache.set(key, value)
    }
    return value
  }

  put(key: string, value: V): void {
    this.cache.delete(key)
    if (this.cache.size >= this.maxSize) {
      // Evict oldest (first entry)
      const oldest = this.cache.keys().next().value
      if (oldest !== undefined) this.cache.delete(oldest)
    }
    this.cache.set(key, value)
  }

  remove(key: string): void {
    this.cache.delete(key)
  }

  clear(): void {
    this.cache.clear()
  }

  get size(): number {
    return this.cache.size
  }
}

const DEFAULT_PER_NOTE_CACHE_SIZE = 100
const DEFAULT_MAX_NOTES = 500

/**
 * Per-note directive cache. All directive results are scoped to the note containing them.
 */
class PerNoteDirectiveCache {
  private readonly noteCaches: LruCache<LruCache<CachedDirectiveResult>>

  constructor(
    private readonly maxEntriesPerNote: number = DEFAULT_PER_NOTE_CACHE_SIZE,
    maxTotalNotes: number = DEFAULT_MAX_NOTES,
  ) {
    this.noteCaches = new LruCache(maxTotalNotes)
  }

  get(noteId: string, directiveKey: string): CachedDirectiveResult | undefined {
    return this.noteCaches.get(noteId)?.get(directiveKey)
  }

  put(noteId: string, directiveKey: string, result: CachedDirectiveResult): void {
    let noteCache = this.noteCaches.get(noteId)
    if (!noteCache) {
      noteCache = new LruCache(this.maxEntriesPerNote)
      this.noteCaches.put(noteId, noteCache)
    }
    noteCache.put(directiveKey, result)
  }

  clearNote(noteId: string): void { this.noteCaches.remove(noteId) }
  clear(): void { this.noteCaches.clear() }
}

/**
 * Central manager for all directive caches.
 */
export class DirectiveCacheManager {
  private readonly perNoteCache: PerNoteDirectiveCache

  constructor() {
    this.perNoteCache = new PerNoteDirectiveCache()
  }

  get(directiveKey: string, noteId: string): CachedDirectiveResult | undefined {
    return this.perNoteCache.get(noteId, directiveKey)
  }

  getIfValid(
    directiveKey: string,
    noteId: string,
    currentNotes: Note[],
    currentNote: Note | null,
  ): CachedDirectiveResult | undefined {
    const cached = this.get(directiveKey, noteId)
    if (!cached) return undefined
    if (StalenessChecker.shouldReExecute(cached, currentNotes, currentNote)) return undefined
    return cached
  }

  put(directiveKey: string, noteId: string, result: CachedDirectiveResult): void {
    this.perNoteCache.put(noteId, directiveKey, result)
  }

  clearNote(noteId: string): void {
    this.perNoteCache.clearNote(noteId)
  }

  clearAll(): void {
    this.perNoteCache.clear()
  }
}
