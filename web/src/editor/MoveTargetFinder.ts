import type { LineState } from './LineState'
import type { EditorSelection } from './EditorSelection'
import * as SC from './SelectionCoordinates'
import * as IU from './IndentationUtils'

export function findMoveUpTarget(lines: LineState[], rangeFirst: number, rangeLast: number): number | null {
  void rangeLast
  if (rangeFirst <= 0) return null
  const firstIndent = IU.getIndentLevel(lines, rangeFirst)
  let target = rangeFirst - 1

  while (target > 0 && IU.getIndentLevel(lines, target) > firstIndent) {
    target--
  }

  return target
}

export function findMoveDownTarget(lines: LineState[], rangeFirst: number, rangeLast: number): number | null {
  if (rangeLast >= lines.length - 1) return null
  const firstIndent = IU.getIndentLevel(lines, rangeFirst)
  let target = rangeLast + 1
  const targetIndent = IU.getIndentLevel(lines, target)

  if (targetIndent <= firstIndent) {
    while (target < lines.length - 1 && IU.getIndentLevel(lines, target + 1) > targetIndent) {
      target++
    }
  }

  return target + 1
}

export function getMoveTarget(
  lines: LineState[],
  hasSel: boolean,
  selection: EditorSelection,
  focusedLineIndex: number,
  moveUp: boolean,
): number | null {
  let rangeFirst: number, rangeLast: number
  if (hasSel) {
    ;[rangeFirst, rangeLast] = SC.getSelectedLineRange(lines, selection, focusedLineIndex)
  } else {
    ;[rangeFirst, rangeLast] = IU.getLogicalBlock(lines, focusedLineIndex)
  }

  if (hasSel) {
    let shallowest = Infinity
    for (let i = rangeFirst; i <= rangeLast; i++) {
      shallowest = Math.min(shallowest, IU.getIndentLevel(lines, i))
    }
    const firstIndent = IU.getIndentLevel(lines, rangeFirst)
    if (firstIndent > shallowest) {
      if (moveUp) {
        return rangeFirst > 0 ? rangeFirst - 1 : null
      }
      return rangeLast < lines.length - 1 ? rangeLast + 2 : null
    }
  }

  return moveUp
    ? findMoveUpTarget(lines, rangeFirst, rangeLast)
    : findMoveDownTarget(lines, rangeFirst, rangeLast)
}

export function wouldOrphanChildren(
  lines: LineState[],
  hasSel: boolean,
  selection: EditorSelection,
  focusedLineIndex: number,
): boolean {
  if (!hasSel) return false
  const [selFirst, selLast] = SC.getSelectedLineRange(lines, selection, focusedLineIndex)

  let shallowest = Infinity
  for (let i = selFirst; i <= selLast; i++) {
    shallowest = Math.min(shallowest, IU.getIndentLevel(lines, i))
  }
  const firstIndent = IU.getIndentLevel(lines, selFirst)
  if (firstIndent > shallowest) return true

  const [, blockLast] = IU.getLogicalBlock(lines, selFirst)
  return selLast < blockLast
}
