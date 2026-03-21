import { describe, it, expect } from 'vitest'
import {
  createDirectiveInstance,
  matchDirectiveInstances,
  parseAllDirectiveLocations,
} from '../../../dsl/directives/DirectiveInstance'
import type { DirectiveInstance, ParsedDirectiveLocation } from '../../../dsl/directives/DirectiveInstance'

function loc(lineIndex: number, startOffset: number, sourceText: string, noteId?: string): ParsedDirectiveLocation {
  return { lineIndex, startOffset, sourceText, noteId }
}

function instance(uuid: string, lineIndex: number, startOffset: number, sourceText: string, noteId?: string): DirectiveInstance {
  return { uuid, lineIndex, startOffset, sourceText, noteId }
}

describe('createDirectiveInstance', () => {
  it('creates instance with unique UUID', () => {
    const a = createDirectiveInstance(0, 0, '[test]')
    const b = createDirectiveInstance(0, 0, '[test]')
    expect(a.uuid).not.toBe(b.uuid)
    expect(a.lineIndex).toBe(0)
    expect(a.startOffset).toBe(0)
    expect(a.sourceText).toBe('[test]')
  })
})

describe('matchDirectiveInstances', () => {
  it('returns empty for empty new directives', () => {
    const existing = [instance('uuid-1', 0, 0, '[a]')]
    const result = matchDirectiveInstances(existing, [])
    expect(result).toEqual([])
  })

  it('creates new UUIDs when no existing instances', () => {
    const result = matchDirectiveInstances([], [loc(0, 0, '[a]'), loc(1, 0, '[b]')])
    expect(result).toHaveLength(2)
    expect(result[0]!.sourceText).toBe('[a]')
    expect(result[1]!.sourceText).toBe('[b]')
    expect(result[0]!.uuid).not.toBe(result[1]!.uuid)
  })

  it('exact match preserves UUID', () => {
    const existing = [instance('uuid-1', 0, 5, '[foo]')]
    const result = matchDirectiveInstances(existing, [loc(0, 5, '[foo]')])
    expect(result).toHaveLength(1)
    expect(result[0]!.uuid).toBe('uuid-1')
    expect(result[0]!.lineIndex).toBe(0)
    expect(result[0]!.startOffset).toBe(5)
  })

  it('same line shift preserves UUID', () => {
    const existing = [instance('uuid-1', 0, 5, '[foo]')]
    // Same line, same text, different offset
    const result = matchDirectiveInstances(existing, [loc(0, 10, '[foo]')])
    expect(result).toHaveLength(1)
    expect(result[0]!.uuid).toBe('uuid-1')
    expect(result[0]!.startOffset).toBe(10)
  })

  it('line move preserves UUID when unique candidate', () => {
    const existing = [instance('uuid-1', 0, 0, '[foo]')]
    // Moved to different line
    const result = matchDirectiveInstances(existing, [loc(2, 0, '[foo]')])
    expect(result).toHaveLength(1)
    expect(result[0]!.uuid).toBe('uuid-1')
    expect(result[0]!.lineIndex).toBe(2)
  })

  it('generates new UUID for unmatched directive', () => {
    const existing = [instance('uuid-1', 0, 0, '[foo]')]
    const result = matchDirectiveInstances(existing, [loc(0, 0, '[bar]')])
    expect(result).toHaveLength(1)
    expect(result[0]!.uuid).not.toBe('uuid-1')
    expect(result[0]!.sourceText).toBe('[bar]')
  })

  it('handles mix of matched and unmatched', () => {
    const existing = [
      instance('uuid-1', 0, 0, '[a]'),
      instance('uuid-2', 1, 0, '[b]'),
    ]
    const newDirs = [loc(0, 0, '[a]'), loc(1, 0, '[c]')]
    const result = matchDirectiveInstances(existing, newDirs)
    expect(result).toHaveLength(2)

    const matchedA = result.find((r) => r.sourceText === '[a]')
    expect(matchedA!.uuid).toBe('uuid-1')

    const matchedC = result.find((r) => r.sourceText === '[c]')
    expect(matchedC!.uuid).not.toBe('uuid-1')
    expect(matchedC!.uuid).not.toBe('uuid-2')
  })

  it('does not reuse UUID for ambiguous line move', () => {
    // Two existing directives with same text on different lines
    const existing = [
      instance('uuid-1', 0, 0, '[foo]'),
      instance('uuid-2', 1, 0, '[foo]'),
    ]
    // One new directive with same text on a different line
    const result = matchDirectiveInstances(existing, [loc(3, 0, '[foo]')])
    // Should not match because there are 2 candidates (not unique)
    expect(result).toHaveLength(1)
    expect(result[0]!.uuid).not.toBe('uuid-1')
    expect(result[0]!.uuid).not.toBe('uuid-2')
  })

  it('prioritizes exact match over same-line shift', () => {
    const existing = [
      instance('uuid-exact', 0, 5, '[foo]'),
      instance('uuid-shift', 0, 10, '[foo]'),
    ]
    const result = matchDirectiveInstances(existing, [loc(0, 5, '[foo]'), loc(0, 10, '[foo]')])
    expect(result).toHaveLength(2)

    const atOffset5 = result.find((r) => r.startOffset === 5)
    const atOffset10 = result.find((r) => r.startOffset === 10)
    expect(atOffset5!.uuid).toBe('uuid-exact')
    expect(atOffset10!.uuid).toBe('uuid-shift')
  })
})

describe('parseAllDirectiveLocations', () => {
  it('parses directives from multiline content', () => {
    const content = 'line0 [a]\nline1 [b]\nline2'
    const result = parseAllDirectiveLocations(content)
    expect(result).toHaveLength(2)
    expect(result[0]).toEqual({ lineIndex: 0, startOffset: 6, sourceText: '[a]' })
    expect(result[1]).toEqual({ lineIndex: 1, startOffset: 6, sourceText: '[b]' })
  })

  it('returns empty for content with no directives', () => {
    const result = parseAllDirectiveLocations('no directives\nhere either')
    expect(result).toEqual([])
  })

  it('handles multiple directives on same line', () => {
    const content = '[a] and [b]'
    const result = parseAllDirectiveLocations(content)
    expect(result).toHaveLength(2)
    expect(result[0]!.lineIndex).toBe(0)
    expect(result[1]!.lineIndex).toBe(0)
    expect(result[0]!.sourceText).toBe('[a]')
    expect(result[1]!.sourceText).toBe('[b]')
  })

  it('handles empty content', () => {
    const result = parseAllDirectiveLocations('')
    expect(result).toEqual([])
  })
})

describe('parseAllDirectiveLocations with lineNoteIds', () => {
  it('assigns noteIds from lineNoteIds', () => {
    const content = 'Line [a]\nLine [b]\nLine [c]'
    const lineNoteIds = ['note1', 'note2', undefined]
    const result = parseAllDirectiveLocations(content, lineNoteIds)

    expect(result).toHaveLength(3)
    expect(result[0]!.noteId).toBe('note1')
    expect(result[1]!.noteId).toBe('note2')
    expect(result[2]!.noteId).toBeUndefined()
  })

  it('uses undefined when lineNoteIds is shorter than content', () => {
    const content = 'Line [a]\nLine [b]'
    const lineNoteIds: (string | undefined)[] = ['note1'] // shorter than 2 lines
    const result = parseAllDirectiveLocations(content, lineNoteIds)

    expect(result).toHaveLength(2)
    expect(result[0]!.noteId).toBe('note1')
    expect(result[1]!.noteId).toBeUndefined()
  })
})

describe('matchDirectiveInstances noteId preservation', () => {
  it('preserves noteId on exact match', () => {
    const existing = [instance('uuid1', 0, 5, '[test]', 'oldNote')]
    const newDirs = [loc(0, 5, '[test]', 'newNote')]
    const result = matchDirectiveInstances(existing, newDirs)

    expect(result).toHaveLength(1)
    expect(result[0]!.uuid).toBe('uuid1')
    expect(result[0]!.noteId).toBe('newNote') // updated to new noteId
  })

  it('preserves noteId on same-line shift', () => {
    const existing = [instance('uuid1', 0, 5, '[test]', 'note1')]
    const newDirs = [loc(0, 10, '[test]', 'note1')]
    const result = matchDirectiveInstances(existing, newDirs)

    expect(result).toHaveLength(1)
    expect(result[0]!.uuid).toBe('uuid1')
    expect(result[0]!.noteId).toBe('note1')
    expect(result[0]!.startOffset).toBe(10)
  })

  it('preserves noteId on line move', () => {
    const existing = [instance('uuid1', 0, 5, '[test]', 'note1')]
    const newDirs = [loc(3, 5, '[test]', 'note1')]
    const result = matchDirectiveInstances(existing, newDirs)

    expect(result).toHaveLength(1)
    expect(result[0]!.uuid).toBe('uuid1')
    expect(result[0]!.noteId).toBe('note1')
    expect(result[0]!.lineIndex).toBe(3)
  })

  it('sets noteId on new instances', () => {
    const result = matchDirectiveInstances([], [loc(0, 0, '[test]', 'note1')])

    expect(result).toHaveLength(1)
    expect(result[0]!.noteId).toBe('note1')
  })
})
