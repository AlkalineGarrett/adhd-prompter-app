/**
 * Content similarity utilities for matching edited lines to their originals.
 *
 * Used by line matching algorithms (NoteRepository.matchLinesToIds, EditorState.updateFromText)
 * to assign note IDs to the line fragment with the highest proportion of original content
 * after a line split.
 */

/**
 * Returns the proportion of oldContent that appears in newContent,
 * measured by longest common subsequence length / old content length.
 * Returns 0 early if the strings share no characters.
 */
export function contentOverlapProportion(oldContent: string, newContent: string): number {
  if (oldContent.length === 0) return 0
  if (!sharesAnyCharacter(oldContent, newContent)) return 0
  return lcsLength(oldContent, newContent) / oldContent.length
}

function sharesAnyCharacter(a: string, b: string): boolean {
  const charSet = new Set(a)
  for (const ch of b) {
    if (charSet.has(ch)) return true
  }
  return false
}

/**
 * Computes the length of the longest common subsequence between two strings.
 * Space-optimized to O(min(m, n)).
 */
export function lcsLength(a: string, b: string): number {
  if (a.length === 0 || b.length === 0) return 0
  const m = a.length
  const n = b.length
  let prev = new Array<number>(n + 1).fill(0)
  let curr = new Array<number>(n + 1).fill(0)
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      curr[j] =
        a[i - 1] === b[j - 1] ? prev[j - 1]! + 1 : Math.max(prev[j]!, curr[j - 1]!)
    }
    const temp = prev
    prev = curr
    curr = temp
    curr.fill(0)
  }
  return prev[n]!
}

/**
 * Matches unmatched new lines to unconsumed old lines by content similarity.
 *
 * For each (old, new) pair, computes the proportion of old content present in the
 * new content (via LCS). Greedily assigns the highest-proportion match first, ensuring
 * that when a line is split, the fragment with more original content keeps the ID.
 */
export function performSimilarityMatching(
  unmatchedNewIndices: Set<number>,
  unconsumedOldIndices: number[],
  getOldContent: (idx: number) => string,
  getNewContent: (idx: number) => string,
  onMatch: (oldIdx: number, newIdx: number) => void,
): void {
  interface Match {
    oldIdx: number
    newIdx: number
    proportion: number
  }

  const candidates: Match[] = []
  for (const oldIdx of unconsumedOldIndices) {
    const oldContent = getOldContent(oldIdx)
    for (const newIdx of unmatchedNewIndices) {
      const proportion = contentOverlapProportion(oldContent, getNewContent(newIdx))
      if (proportion > 0) {
        candidates.push({ oldIdx, newIdx, proportion })
      }
    }
  }
  candidates.sort((a, b) => b.proportion - a.proportion)
  const matchedOld = new Set<number>()
  const matchedNew = new Set<number>()
  for (const match of candidates) {
    if (matchedOld.has(match.oldIdx) || matchedNew.has(match.newIdx)) continue
    onMatch(match.oldIdx, match.newIdx)
    matchedOld.add(match.oldIdx)
    matchedNew.add(match.newIdx)
  }
}

/**
 * Determines which half of a split line should keep the noteIds.
 *
 * @returns [currentLineNoteIds, newLineNoteIds]
 */
export function splitNoteIds(
  noteIds: string[],
  beforeContentLen: number,
  afterContentLen: number,
  beforeHasContent: boolean,
  afterHasContent: boolean,
): [string[], string[]] {
  if (!beforeHasContent && afterHasContent) return [[], noteIds]
  if (beforeHasContent && !afterHasContent) return [noteIds, []]
  return beforeContentLen >= afterContentLen ? [noteIds, []] : [[], noteIds]
}
