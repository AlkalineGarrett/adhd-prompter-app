/**
 * Single source of truth for line prefix constants used in bullet lists and checkboxes.
 */

export const BULLET = '• '
export const CHECKBOX_UNCHECKED = '☐ '
export const CHECKBOX_CHECKED = '☑ '

export const ASTERISK_SPACE = '* '
export const BRACKETS_EMPTY = '[]'
export const BRACKETS_CHECKED = '[x]'

export const TAB = '\t'

export const LINE_PREFIXES = [BULLET, CHECKBOX_UNCHECKED, CHECKBOX_CHECKED]

export function getPrefix(lineContent: string): string | null {
  const trimmed = lineContent.replace(/^\t+/, '')
  return LINE_PREFIXES.find((p) => trimmed.startsWith(p)) ?? null
}

export function hasBullet(lineContent: string): boolean {
  return lineContent.replace(/^\t+/, '').startsWith(BULLET)
}

export function hasCheckbox(lineContent: string): boolean {
  const trimmed = lineContent.replace(/^\t+/, '')
  return trimmed.startsWith(CHECKBOX_UNCHECKED) || trimmed.startsWith(CHECKBOX_CHECKED)
}

export function hasAnyPrefix(lineContent: string): boolean {
  return getPrefix(lineContent) != null
}

export function removePrefix(lineContent: string): string {
  const indentation = lineContent.match(/^\t*/)?.[0] ?? ''
  const afterIndent = lineContent.slice(indentation.length)
  const prefix = LINE_PREFIXES.find((p) => afterIndent.startsWith(p))
  if (!prefix) return lineContent
  return indentation + afterIndent.slice(prefix.length)
}

export function addPrefix(lineContent: string, prefix: string): string {
  const indentation = lineContent.match(/^\t*/)?.[0] ?? ''
  const afterIndent = lineContent.slice(indentation.length)
  const existingPrefix = LINE_PREFIXES.find((p) => afterIndent.startsWith(p))
  if (existingPrefix) {
    return indentation + prefix + afterIndent.slice(existingPrefix.length)
  }
  return indentation + prefix + afterIndent
}
