import { LineState } from './LineState'
import type { EditorSelection } from './EditorSelection'
import { hasSelection } from './EditorSelection'
import * as SC from './SelectionCoordinates'

export interface MoveResult {
  newLines: string[]
  newFocusedLineIndex: number
  newSelection: EditorSelection | null
  newRange: [number, number]
}

export function calculateMove(
  lines: LineState[],
  sourceFirst: number,
  sourceLast: number,
  targetIndex: number,
  focusedLineIndex: number,
  selection: EditorSelection | null,
): MoveResult | null {
  if (sourceFirst < 0 || sourceLast >= lines.length) return null
  if (targetIndex < 0 || targetIndex > lines.length) return null
  if (targetIndex >= sourceFirst && targetIndex <= sourceLast + 1) return null

  const moveCount = sourceLast - sourceFirst + 1

  // Capture selection info before modifying
  let selStartLine = -1, selStartLocal = 0
  let selEndLine = -1, selEndLocal = 0
  if (selection && hasSelection(selection)) {
    ;[selStartLine, selStartLocal] = SC.getLineAndLocalOffset(lines, selection.start)
    ;[selEndLine, selEndLocal] = SC.getLineAndLocalOffset(lines, selection.end)
  }

  const linesToMove = []
  for (let i = sourceFirst; i <= sourceLast; i++) {
    linesToMove.push(lines[i]!.text)
  }

  // Build new lines list (without source range)
  const withoutSource: string[] = []
  for (let i = 0; i < lines.length; i++) {
    if (i < sourceFirst || i > sourceLast) {
      withoutSource.push(lines[i]!.text)
    }
  }

  const adjustedTarget = targetIndex > sourceFirst ? targetIndex - moveCount : targetIndex

  // Insert moved lines
  const newLines = [...withoutSource]
  for (let i = 0; i < linesToMove.length; i++) {
    newLines.splice(adjustedTarget + i, 0, linesToMove[i]!)
  }

  const newRangeStart = adjustedTarget
  const newRangeEnd = adjustedTarget + moveCount - 1

  // Adjust focused line
  let newFocusedLineIndex: number
  if (focusedLineIndex >= sourceFirst && focusedLineIndex <= sourceLast) {
    newFocusedLineIndex = adjustedTarget + (focusedLineIndex - sourceFirst)
  } else if (targetIndex <= focusedLineIndex && sourceFirst > focusedLineIndex) {
    newFocusedLineIndex = focusedLineIndex + moveCount
  } else if (sourceFirst <= focusedLineIndex && targetIndex > focusedLineIndex) {
    newFocusedLineIndex = focusedLineIndex - moveCount
  } else {
    newFocusedLineIndex = focusedLineIndex
  }

  // Calculate new selection
  let newSelection: EditorSelection | null = null
  if (selection && hasSelection(selection)) {
    const newStartLine = adjustLineIndexForMove(selStartLine, sourceFirst, sourceLast, adjustedTarget, moveCount)

    let newEndLine: number
    let newEndLocal: number
    if (selEndLocal === 0 && selEndLine > 0) {
      const adjustedPrevLine = adjustLineIndexForMove(selEndLine - 1, sourceFirst, sourceLast, adjustedTarget, moveCount)
      newEndLine = adjustedPrevLine + 1
      newEndLocal = 0
    } else {
      newEndLine = adjustLineIndexForMove(selEndLine, sourceFirst, sourceLast, adjustedTarget, moveCount)
      newEndLocal = selEndLocal
    }

    const tempLines = newLines.map((t) => new LineState(t))
    const newSelStart = SC.getLineStartOffset(tempLines, newStartLine) +
      Math.min(selStartLocal, tempLines[newStartLine]?.text.length ?? 0)
    const newSelEnd = SC.getLineStartOffset(tempLines, newEndLine) +
      Math.min(newEndLocal, tempLines[newEndLine]?.text.length ?? 0)

    newSelection = { start: newSelStart, end: newSelEnd }
  }

  return {
    newLines,
    newFocusedLineIndex,
    newSelection,
    newRange: [newRangeStart, newRangeEnd],
  }
}

export function adjustLineIndexForMove(
  lineIndex: number,
  sourceFirst: number,
  sourceLast: number,
  targetIndex: number,
  moveCount: number,
): number {
  if (lineIndex >= sourceFirst && lineIndex <= sourceLast) {
    return targetIndex + (lineIndex - sourceFirst)
  }
  if (targetIndex <= lineIndex && sourceFirst > lineIndex) {
    return lineIndex + moveCount
  }
  if (sourceFirst <= lineIndex && targetIndex > lineIndex) {
    return lineIndex - moveCount
  }
  return lineIndex
}
