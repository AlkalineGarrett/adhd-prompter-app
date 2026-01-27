package org.alkaline.taskbrain.dsl.language

/**
 * Recursive descent parser for the TaskBrain DSL.
 * Produces an AST from a sequence of tokens.
 *
 * Milestone 3: Adds parenthesized calls with named arguments.
 * Supports two calling styles:
 * 1. Space-separated: [a b c] -> a(b(c)) (right-to-left nesting)
 * 2. Parenthesized: [add(1, 2)] or [foo(bar: "baz")]
 *
 * Space-separated nesting still works inside parens: [a(b c, d)] -> a(b(c), d)
 *
 * Milestone 4: Adds pattern(...) special parsing for mobile-friendly pattern matching.
 * Milestone 6: Adds dot operator for current note reference and property access.
 * Milestone 7: Adds assignment syntax and statement separation.
 */
class Parser(private val tokens: List<Token>, private val source: String) {

    companion object {
        /** Valid character class names for pattern matching. */
        private val CHAR_CLASS_NAMES = mapOf(
            "digit" to CharClassType.DIGIT,
            "letter" to CharClassType.LETTER,
            "space" to CharClassType.SPACE,
            "punct" to CharClassType.PUNCT,
            "any" to CharClassType.ANY
        )
    }
    private var current = 0

    /**
     * Parse a complete directive.
     * @return The parsed Directive
     * @throws ParseException on syntax error
     */
    fun parseDirective(): Directive {
        val startToken = consume(TokenType.LBRACKET, "Expected '[' to start directive")
        val startPos = startToken.position
        val expression = parseExpression()
        consume(TokenType.RBRACKET, "Expected ']' to close directive")

        val endPos = previous().position + previous().lexeme.length
        val sourceText = source.substring(startPos, endPos)

        return Directive(expression, sourceText, startPos)
    }

    /**
     * Parse an expression.
     * Handles statement lists (semicolon-separated), assignments, and call chains.
     *
     * Milestone 7: Adds statement list and assignment support.
     */
    private fun parseExpression(): Expression {
        return parseStatementList()
    }

    /**
     * Parse a list of statements separated by semicolons.
     * Returns a StatementList if multiple statements, otherwise the single statement.
     *
     * Milestone 7.
     */
    private fun parseStatementList(): Expression {
        val position = peek().position
        val statements = mutableListOf<Expression>()

        statements.add(parseStatement())

        while (match(TokenType.SEMICOLON)) {
            statements.add(parseStatement())
        }

        return if (statements.size == 1) {
            statements[0]
        } else {
            StatementList(statements, position)
        }
    }

    /**
     * Parse a single statement (assignment or expression).
     * Assignment has the form: target : value
     * where target is an identifier (variable) or property access.
     *
     * Milestone 7.
     */
    private fun parseStatement(): Expression {
        val position = peek().position

        // Special case: [x: value] where x is a variable being defined
        // Need to look ahead for IDENTIFIER followed by COLON (but not inside a call)
        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.COLON)) {
            val nameToken = advance()
            val name = nameToken.literal as String
            advance() // consume COLON
            val value = parseCallChain()
            return Assignment(VariableRef(name, nameToken.position), value, position)
        }

        // Parse the expression (could be an assignment target or a regular expression)
        val expr = parseCallChain()

        // Check if this is an assignment (expression followed by COLON)
        if (match(TokenType.COLON)) {
            // Validate that the target is assignable (property access or current note ref)
            if (!isAssignableTarget(expr)) {
                throw ParseException(
                    "Invalid assignment target. Expected variable, property access, or current note reference.",
                    position
                )
            }
            val value = parseCallChain()
            return Assignment(expr, value, position)
        }

        return expr
    }

    /**
     * Check if an expression can be the target of an assignment.
     */
    private fun isAssignableTarget(expr: Expression): Boolean {
        return when (expr) {
            is PropertyAccess -> true  // .path, note.path
            is CurrentNoteRef -> true  // . (whole note)
            is VariableRef -> true     // x
            else -> false
        }
    }

    /**
     * Parse a chain of space-separated calls.
     * Uses right-to-left nesting: [a b c] -> a(b(c))
     */
    private fun parseCallChain(): Expression {
        val first = parsePostfix(parsePrimary())

        // If the first element is not an identifier, it can't start a call chain
        if (first !is CallExpr) {
            return first
        }

        // Check if there are more expressions to nest (not at end-of-argument boundary)
        if (!isAtExpressionStart()) {
            return first
        }

        // Parse the rest of the chain recursively
        val rest = parseCallChain()

        // Nest right-to-left: the rest becomes the argument to first
        return CallExpr(first.name, listOf(rest), first.position, first.namedArgs)
    }

    /**
     * Parse postfix operators (property access chains and method calls).
     * Example: expr.path.name -> PropertyAccess(PropertyAccess(expr, "path"), "name")
     * Example: expr.append("x") -> MethodCall(expr, "append", ["x"], [])
     *
     * Milestone 6: Property access chains.
     * Milestone 7: Method calls on expressions.
     */
    private fun parsePostfix(expr: Expression): Expression {
        var result = expr

        // Keep consuming .identifier chains and method calls
        while (match(TokenType.DOT)) {
            val dotPosition = previous().position
            val propToken = consume(TokenType.IDENTIFIER, "Expected property or method name after '.'")
            val propName = propToken.literal as String

            // Check if this is a method call (followed by parentheses)
            if (match(TokenType.LPAREN)) {
                val (positionalArgs, namedArgs) = parseMethodArguments()
                result = MethodCall(result, propName, positionalArgs, namedArgs, dotPosition)
            } else {
                result = PropertyAccess(result, propName, dotPosition)
            }
        }

        return result
    }

    /**
     * Parse method arguments inside parentheses.
     * Called after LPAREN has been consumed.
     *
     * Milestone 7.
     */
    private fun parseMethodArguments(): Pair<List<Expression>, List<NamedArg>> {
        val positionalArgs = mutableListOf<Expression>()
        val namedArgs = mutableListOf<NamedArg>()

        // Check for empty argument list
        if (!check(TokenType.RPAREN)) {
            do {
                val arg = parseArgument()
                when (arg) {
                    is ParsedPositionalArg -> {
                        if (namedArgs.isNotEmpty()) {
                            throw ParseException(
                                "Positional argument cannot follow named argument",
                                arg.expr.position
                            )
                        }
                        positionalArgs.add(arg.expr)
                    }
                    is ParsedNamedArg -> {
                        namedArgs.add(arg.namedArg)
                    }
                }
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RPAREN, "Expected ')' after method arguments")

        return positionalArgs to namedArgs
    }

    /**
     * Check if we're at the start of another expression.
     * Not at: ], ), ,, :, ;, or EOF
     *
     * Milestone 7: Added SEMICOLON as a boundary.
     */
    private fun isAtExpressionStart(): Boolean {
        return !check(TokenType.RBRACKET) &&
               !check(TokenType.RPAREN) &&
               !check(TokenType.COMMA) &&
               !check(TokenType.COLON) &&
               !check(TokenType.SEMICOLON) &&
               !isAtEnd()
    }

    /**
     * Parse a primary expression (literal, identifier, current note ref, or parenthesized call).
     */
    private fun parsePrimary(): Expression {
        return when {
            match(TokenType.NUMBER) -> {
                val token = previous()
                NumberLiteral(token.literal as Double, token.position)
            }
            match(TokenType.STRING) -> {
                val token = previous()
                StringLiteral(token.literal as String, token.position)
            }
            match(TokenType.DOT) -> {
                // Current note reference: [.] or [.path] or [.method(...)]
                val position = previous().position
                val currentNoteRef = CurrentNoteRef(position)

                // Check if followed by identifier for property access or method call
                if (check(TokenType.IDENTIFIER)) {
                    val propToken = advance()
                    val propName = propToken.literal as String

                    // Check if this is a method call (followed by parentheses)
                    if (match(TokenType.LPAREN)) {
                        val (positionalArgs, namedArgs) = parseMethodArguments()
                        MethodCall(currentNoteRef, propName, positionalArgs, namedArgs, position)
                    } else {
                        PropertyAccess(currentNoteRef, propName, position)
                    }
                } else {
                    // Just [.] - return current note reference
                    currentNoteRef
                }
            }
            match(TokenType.IDENTIFIER) -> {
                val token = previous()
                val name = token.literal as String
                val position = token.position

                // Check for parenthesized argument list
                if (match(TokenType.LPAREN)) {
                    // Special case: pattern(...) has its own parsing mode
                    if (name == "pattern") {
                        parsePatternExpression(position)
                    } else {
                        parseParenthesizedCall(name, position)
                    }
                } else {
                    // An identifier alone becomes a zero-arg function call
                    CallExpr(name, emptyList(), position)
                }
            }
            else -> throw ParseException(
                "Expected expression",
                peek().position
            )
        }
    }

    /**
     * Parse parenthesized arguments: name(arg1, arg2, key: value, ...)
     * Called after LPAREN has been consumed.
     */
    private fun parseParenthesizedCall(name: String, position: Int): CallExpr {
        val positionalArgs = mutableListOf<Expression>()
        val namedArgs = mutableListOf<NamedArg>()

        // Check for empty argument list
        if (!check(TokenType.RPAREN)) {
            do {
                val arg = parseArgument()
                when (arg) {
                    is ParsedPositionalArg -> {
                        if (namedArgs.isNotEmpty()) {
                            throw ParseException(
                                "Positional argument cannot follow named argument",
                                arg.expr.position
                            )
                        }
                        positionalArgs.add(arg.expr)
                    }
                    is ParsedNamedArg -> {
                        namedArgs.add(arg.namedArg)
                    }
                }
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RPAREN, "Expected ')' after arguments")

        return CallExpr(name, positionalArgs, position, namedArgs)
    }

    /**
     * Result of parsing a single argument - either positional or named.
     */
    private sealed class ParsedArg
    private data class ParsedPositionalArg(val expr: Expression) : ParsedArg()
    private data class ParsedNamedArg(val namedArg: NamedArg) : ParsedArg()

    /**
     * Parse a single argument (positional or named).
     * Named arguments have the form: identifier: expression
     * Positional arguments are just expressions.
     */
    private fun parseArgument(): ParsedArg {
        // Look ahead: if we see IDENTIFIER followed by COLON, it's a named argument
        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.COLON)) {
            val nameToken = advance()
            val argName = nameToken.literal as String
            val argPosition = nameToken.position
            consume(TokenType.COLON, "Expected ':' after parameter name")
            val value = parseCallChain()
            return ParsedNamedArg(NamedArg(argName, value, argPosition))
        }

        // Otherwise it's a positional argument
        val expr = parseCallChain()
        return ParsedPositionalArg(expr)
    }

    /**
     * Check the next token (one ahead) without consuming.
     */
    private fun checkNext(type: TokenType): Boolean {
        if (current + 1 >= tokens.size) return false
        return tokens[current + 1].type == type
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw ParseException(message, peek().position)
    }

    // ========================================================================
    // Pattern Parsing (Milestone 4)
    // ========================================================================

    /**
     * Parse a pattern expression: pattern(digit*4 "-" digit*2 "-" digit*2)
     * Called after 'pattern(' has been consumed.
     *
     * Pattern syntax:
     * - Character classes: digit, letter, space, punct, any
     * - Quantifiers: *4, *any, *(0..5), *(1..)
     * - Literals: "string"
     */
    private fun parsePatternExpression(position: Int): PatternExpr {
        val elements = mutableListOf<PatternElement>()

        // Parse pattern elements until we hit RPAREN
        while (!check(TokenType.RPAREN)) {
            elements.add(parsePatternElement())
        }

        consume(TokenType.RPAREN, "Expected ')' after pattern elements")

        if (elements.isEmpty()) {
            throw ParseException("Pattern cannot be empty", position)
        }

        return PatternExpr(elements, position)
    }

    /**
     * Parse a single pattern element (possibly with a quantifier).
     */
    private fun parsePatternElement(): PatternElement {
        val baseElement = parseBasePatternElement()

        // Check for optional quantifier
        return if (check(TokenType.STAR)) {
            parseQuantifiedElement(baseElement)
        } else {
            baseElement
        }
    }

    /**
     * Parse a base pattern element (before quantifier).
     */
    private fun parseBasePatternElement(): PatternElement {
        return when {
            match(TokenType.STRING) -> {
                val token = previous()
                PatternLiteral(token.literal as String, token.position)
            }
            match(TokenType.IDENTIFIER) -> {
                val token = previous()
                val name = token.literal as String
                val charClassType = CHAR_CLASS_NAMES[name]
                    ?: throw ParseException(
                        "Unknown pattern character class '$name'. " +
                        "Valid classes are: ${CHAR_CLASS_NAMES.keys.joinToString()}",
                        token.position
                    )
                CharClass(charClassType, token.position)
            }
            else -> throw ParseException(
                "Expected pattern element (character class or string literal)",
                peek().position
            )
        }
    }

    /**
     * Parse a quantified pattern element: element*4, element*any, element*(0..5)
     * Called when STAR is the current token.
     */
    private fun parseQuantifiedElement(element: PatternElement): Quantified {
        val starToken = advance() // consume STAR
        val quantifier = parseQuantifier()
        return Quantified(element, quantifier, element.position)
    }

    /**
     * Parse a quantifier after the STAR token.
     * Forms: *4, *any, *(0..5), *(1..)
     */
    private fun parseQuantifier(): Quantifier {
        return when {
            match(TokenType.NUMBER) -> {
                // *4 - exact count
                val token = previous()
                val count = (token.literal as Double).toInt()
                if (count < 0) {
                    throw ParseException("Quantifier count must be non-negative", token.position)
                }
                Quantifier.Exact(count)
            }
            match(TokenType.IDENTIFIER) -> {
                // *any - match any number of times
                val token = previous()
                val name = token.literal as String
                if (name != "any") {
                    throw ParseException(
                        "Expected quantifier: number, 'any', or '(min..max)'. Got '$name'",
                        token.position
                    )
                }
                Quantifier.Any
            }
            match(TokenType.LPAREN) -> {
                // *(0..5) or *(1..) - range quantifier
                parseRangeQuantifier()
            }
            else -> throw ParseException(
                "Expected quantifier after '*': number, 'any', or '(min..max)'",
                peek().position
            )
        }
    }

    /**
     * Parse a range quantifier: (0..5) or (1..)
     * Called after LPAREN has been consumed.
     */
    private fun parseRangeQuantifier(): Quantifier.Range {
        // Parse minimum
        val minToken = consume(TokenType.NUMBER, "Expected minimum in range quantifier")
        val min = (minToken.literal as Double).toInt()
        if (min < 0) {
            throw ParseException("Range minimum must be non-negative", minToken.position)
        }

        // Expect ..
        consume(TokenType.DOTDOT, "Expected '..' in range quantifier")

        // Parse maximum (optional - if missing, it's unbounded)
        val max: Int? = if (check(TokenType.NUMBER)) {
            val maxToken = advance()
            val maxVal = (maxToken.literal as Double).toInt()
            if (maxVal < min) {
                throw ParseException(
                    "Range maximum ($maxVal) must be >= minimum ($min)",
                    maxToken.position
                )
            }
            maxVal
        } else {
            null // unbounded
        }

        consume(TokenType.RPAREN, "Expected ')' after range quantifier")

        return Quantifier.Range(min, max)
    }
}

/**
 * Exception thrown when the parser encounters a syntax error.
 */
class ParseException(
    message: String,
    val position: Int
) : RuntimeException("Parse error at position $position: $message")
