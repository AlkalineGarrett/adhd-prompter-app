import { describe, it, expect } from 'vitest'
import { PersistableOnceCache } from '../../../dsl/runtime/PersistableOnceCache'
import { numberVal, stringVal, dateVal } from '../../../dsl/runtime/DslValue'

describe('PersistableOnceCache', () => {
  it('loads persisted entries on construction', () => {
    const persisted = {
      key1: { type: 'number', value: 42 },
      key2: { type: 'string', value: 'hello' },
    }
    const cache = new PersistableOnceCache(persisted)

    expect(cache.get('key1')).toEqual(numberVal(42))
    expect(cache.get('key2')).toEqual(stringVal('hello'))
    expect(cache.contains('key1')).toBe(true)
    expect(cache.contains('key2')).toBe(true)
  })

  it('returns null for missing keys', () => {
    const cache = new PersistableOnceCache({})
    expect(cache.get('nonexistent')).toBeNull()
    expect(cache.contains('nonexistent')).toBe(false)
  })

  it('tracks new entries separately from persisted', () => {
    const persisted = {
      existing: { type: 'number', value: 1 },
    }
    const cache = new PersistableOnceCache(persisted)

    expect(cache.hasNewEntries()).toBe(false)
    expect(Object.keys(cache.newEntries())).toHaveLength(0)

    cache.put('new-key', dateVal('2026-04-09'))

    expect(cache.hasNewEntries()).toBe(true)
    const entries = cache.newEntries()
    expect(Object.keys(entries)).toHaveLength(1)
    expect(entries['new-key']).toBeDefined()
    expect(entries['new-key']!.type).toBe('date')

    // Persisted entry is NOT in newEntries
    expect(entries['existing']).toBeUndefined()
  })

  it('overwriting persisted entry does not create new entry', () => {
    const persisted = {
      key: { type: 'number', value: 1 },
    }
    const cache = new PersistableOnceCache(persisted)

    cache.put('key', numberVal(2))

    expect(cache.hasNewEntries()).toBe(false)
  })

  it('skips entries that fail deserialization', () => {
    const persisted = {
      good: { type: 'number', value: 42 },
      bad: { type: 'unknown_type_xyz', value: '???' },
    }
    const cache = new PersistableOnceCache(persisted)

    expect(cache.get('good')).toEqual(numberVal(42))
    expect(cache.get('bad')).toBeNull()
  })

  it('per-line scoped keys work correctly', () => {
    const persisted = {
      'noteA:CALL(date,)': { type: 'date', value: '2026-04-08' },
      'noteB:CALL(date,)': { type: 'date', value: '2026-04-09' },
    }
    const cache = new PersistableOnceCache(persisted)

    expect(cache.get('noteA:CALL(date,)')).toEqual(dateVal('2026-04-08'))
    expect(cache.get('noteB:CALL(date,)')).toEqual(dateVal('2026-04-09'))
  })
})
