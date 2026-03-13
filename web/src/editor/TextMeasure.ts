/** Measures text width using a canvas context for fast, accurate results. */
let measureCanvas: HTMLCanvasElement | null = null

function getContext(font: string): CanvasRenderingContext2D {
  if (!measureCanvas) measureCanvas = document.createElement('canvas')
  const ctx = measureCanvas.getContext('2d')!
  ctx.font = font
  return ctx
}

/** Returns the character index in `text` closest to `x` pixels from the left. */
export function getCharIndexAtX(text: string, x: number, font: string): number {
  if (text.length === 0 || x <= 0) return 0
  const ctx = getContext(font)
  const fullWidth = ctx.measureText(text).width
  if (x >= fullWidth) return text.length

  // Binary search for the closest character boundary
  let lo = 0
  let hi = text.length
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    const midWidth = ctx.measureText(text.substring(0, mid + 1)).width
    if (midWidth <= x) {
      lo = mid + 1
    } else {
      // Check if we're closer to mid or mid+1
      const prevWidth = mid > 0 ? ctx.measureText(text.substring(0, mid)).width : 0
      if (x - prevWidth < midWidth - x) {
        hi = mid
      } else {
        lo = mid + 1
        hi = lo
      }
    }
  }
  return lo
}

/** Returns the pixel X offset for a given character index in `text`. */
export function getXAtCharIndex(text: string, charIndex: number, font: string): number {
  if (charIndex <= 0 || text.length === 0) return 0
  const ctx = getContext(font)
  return ctx.measureText(text.substring(0, charIndex)).width
}

/** Returns [start, end) bounds of the word at the given character index. */
export function getWordBoundsAt(text: string, charIndex: number): [number, number] {
  const isWordChar = (c: string) => /\w/.test(c)
  const idx = Math.min(charIndex, text.length - 1)
  if (idx < 0) return [0, 0]

  // If clicked on non-word char, select just that character
  if (!isWordChar(text[idx]!)) {
    return [idx, idx + 1]
  }

  let start = idx
  while (start > 0 && isWordChar(text[start - 1]!)) start--
  let end = idx
  while (end < text.length && isWordChar(text[end]!)) end++
  return [start, end]
}

/** Gets the computed font string from an element (for use with canvas measurement). */
export function getComputedFont(element: HTMLElement): string {
  const style = getComputedStyle(element)
  return `${style.fontStyle} ${style.fontWeight} ${style.fontSize} ${style.fontFamily}`
}
