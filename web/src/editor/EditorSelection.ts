export interface EditorSelection {
  start: number
  end: number
}

export const SELECTION_NONE: EditorSelection = { start: -1, end: -1 }

export function selMin(sel: EditorSelection): number {
  return Math.min(sel.start, sel.end)
}

export function selMax(sel: EditorSelection): number {
  return Math.max(sel.start, sel.end)
}

export function hasSelection(sel: EditorSelection): boolean {
  return sel.start !== sel.end && sel.start >= 0
}
