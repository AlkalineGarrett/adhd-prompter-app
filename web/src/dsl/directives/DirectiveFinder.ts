/**
 * A located directive in note content.
 */
export interface FoundDirective {
  sourceText: string
  startOffset: number
  endOffset: number
}

/**
 * Creates a unique key for a directive based on its position.
 */
export function directiveKey(lineIndex: number, startOffset: number): string {
  return `${lineIndex}:${startOffset}`
}

/**
 * Find all directives in the given content.
 * Handles nested brackets for lambda syntax: [lambda[...]]
 */
export function findDirectives(content: string): FoundDirective[] {
  const directives: FoundDirective[] = []
  let i = 0

  while (i < content.length) {
    if (content[i] === '[') {
      const startOffset = i
      let depth = 1
      i++

      while (i < content.length && depth > 0) {
        if (content[i] === '[') depth++
        else if (content[i] === ']') depth--
        i++
      }

      if (depth === 0) {
        directives.push({
          sourceText: content.substring(startOffset, i),
          startOffset,
          endOffset: i,
        })
      }
    } else {
      i++
    }
  }

  return directives
}

/**
 * Check if the given text contains any directives.
 */
export function containsDirectives(content: string): boolean {
  return findDirectives(content).length > 0
}

/**
 * Compute a SHA-256 hash of directive source text.
 */
export async function hashDirective(sourceText: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(sourceText)
  const hashBuffer = await crypto.subtle.digest('SHA-256', data)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
}
