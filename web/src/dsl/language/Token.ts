export enum TokenType {
  LBRACKET = 'LBRACKET',
  RBRACKET = 'RBRACKET',
  LPAREN = 'LPAREN',
  RPAREN = 'RPAREN',
  COMMA = 'COMMA',
  COLON = 'COLON',
  SEMICOLON = 'SEMICOLON',
  DOT = 'DOT',
  STAR = 'STAR',
  DOTDOT = 'DOTDOT',
  NUMBER = 'NUMBER',
  STRING = 'STRING',
  IDENTIFIER = 'IDENTIFIER',
  EOF = 'EOF',
}

export interface Token {
  type: TokenType
  lexeme: string
  literal: string | number | null
  position: number
}
