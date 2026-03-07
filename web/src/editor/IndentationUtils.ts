import type { LineState } from './LineState'

export function getIndentLevelFromText(text: string): number {
  if (text.length === 0) return 0
  let count = 0
  while (count < text.length && text[count] === '\t') count++
  return count
}

export function getIndentLevel(lines: LineState[], lineIndex: number): number {
  const text = lines[lineIndex]?.text
  if (text == null) return 0
  return getIndentLevelFromText(text)
}

export function getLogicalBlock(lines: LineState[], startIndex: number): [number, number] {
  if (startIndex < 0 || startIndex >= lines.length) return [startIndex, startIndex]
  const startIndent = getIndentLevel(lines, startIndex)
  let endIndex = startIndex
  for (let i = startIndex + 1; i < lines.length; i++) {
    if (getIndentLevel(lines, i) <= startIndent) break
    endIndex = i
  }
  return [startIndex, endIndex]
}
