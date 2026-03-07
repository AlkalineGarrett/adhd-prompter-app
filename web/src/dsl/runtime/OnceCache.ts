import type { DslValue } from './DslValue'

/**
 * Cache for `once[...]` expression results.
 */
export interface OnceCache {
  get(key: string): DslValue | null
  put(key: string, value: DslValue): void
  contains(key: string): boolean
}

/**
 * In-memory implementation of OnceCache.
 */
export class InMemoryOnceCache implements OnceCache {
  private readonly cache = new Map<string, DslValue>()

  get(key: string): DslValue | null {
    return this.cache.get(key) ?? null
  }

  put(key: string, value: DslValue): void {
    this.cache.set(key, value)
  }

  contains(key: string): boolean {
    return this.cache.has(key)
  }
}
