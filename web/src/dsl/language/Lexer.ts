import { TokenType, type Token } from './Token'

export class LexerException extends Error {
  constructor(
    message: string,
    public readonly position: number,
  ) {
    super(`Lexer error at position ${position}: ${message}`)
  }
}

export class Lexer {
  private start = 0
  private current = 0
  private readonly tokens: Token[] = []

  constructor(private readonly source: string) {}

  tokenize(): Token[] {
    while (!this.isAtEnd()) {
      this.start = this.current
      this.scanToken()
    }
    this.tokens.push({ type: TokenType.EOF, lexeme: '', literal: null, position: this.current })
    return this.tokens
  }

  private scanToken(): void {
    const c = this.advance()
    switch (c) {
      case '[': this.addToken(TokenType.LBRACKET); break
      case ']': this.addToken(TokenType.RBRACKET); break
      case '(': this.addToken(TokenType.LPAREN); break
      case ')': this.addToken(TokenType.RPAREN); break
      case ',': this.addToken(TokenType.COMMA); break
      case ':': this.addToken(TokenType.COLON); break
      case ';': this.addToken(TokenType.SEMICOLON); break
      case '*': this.addToken(TokenType.STAR); break
      case '.': this.dotOrDotDot(); break
      case '"': this.string(); break
      case '#': this.skipComment(); break
      case ' ': case '\t': case '\r': case '\n': break // skip whitespace
      default:
        if (isDigit(c)) {
          this.number()
        } else if (isIdentifierStart(c)) {
          this.identifier()
        } else {
          throw new LexerException(`Unexpected character '${c}'`, this.current - 1)
        }
    }
  }

  private dotOrDotDot(): void {
    if (this.peek() === '.') {
      this.advance()
      this.addToken(TokenType.DOTDOT)
    } else {
      this.addToken(TokenType.DOT)
    }
  }

  private identifier(): void {
    while (isIdentifierPart(this.peek())) this.advance()
    const text = this.source.substring(this.start, this.current)
    this.addToken(TokenType.IDENTIFIER, text)
  }

  private number(): void {
    while (isDigit(this.peek())) this.advance()
    if (this.peek() === '.' && isDigit(this.peekNext())) {
      this.advance()
      while (isDigit(this.peek())) this.advance()
    }
    const text = this.source.substring(this.start, this.current)
    this.addToken(TokenType.NUMBER, parseFloat(text))
  }

  private string(): void {
    while (this.peek() !== '"' && !this.isAtEnd()) {
      this.advance()
    }
    if (this.isAtEnd()) {
      throw new LexerException('Unterminated string', this.start)
    }
    this.advance() // closing "
    const content = this.source.substring(this.start + 1, this.current - 1)
    this.addToken(TokenType.STRING, content)
  }

  private skipComment(): void {
    while (!this.isAtEnd() && this.peek() !== '\n') this.advance()
  }

  private isAtEnd(): boolean { return this.current >= this.source.length }
  private advance(): string { return this.source[this.current++]! }
  private peek(): string { return this.isAtEnd() ? '\0' : this.source[this.current]! }
  private peekNext(): string {
    return this.current + 1 >= this.source.length ? '\0' : this.source[this.current + 1]!
  }

  private addToken(type: TokenType, literal: string | number | null = null): void {
    const text = this.source.substring(this.start, this.current)
    this.tokens.push({ type, lexeme: text, literal, position: this.start })
  }
}

function isDigit(c: string): boolean { return c >= '0' && c <= '9' }
function isLetter(c: string): boolean {
  return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
}
function isIdentifierStart(c: string): boolean { return isLetter(c) || c === '_' }
function isIdentifierPart(c: string): boolean {
  return isLetter(c) || isDigit(c) || c === '_'
}
