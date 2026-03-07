import { describe, it, expect } from 'vitest'
import { Lexer } from '../../../dsl/language/Lexer'
import { TokenType } from '../../../dsl/language/Token'

function tokenTypes(source: string): TokenType[] {
  return new Lexer(source).tokenize().map((t) => t.type)
}

describe('Lexer - comments', () => {
  it('should skip comment to end of line', () => {
    expect(tokenTypes('42 # this is a comment')).toEqual([
      TokenType.NUMBER,
      TokenType.EOF,
    ])
  })

  it('should handle comment at start of input', () => {
    expect(tokenTypes('# comment only')).toEqual([TokenType.EOF])
  })

  it('should handle comment with tokens after newline', () => {
    expect(tokenTypes('42 # comment\n"hello"')).toEqual([
      TokenType.NUMBER,
      TokenType.STRING,
      TokenType.EOF,
    ])
  })

  it('should handle multiple comment lines', () => {
    expect(tokenTypes('# first\n# second\n42')).toEqual([
      TokenType.NUMBER,
      TokenType.EOF,
    ])
  })

  it('should not treat # inside string as comment', () => {
    const tokens = new Lexer('"has # inside"').tokenize()
    expect(tokens[0]!.type).toBe(TokenType.STRING)
    expect(tokens[0]!.literal).toBe('has # inside')
  })

  it('should handle comment at end of input with no newline', () => {
    expect(tokenTypes('foo # end')).toEqual([
      TokenType.IDENTIFIER,
      TokenType.EOF,
    ])
  })
})
