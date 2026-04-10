import type { DslValue } from './DslValue'
import { deserializeValue, serializeValue } from './DslValue'
import type { OnceCache } from './OnceCache'

/**
 * OnceCache backed by Firestore-persisted data.
 *
 * Loads initial entries from a note's onceCache field, then tracks
 * any new entries added during execution. After execution, newEntries()
 * returns only the entries that need to be written back to Firestore.
 */
export class PersistableOnceCache implements OnceCache {
  private readonly cache = new Map<string, DslValue>()
  private readonly persistedKeys = new Set<string>()

  constructor(persisted: Record<string, Record<string, unknown>>) {
    for (const [key, serialized] of Object.entries(persisted)) {
      try {
        const value = deserializeValue(serialized)
        this.cache.set(key, value)
        this.persistedKeys.add(key)
      } catch {
        // Skip entries that can't be deserialized (e.g., schema change)
      }
    }
  }

  get(key: string): DslValue | null {
    return this.cache.get(key) ?? null
  }

  put(key: string, value: DslValue): void {
    this.cache.set(key, value)
  }

  contains(key: string): boolean {
    return this.cache.has(key)
  }

  /**
   * Returns entries that were added during execution (not already persisted).
   * Each entry is a serialized DslValue record ready for Firestore storage.
   */
  newEntries(): Record<string, Record<string, unknown>> {
    const result: Record<string, Record<string, unknown>> = {}
    for (const [key, value] of this.cache) {
      if (!this.persistedKeys.has(key)) {
        result[key] = serializeValue(value)
      }
    }
    return result
  }

  /**
   * Whether there are new entries that need to be persisted.
   */
  hasNewEntries(): boolean {
    for (const key of this.cache.keys()) {
      if (!this.persistedKeys.has(key)) return true
    }
    return false
  }
}
