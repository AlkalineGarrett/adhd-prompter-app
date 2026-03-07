// AST node types using discriminated unions

export type Expression =
  | NumberLiteral
  | StringLiteral
  | CallExpr
  | CurrentNoteRef
  | PropertyAccess
  | Assignment
  | StatementList
  | VariableRef
  | MethodCall
  | PatternExpr
  | LambdaExpr
  | LambdaInvocation
  | OnceExpr
  | RefreshExpr

export interface NumberLiteral {
  kind: 'NumberLiteral'
  value: number
  position: number
}

export interface StringLiteral {
  kind: 'StringLiteral'
  value: string
  position: number
}

export interface NamedArg {
  name: string
  value: Expression
  position: number
}

export interface CallExpr {
  kind: 'CallExpr'
  name: string
  args: Expression[]
  namedArgs: NamedArg[]
  position: number
}

export interface Directive {
  expression: Expression
  sourceText: string
  startPosition: number
}

export interface CurrentNoteRef {
  kind: 'CurrentNoteRef'
  position: number
}

export interface PropertyAccess {
  kind: 'PropertyAccess'
  target: Expression
  property: string
  position: number
}

export interface Assignment {
  kind: 'Assignment'
  target: Expression
  value: Expression
  position: number
}

export interface StatementList {
  kind: 'StatementList'
  statements: Expression[]
  position: number
}

export interface VariableRef {
  kind: 'VariableRef'
  name: string
  position: number
}

export interface MethodCall {
  kind: 'MethodCall'
  target: Expression
  methodName: string
  args: Expression[]
  namedArgs: NamedArg[]
  position: number
}

// Pattern AST nodes

export enum CharClassType {
  DIGIT = 'DIGIT',
  LETTER = 'LETTER',
  SPACE = 'SPACE',
  PUNCT = 'PUNCT',
  ANY = 'ANY',
}

export type Quantifier =
  | { kind: 'Exact'; n: number }
  | { kind: 'Range'; min: number; max: number | null }
  | { kind: 'Any' }

export type PatternElement = CharClass | PatternLiteral | Quantified

export interface CharClass {
  kind: 'CharClass'
  type: CharClassType
  position: number
}

export interface PatternLiteral {
  kind: 'PatternLiteral'
  value: string
  position: number
}

export interface Quantified {
  kind: 'Quantified'
  element: PatternElement
  quantifier: Quantifier
  position: number
}

export interface PatternExpr {
  kind: 'PatternExpr'
  elements: PatternElement[]
  position: number
}

// Lambda

export interface LambdaExpr {
  kind: 'LambdaExpr'
  params: string[]
  body: Expression
  position: number
}

export interface LambdaInvocation {
  kind: 'LambdaInvocation'
  lambda: LambdaExpr
  args: Expression[]
  namedArgs: NamedArg[]
  position: number
}

// Execution blocks

export interface OnceExpr {
  kind: 'OnceExpr'
  body: Expression
  position: number
}

export interface RefreshExpr {
  kind: 'RefreshExpr'
  body: Expression
  position: number
}
