package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.dsl.language.Assignment
import org.alkaline.taskbrain.dsl.language.CallExpr
import org.alkaline.taskbrain.dsl.language.CharClass
import org.alkaline.taskbrain.dsl.language.CurrentNoteRef
import org.alkaline.taskbrain.dsl.language.Expression
import org.alkaline.taskbrain.dsl.language.LambdaExpr
import org.alkaline.taskbrain.dsl.language.LambdaInvocation
import org.alkaline.taskbrain.dsl.language.MethodCall
import org.alkaline.taskbrain.dsl.language.NamedArg
import org.alkaline.taskbrain.dsl.language.NumberLiteral
import org.alkaline.taskbrain.dsl.language.OnceExpr
import org.alkaline.taskbrain.dsl.language.PatternElement
import org.alkaline.taskbrain.dsl.language.PatternExpr
import org.alkaline.taskbrain.dsl.language.PatternLiteral
import org.alkaline.taskbrain.dsl.language.PropertyAccess
import org.alkaline.taskbrain.dsl.language.Quantified
import org.alkaline.taskbrain.dsl.language.Quantifier
import org.alkaline.taskbrain.dsl.language.RefreshExpr
import org.alkaline.taskbrain.dsl.language.StatementList
import org.alkaline.taskbrain.dsl.language.StringLiteral
import org.alkaline.taskbrain.dsl.language.VariableRef
import java.security.MessageDigest

/**
 * Normalizes AST for cache key generation.
 *
 * Phase 2: AST normalization for cache keys.
 *
 * This normalizer produces a canonical string representation of the AST
 * that is consistent regardless of syntactic variations:
 * - `func[x]` and `func([x])` produce the same normalized form
 * - Source positions are ignored (only structure matters)
 *
 * The normalized form is then hashed to create cache keys.
 */
object AstNormalizer {

    /**
     * Generate a cache key hash for an expression.
     * Returns a hex string suitable for use as a cache key.
     */
    fun computeCacheKey(expr: Expression): String {
        val normalized = normalize(expr)
        return hash(normalized)
    }

    /**
     * Normalize an expression to a canonical string form.
     * This form is position-independent and syntactically normalized.
     */
    fun normalize(expr: Expression): String {
        return when (expr) {
            is NumberLiteral -> "NUM(${expr.value})"
            is StringLiteral -> "STR(${escapeString(expr.value)})"
            is CurrentNoteRef -> "SELF"
            is VariableRef -> "VAR(${expr.name})"

            is PropertyAccess -> "PROP(${normalize(expr.target)},${expr.property})"

            is MethodCall -> {
                val argsNorm = normalizeArgs(expr.args, expr.namedArgs)
                "METHOD(${normalize(expr.target)},${expr.methodName},$argsNorm)"
            }

            is CallExpr -> {
                val argsNorm = normalizeArgs(expr.args, expr.namedArgs)
                "CALL(${expr.name},$argsNorm)"
            }

            is Assignment -> {
                val targetNorm = normalizeAssignmentTarget(expr.target)
                "ASSIGN($targetNorm,${normalize(expr.value)})"
            }

            is StatementList -> {
                val stmtsNorm = expr.statements.joinToString(";") { normalize(it) }
                "STMTS($stmtsNorm)"
            }

            is LambdaExpr -> {
                val paramsNorm = expr.params.joinToString(",")
                "LAMBDA($paramsNorm,${normalize(expr.body)})"
            }

            is LambdaInvocation -> {
                // Normalize as a call to the lambda
                val lambdaNorm = normalize(expr.lambda)
                val argsNorm = normalizeArgs(expr.args, expr.namedArgs)
                "INVOKE($lambdaNorm,$argsNorm)"
            }

            is OnceExpr -> "ONCE(${normalize(expr.body)})"

            is RefreshExpr -> "REFRESH(${normalize(expr.body)})"

            is PatternExpr -> {
                val elementsNorm = expr.elements.joinToString(",") { normalizePatternElement(it) }
                "PATTERN($elementsNorm)"
            }
        }
    }

    private fun normalizeArgs(args: List<Expression>, namedArgs: List<NamedArg>): String {
        val parts = mutableListOf<String>()

        // Positional args
        for (arg in args) {
            parts.add(normalize(arg))
        }

        // Named args (sorted by name for consistency)
        for (namedArg in namedArgs.sortedBy { it.name }) {
            parts.add("${namedArg.name}=${normalize(namedArg.value)}")
        }

        return parts.joinToString(",")
    }

    private fun normalizeAssignmentTarget(target: Expression): String {
        return when (target) {
            is VariableRef -> target.name
            is PropertyAccess -> "PROP(${normalize(target.target)},${target.property})"
            else -> normalize(target)
        }
    }

    private fun normalizePatternElement(element: PatternElement): String {
        return when (element) {
            is CharClass -> "CHAR(${element.type.name})"
            is PatternLiteral -> "LIT(${escapeString(element.value)})"
            is Quantified -> {
                val quantNorm = when (val q = element.quantifier) {
                    is Quantifier.Exact -> "x${q.n}"
                    is Quantifier.Range -> "${q.min}..${q.max ?: ""}"
                    is Quantifier.Any -> "*"
                }
                "QUANT(${normalizePatternElement(element.element)},$quantNorm)"
            }
        }
    }

    private fun escapeString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
