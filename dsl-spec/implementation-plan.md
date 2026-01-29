# Mindl Implementation Plan

Detailed implementation plan for Mindl (the TaskBrain DSL), based on the spec and implementation decisions.

---

## Phase 0: Foundation Infrastructure

### 0.1 Package Structure

Mindl files are organized into subpackages under `app/src/main/java/org/alkaline/taskbrain/dsl/`:

**language/** - Core Mindl parsing pipeline
```
├── TokenType.kt              # Enum of token types
├── Token.kt                  # Token data class with position
├── Lexer.kt                  # Tokenizes input string (+ LexerException)
├── Expression.kt             # AST node types + Directive wrapper
├── Parser.kt                 # Recursive descent parser (+ ParseException)
├── DynamicCallAnalyzer.kt    # Checks if AST contains dynamic function calls
└── IdempotencyAnalyzer.kt    # Analyzes idempotency of expressions
```

**runtime/** - Execution engine and context
```
├── Executor.kt               # AST interpreter
├── ExecutionException.kt     # Execution errors with position
├── Environment.kt            # Variable scopes, note context, mutation tracking
├── BuiltinRegistry.kt        # Function registry + BuiltinFunction
├── Arguments.kt              # Container for positional + named args with validation
├── NoteContext.kt            # Bundles notes/currentNote/noteOperations
├── NoteOperations.kt         # Interface for note mutations
├── NoteRepositoryOperations.kt  # NoteOperations impl using NoteRepository
├── NoteMutation.kt           # Tracks mutations during execution
├── NotePropertyHandler.kt    # Handles note property access/assignment
└── NoteMethodHandler.kt      # Handles note method calls
```

**runtime/values/** - Runtime value types
```
├── DslValue.kt               # Base class with serialization/deserialization
├── PrimitiveValues.kt        # UndefinedVal, NumberVal, StringVal, BooleanVal
├── TemporalValues.kt         # DateVal, TimeVal, DateTimeVal
├── PatternVal.kt             # Pattern values with regex compilation
├── ListVal.kt                # List values
└── NoteVal.kt                # Note values
```

**BuiltinFunction.isDynamic**: Each registered function has an `isDynamic` flag:
- `true` for functions that return different results each call (e.g., `date`, `datetime`, `time`)
- `false` for pure/static functions (e.g., `add`, `qt`, `nl`)

**DynamicCallAnalyzer.containsDynamicCalls(expr)**: Recursively checks if an AST contains any dynamic function calls. Used to determine if a directive needs re-execution when confirmed (dynamic directives are re-executed; static ones use cached results).

**builtins/** - Library functions (extensible)
```
├── DateFunctions.kt          # date, datetime, time (dynamic)
├── ArithmeticFunctions.kt    # add, sub, mul, div, mod (static)
├── CharacterConstants.kt     # qt, nl, tab, ret (special chars for mobile)
├── PatternFunctions.kt       # matches (static)
├── NoteFunctions.kt          # find, new, maybe_new (note operations)
├── ListFunctions.kt          # list, sort, first (list operations) - Milestone 9
└── SortConstants.kt          # ascending, descending (sort order constants) - Milestone 9
```

**directives/** - Directive lifecycle management
```
├── DirectiveFinder.kt        # Finds directives in note content, executes them
├── DirectiveInstance.kt      # UUID-stable directive tracking across edits
├── DirectiveResult.kt        # Cached execution result model
├── DirectiveResultRepository.kt  # Firestore storage for results
└── DirectiveSegment.kt       # Segment types + DirectiveSegmenter + DisplayTextResult
```

**DirectiveInstance UUID Matching Algorithm** (`matchDirectiveInstances`):
Preserves UUIDs across text edits using a 4-pass priority system:
1. **Exact match**: Same line, same offset, same text → reuse UUID
2. **Same line shift**: Same line, same text, different offset → reuse UUID (text shifted)
3. **Line move**: Different line, same text, **unique candidate only** → reuse UUID (avoids ambiguity when multiple identical directives exist)
4. **No match**: Generate new UUID

The uniqueness check in pass 3 is critical: if multiple existing directives have the same text, we don't guess which one moved—we generate new UUIDs to avoid incorrect cache associations.

**ui/** - Compose presentation layer
```
├── DirectiveColors.kt        # Centralized color definitions for directive UI
├── DirectiveChip.kt          # Standalone chip for collapsed/expanded display
├── DirectiveLineRenderer.kt  # Renders lines with computed results in dashed boxes
└── DirectiveEditRow.kt       # Edit row with confirm/cancel for directive editing
```

**UI integration (in ui/currentnote/):**
```
├── ime/DirectiveAwareLineInput.kt  # Custom input handling for directive lines
```

**Future additions:**
```
dsl/
├── ScheduleRepository.kt     # For Milestone 13
└── ViewContentTracker.kt     # For Milestone 9 (view)
```

**Test structure** mirrors main source under `app/src/test/java/org/alkaline/taskbrain/dsl/`:
```
├── language/
│   ├── LexerTest.kt          # Token tests
│   ├── ParserTest.kt         # AST parsing tests
│   ├── PatternTest.kt        # Pattern parsing/compilation tests
│   ├── NotePropertiesTest.kt # Note properties and hierarchy navigation tests
│   └── IdempotencyAnalyzerTest.kt  # Idempotency analysis tests
├── runtime/
│   ├── ExecutorTest.kt       # Execution tests
│   └── NoteMutationTest.kt   # Note mutation tests (variable/property assignment, methods)
├── builtins/
│   ├── NoteFunctionsTest.kt  # find, new, maybe_new tests
│   └── ListFunctionsTest.kt  # sort, first tests (Milestone 9)
└── directives/
    ├── DirectiveFinderTest.kt
    ├── DirectiveInstanceTest.kt
    ├── DirectiveResultTest.kt
    └── DirectiveSegmenterTest.kt
```

**Test utilities** under `app/src/test/java/org/alkaline/taskbrain/testutil/`:
```
└── MockNoteOperations.kt     # Mock implementation for testing note mutations
```

### 0.2 Firestore Schema Additions

**Subcollection: `notes/{noteId}/directiveResults/{directiveHash}`** ✅ IMPLEMENTED
```
{
  result: {                 // Serialized DslValue (null if error)
    type: string,           // "number" | "string" | ... (type discriminator)
    value: any              // The actual value
  } | null,
  executedAt: timestamp,
  error: string | null,
  collapsed: boolean
}
```

**Firestore Rules Addition:**
```javascript
match /notes/{noteId}/directiveResults/{resultId} {
  allow read, write: if request.auth != null &&
    get(/databases/$(database)/documents/notes/$(noteId)).data.userId == request.auth.uid;
}
```

**Collection: `schedules`**
```
{
  directiveHash: string,    // Hash of directive text (identity)
  noteId: string,           // Parent note reference
  notePath: string,         // For display purposes
  rule: {
    type: "at" | "daily_at",
    datetime?: timestamp,   // For "at"
    time?: string           // For "daily_at" (e.g., "09:00")
  },
  deferredAst: string,      // Serialized AST to execute
  nextExecution: timestamp,
  status: "active" | "paused" | "failed",
  lastExecution: timestamp | null,
  lastError: string | null,
  failureCount: number,
  createdAt: timestamp
}
```

---

## Milestone 1: Literals ✅ COMPLETE

**Target:** `[1]`, `["hello"]`

### Lexer (Lexer.kt, Token.kt, TokenType.kt)
| Token Type | Pattern |
|------------|---------|
| `LBRACKET` | `[` |
| `RBRACKET` | `]` |
| `NUMBER` | `[0-9]+(\.[0-9]+)?` |
| `STRING` | `"[^"]*"` |
| `EOF` | End of input |

**Note:** Strings have no escape sequences (mobile-friendly). All characters between quotes are literal. Special characters are inserted via character constants (`qt`, `nl`, `tab`, `ret`) with the `string()` function in later milestones.

**Implementation notes:**
- `Token` includes `position: Int` for error reporting
- `LexerException` includes position for pinpointing errors

### Parser (Parser.kt)
- `parseDirective()` → expects `[`, expression, `]`; captures source text
- `parseExpression()` → for now, just `parseLiteral()`
- `parseLiteral()` → NUMBER or STRING token

### AST Nodes (Expression.kt)
```kotlin
sealed class Expression {
    abstract val position: Int  // Source position for error reporting
}
data class NumberLiteral(val value: Double, override val position: Int) : Expression()
data class StringLiteral(val value: String, override val position: Int) : Expression()

// Wrapper for a complete directive with source text
data class Directive(
    val expression: Expression,
    val sourceText: String,      // Full source including brackets
    val startPosition: Int
)
```

### Executor (Executor.kt)
```kotlin
class Executor {
    fun execute(directive: Directive, env: Environment = Environment()): DslValue
    fun evaluate(expr: Expression, env: Environment): DslValue = when (expr) {
        is NumberLiteral -> NumberVal(expr.value)
        is StringLiteral -> StringVal(expr.value)
    }
}
```

### ExecutionException (ExecutionException.kt)
```kotlin
class ExecutionException(message: String, val position: Int? = null) : RuntimeException(message)
```

### Environment (Environment.kt)
```kotlin
// Environment with parent scoping (minimal for M1, expanded in later milestones)
class Environment(private val parent: Environment? = null) {
    fun define(name: String, value: DslValue)
    fun get(name: String): DslValue?
    fun child(): Environment
    fun capture(): Environment
}
```

### Types (runtime/values/)

**DslValue.kt** - Base class:
```kotlin
sealed class DslValue {
    abstract val typeName: String           // "number", "string"
    abstract fun toDisplayString(): String  // Human-readable output
    fun serialize(): Map<String, Any?>      // For Firestore
    companion object {
        fun deserialize(map: Map<String, Any?>): DslValue
    }
}
```

**PrimitiveValues.kt** - Basic value types:
```kotlin
object UndefinedVal : DslValue()  // For graceful undefined access
data class NumberVal(val value: Double) : DslValue()  // Displays integers without decimal
data class StringVal(val value: String) : DslValue()
data class BooleanVal(val value: Boolean) : DslValue()  // Added in Milestone 4
```

### Directive Finding (DirectiveFinder.kt)
```kotlin
object DirectiveFinder {
    /**
     * Creates a position-based key for a directive.
     * Format: "lineIndex:startOffset" (e.g., "3:15" for line 3, offset 15)
     *
     * Position-based keys (not hash-based) allow multiple identical directives
     * (e.g., two [now] on different lines) to have separate cached results.
     */
    fun directiveKey(lineIndex: Int, startOffset: Int): String

    // Pattern: \[[^\[\]]*\] - matches [...] that doesn't contain [ or ] inside.
    // Stricter than \[.*?\] to explicitly reject nested brackets until nesting is supported.
    private val DIRECTIVE_PATTERN = Regex("""\[[^\[\]]*\]""")

    data class FoundDirective(
        val sourceText: String,
        val startOffset: Int,
        val endOffset: Int
    ) {
        fun hash(): String  // SHA-256 of sourceText
    }

    fun findDirectives(content: String): List<FoundDirective>
    fun containsDirectives(content: String): Boolean
    fun executeDirective(sourceText: String): DirectiveResult
    fun executeAllDirectives(content: String): Map<String, DirectiveResult>
}
```

**Pattern decision:** Using `\[[^\[\]]*\]` instead of `\[.*?\]` to properly reject nested brackets until nesting is supported. This matches any `[...]` that doesn't contain `[` or `]` inside.

### Line Segmentation (DirectiveSegment.kt)
```kotlin
sealed class DirectiveSegment {
    abstract val range: IntRange

    data class Text(val content: String, override val range: IntRange)
    data class Directive(
        val sourceText: String,
        val hash: String,
        val result: DirectiveResult?,
        override val range: IntRange
    ) {
        val displayText: String  // Result value or source if not computed
        val isComputed: Boolean
    }
}

object DirectiveSegmenter {
    fun segmentLine(content: String, results: Map<String, DirectiveResult>): List<DirectiveSegment>
    fun hasDirectives(content: String): Boolean
    fun hasComputedDirectives(content: String, results: Map<String, DirectiveResult>): Boolean
    fun buildDisplayText(content: String, results: Map<String, DirectiveResult>): DisplayTextResult
}
```

### UI Integration

**Display Mode (DirectiveLineRenderer.kt):**
- Lines with computed directives render in display mode (no text editing)
- Directive results shown in dashed boxes (green border for success, red for error)
- Tapping a directive expands it for editing

**Dashed Border Styling:**
- Stroke width: 1dp
- Dash length: 4dp
- Gap length: 2dp
- Corner radius: 3dp

**Empty Result Placeholder:**
When a directive evaluates to an empty string, a vertical dashed line is displayed:
- Dimensions: 12dp × 16dp
- Stroke width: 1.5dp, dash: 3dp, gap: 2dp
- Provides a tappable target for editing the directive

**Edit Mode (DirectiveEditRow.kt):**
- Appears below the line when directive is tapped
- Shows directive source text in editable field
- Confirm (checkmark) and Cancel (X) buttons
- Auto-focuses on show

**Integration points:**
- `DirectiveAwareLineInput.kt` - Custom input handling for lines with directives
- `HangingIndentEditor.kt` - Switches between normal editing and directive display mode
- `LineView.kt` - Detects directives and routes to appropriate renderer
- `CurrentNoteViewModel.kt` - Manages directive results state, executes on save

### UI Colors (DirectiveColors.kt)
```kotlin
object DirectiveColors {
    // Success state (computed directives)
    val SuccessBackground = Color(0xFFE8F5E9)
    val SuccessContent = Color(0xFF2E7D32)
    val SuccessBorder = Color(0xFF4CAF50)

    // Error state
    val ErrorBackground = Color(0xFFFFEBEE)
    val ErrorContent = Color(0xFFC62828)
    val ErrorBorder = Color(0xFFF44336)
    val ErrorText = Color(0xFFD32F2F)

    // Edit row
    val EditRowBackground = Color(0xFFF5F5F5)
    val EditIndicator = Color(0xFF9E9E9E)
    val CancelButton = Color(0xFF757575)
}
```

### Storage (DirectiveResult.kt, DirectiveResultRepository.kt)
```kotlin
data class DirectiveResult(
    val result: Map<String, Any?>? = null,  // Serialized DslValue
    val executedAt: Timestamp? = null,
    val error: String? = null,
    val collapsed: Boolean = true
) {
    fun toValue(): DslValue?

    // Display helpers
    fun toDisplayString(fallback: String = "..."): String  // Returns formatted result or error
    val isComputed: Boolean  // True if result != null && error == null

    companion object {
        fun success(value: DslValue, collapsed: Boolean = true): DirectiveResult
        fun failure(errorMessage: String, collapsed: Boolean = true): DirectiveResult
        fun hashDirective(sourceText: String): String  // SHA-256
    }
}

class DirectiveResultRepository(db: FirebaseFirestore) {
    suspend fun saveResult(noteId: String, directiveHash: String, result: DirectiveResult): Result<Unit>
    suspend fun getResults(noteId: String): Result<Map<String, DirectiveResult>>
    suspend fun getResult(noteId: String, directiveHash: String): Result<DirectiveResult?>
    suspend fun updateCollapsed(noteId: String, directiveHash: String, collapsed: Boolean): Result<Unit>
    suspend fun deleteAllResults(noteId: String): Result<Unit>
}
```

### Tests
- `LexerTest.kt` - tokenizes literals, whitespace handling, error cases
- `ParserTest.kt` - produces correct AST, error handling
- `ExecutorTest.kt` - evaluates to correct values
- `DirectiveFinderTest.kt` - finds directives in content, hash calculation
- `DirectiveResultTest.kt` - serialization/deserialization round-trip
- `DirectiveSegmenterTest.kt` - line segmentation, display text building

---

## Milestone 2: Function Calls ✅ COMPLETE

**Target:** `[date]`, `[datetime]`, `[time]`

### Lexer Additions
| Token Type | Pattern |
|------------|---------|
| `IDENTIFIER` | `[a-zA-Z_][a-zA-Z0-9_]*` |

### Parser Additions
- Single identifier → `CallExpr(name, args=[])`
- Space-separated tokens → right-to-left nesting
  - `a b c` → `CallExpr("a", [CallExpr("b", [CallExpr("c", [])])])`

### AST Nodes
```kotlin
data class CallExpr(val name: String, val args: List<Expression>) : Expression()
data class IdentifierExpr(val name: String) : Expression()  // For variables later
```

### Executor
- Look up function in `BuiltinRegistry`
- Evaluate args, call function

### Builtins: DateFunctions.kt
```kotlin
"date" to { args, env -> DateVal(LocalDate.now()) }
"datetime" to { args, env -> DateTimeVal(LocalDateTime.now()) }
"time" to { args, env -> TimeVal(LocalTime.now()) }
```

### Builtins: CharacterConstants.kt
Character constants for mobile-friendly string building (no escape sequences).
Defines reusable constants for special characters used across Mindl:
```kotlin
object CharacterConstants {
    const val QUOTE = "\""
    const val NEWLINE = "\n"
    const val TAB = "\t"
    const val CARRIAGE_RETURN = "\r"
}

// Builtin functions:
"qt" to { args, env -> StringVal(CharacterConstants.QUOTE) }
"nl" to { args, env -> StringVal(CharacterConstants.NEWLINE) }
"tab" to { args, env -> StringVal(CharacterConstants.TAB) }
"ret" to { args, env -> StringVal(CharacterConstants.CARRIAGE_RETURN) }
```

### Types Additions (TemporalValues.kt)
```kotlin
data class DateVal(val value: LocalDate) : DslValue()
data class TimeVal(val value: LocalTime) : DslValue()
data class DateTimeVal(val value: LocalDateTime) : DslValue()
```

**Serialization vs Display Formats**: DslValue uses different formats for storage and display:

| Type | Serialization (Firestore) | Display (UI) |
|------|---------------------------|--------------|
| DateVal | `ISO_LOCAL_DATE` (2026-01-25) | `yyyy-MM-dd` (2026-01-25) |
| TimeVal | `ISO_LOCAL_TIME` (14:30:00.000) | `HH:mm:ss` (14:30:00) |
| DateTimeVal | `ISO_LOCAL_DATE_TIME` (2026-01-25T14:30:00) | `yyyy-MM-dd, HH:mm:ss` (2026-01-25, 14:30:00) |
| NumberVal | Double | Integer format if whole number (42 not 42.0) |

### Tests
- `[date]` returns today's date
- `[datetime]` returns current date and time
- `[time]` returns current time
- Right-to-left nesting works correctly
- `[qt]` returns `"`
- `[nl]` returns newline character
- `[tab]` returns tab character

---

## Milestone 3: Parentheses & Named Arguments ✅ COMPLETE

**Target:** `[add(1, 2)]`, `[foo(bar: "baz")]`

### Lexer Additions
| Token Type | Pattern |
|------------|---------|
| `LPAREN` | `(` |
| `RPAREN` | `)` |
| `COMMA` | `,` |
| `COLON` | `:` |

### Parser Additions
- Parenthesized argument lists: `name(arg1, arg2, ...)`
- Named arguments: `name(param: value, ...)`
- Space-separated nesting still works inside parens: `a(b c, d)` → `a(b(c), d)`

### AST Nodes
```kotlin
data class NamedArg(val name: String, val value: Expression)
data class CallExpr(
    val name: String,
    val positionalArgs: List<Expression>,
    val namedArgs: List<NamedArg>
) : Expression()
```

### Arguments (Arguments.kt)
```kotlin
data class Arguments(
    val positional: List<DslValue>,
    val named: Map<String, DslValue> = emptyMap()
) {
    operator fun get(index: Int): DslValue?
    operator fun get(name: String): DslValue?
    fun require(index: Int, paramName: String): DslValue
    fun requireNamed(name: String): DslValue

    // Validation utilities
    fun requireExactCount(count: Int, funcName: String)
    fun requireNoArgs(funcName: String)
    fun requireNumber(index: Int, funcName: String, paramName: String): NumberVal
    fun requireString(index: Int, funcName: String, paramName: String): StringVal
    fun requirePattern(index: Int, funcName: String, paramName: String): PatternVal
}
```

### Builtins: ArithmeticFunctions.kt
```kotlin
"add" to { args, env -> NumberVal(args[0].asNumber() + args[1].asNumber()) }
"sub" to { args, env -> NumberVal(args[0].asNumber() - args[1].asNumber()) }
"mul" to { args, env -> NumberVal(args[0].asNumber() * args[1].asNumber()) }
"div" to { args, env -> NumberVal(args[0].asNumber() / args[1].asNumber()) }
"mod" to { args, env -> NumberVal(args[0].asNumber() % args[1].asNumber()) }
```

### Tests
- `[add(1, 2)]` returns 3
- `[add(mul(2, 3), 4)]` returns 10
- Named args are parsed and accessible

---

## Milestone 4: Patterns ✅ COMPLETE

**Target:** `[pattern(digit*4 "-" digit*2 "-" digit*2)]`, `[matches("2026-01-15", pattern(...))]`

### Lexer Additions
| Token Type | Pattern |
|------------|---------|
| `STAR` | `*` |
| `DOTDOT` | `..` |

### Parser Additions
- Special parsing mode inside `pattern(...)`
- Character classes: `digit`, `letter`, `space`, `punct`, `any`
- Quantifiers: `*4`, `*any`, `*(0..5)`, `*(1..)`
- Literals in quotes: `"-"`

### AST Nodes (Expression.kt)
```kotlin
enum class CharClassType { DIGIT, LETTER, SPACE, PUNCT, ANY }

sealed class Quantifier {
    data class Exact(val n: Int) : Quantifier()
    data class Range(val min: Int, val max: Int?) : Quantifier()
    data object Any : Quantifier()
}

sealed class PatternElement {
    abstract val position: Int
}
data class CharClass(val type: CharClassType, override val position: Int) : PatternElement()
data class PatternLiteral(val value: String, override val position: Int) : PatternElement()
data class Quantified(val element: PatternElement, val quantifier: Quantifier, override val position: Int) : PatternElement()

data class PatternExpr(val elements: List<PatternElement>, override val position: Int) : Expression()
```

### Types (PatternVal.kt)
```kotlin
data class PatternVal(
    val elements: List<PatternElement>,
    val compiledRegex: Regex
) : DslValue() {
    fun matches(input: String): Boolean = compiledRegex.matches(input)

    companion object {
        fun compile(elements: List<PatternElement>): PatternVal
        fun fromRegexString(regexString: String): PatternVal  // For deserialization
    }
}
```

Note: `BooleanVal` is in `PrimitiveValues.kt`.

### Pattern Compilation (in PatternVal.companion)
```kotlin
private fun elementToRegex(el: PatternElement): String = when (el) {
    is CharClass -> when (el.type) {
        DIGIT -> "\\d"
        LETTER -> "[a-zA-Z]"
        SPACE -> "\\s"
        PUNCT -> "[\\p{Punct}]"
        ANY -> "."
    }
    is PatternLiteral -> Regex.escape(el.value)
    is Quantified -> "${elementToRegex(el.element)}${quantifierToRegex(el.quantifier)}"
}

private fun quantifierToRegex(q: Quantifier): String = when (q) {
    is Quantifier.Exact -> "{${q.n}}"
    is Quantifier.Any -> "*"
    is Quantifier.Range -> if (q.max == null) "{${q.min},}" else "{${q.min},${q.max}}"
}
```

### Builtins: PatternFunctions.kt
```kotlin
object PatternFunctions {
    fun register(registry: BuiltinRegistry)
}

"matches" to { args, env ->
    val string = args[0] as StringVal
    val pattern = args[1] as PatternVal
    BooleanVal(pattern.matches(string.value))
}
```

### Tests
- `pattern(digit*4 "-" digit*2 "-" digit*2)` matches "2026-01-15"
- Quantifier variations work correctly
- `matches("2026-01-15", pattern(...))` returns true
- `matches("not-a-date", pattern(...))` returns false
- Error handling for wrong argument types

---

## Milestone 5: Find with Pattern ✅ COMPLETE

**Target:** `[find(path: pattern(digit*4 "-" digit*2 "-" digit*2))]`, `[find(name: "Shopping List")]`

### Implementation Summary

**Changes made:**
1. Added `path` field to Note model (`data/Note.kt`) and updated `schema.md`
2. Added `NoteVal` and `ListVal` types to `DslValue.kt` with serialization/deserialization
3. Extended `Environment` to carry optional note list for find operations
4. Created `NoteFunctions.kt` with `find(path: ...)` function supporting both exact string match and pattern match
5. Registered `NoteFunctions` in `BuiltinRegistry`
6. Updated `DirectiveFinder` to accept and pass notes to executor via environment
7. Updated `CurrentNoteViewModel` to load and pass notes to directive execution

### Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `path` | String or Pattern | Match against note's `path` field |
| `name` | String or Pattern | Match against note's name (first line of content) |
| `where` | Lambda | Predicate for filtering (Milestone 7) |

All parameters are optional. Multiple parameters are combined with AND logic.

### Builtins: NoteFunctions.kt
```kotlin
// Implemented in builtins/NoteFunctions.kt
"find" to { args, env ->
    val pathArg = args["path"]
    val nameArg = args["name"]
    val notes = env.getNotes() ?: emptyList()

    val filtered = notes.filter { note ->
        val pathMatches = when (pathArg) {
            null -> true
            is StringVal -> note.path == pathArg.value
            is PatternVal -> pathArg.matches(note.path)
            else -> throw ExecutionException("path must be string or pattern")
        }
        val nameMatches = when (nameArg) {
            null -> true
            is StringVal -> note.content.lines().firstOrNull() == nameArg.value
            is PatternVal -> nameArg.matches(note.content.lines().firstOrNull() ?: "")
            else -> throw ExecutionException("name must be string or pattern")
        }
        pathMatches && nameMatches
    }
    ListVal(filtered.map { NoteVal(it) })
}
```

### Types Additions (runtime/values/)

**NoteVal.kt:**
```kotlin
data class NoteVal(val note: Note) : DslValue() {
    fun getProperty(name: String, env: Environment): DslValue  // Delegates to NotePropertyHandler
}
```

**ListVal.kt:**
```kotlin
data class ListVal(val items: List<DslValue>) : DslValue() {
    val size: Int get() = items.size
    operator fun get(index: Int): DslValue = items[index]
}
```

### Environment Extension
```kotlin
class Environment(
    private val parent: Environment? = null,
    private val notes: List<Note>? = null  // For find() operations
) {
    fun getNotes(): List<Note>? = notes ?: parent?.getNotes()

    companion object {
        fun withNotes(notes: List<Note>): Environment
    }
}
```

### DirectiveFinder Update
```kotlin
fun executeDirective(sourceText: String, notes: List<Note>? = null): DirectiveResult
fun executeAllDirectives(content: String, lineIndex: Int, notes: List<Note>? = null): Map<String, DirectiveResult>
```

### Query Optimization (Future)
- For prefix patterns (e.g., `"journal/" any*(1..)`), use Firestore range query
- For regex patterns, fetch candidates and filter client-side
- Consider indexing common path prefixes

### Tests (NoteFunctionsTest.kt)
- Find with no notes returns empty list ✅
- Find with exact path returns matching note ✅
- Find with pattern returns matching notes ✅
- Find returns empty list when no matches ✅
- Find without arguments returns all notes ✅
- NoteVal/ListVal serialization round-trip ✅
- Display strings work correctly ✅
- Find with exact name returns matching note ✅
- Find with name pattern returns matching notes ✅
- Find with both path and name filters ✅

---

## Milestone 6: Note Properties ✅ COMPLETE

**Target:** `[.path]`, `[i.path]`, `[parse_date i.path]`

### Lexer Additions ✅
| Token Type | Pattern |
|------------|---------|
| `DOT` | `.` |

### Parser Additions ✅
- `.` alone → current note reference
- `.prop` → current note property access
- `expr.prop` → property access on expression result

### AST Nodes ✅
```kotlin
data class CurrentNoteRef(override val position: Int) : Expression()
data class PropertyAccess(val target: Expression, val property: String, override val position: Int) : Expression()
```

### Executor ✅
```kotlin
is CurrentNoteRef -> env.getCurrentNote()
is PropertyAccess -> {
    val target = evaluate(expr.target, env)
    when (target) {
        is NoteVal -> target.getProperty(expr.property)
        else -> error("Cannot access property on ${target.type}")
    }
}
```

### Note Properties ✅
| Property   | Type | Access | Milestone |
|------------|------|--------|-----------|
| `id`       | String | Read | 6 |
| `path`     | String | Read/Write | 6 |
| `name`     | String | Read/Write | 6 |
| `created`  | DateTime | Read | 6 |
| `modified` | DateTime | Read | 6 |
| `viewed`   | DateTime | Read | 6 |
| `up`       | Note/Undefined | Read | 7 |
| `root`     | Note | Read | 7 |

* id: the firebase id of the note
* name: the first line of the note
* up: parent note (returns UndefinedVal if no parent)
* root: top-level ancestor (returns self if already root)

### Environment Additions ✅
- `currentNote: Note?` parameter added to Environment
- `getCurrentNote(): NoteVal?` method added
- `Environment.withCurrentNote()` and `Environment.withNotesAndCurrentNote()` factory methods

### Integration ✅
- DirectiveFinder.executeDirective accepts optional currentNote parameter
- CurrentNoteViewModel caches and passes current note to directive execution

### Tests ✅
- Lexer tokenizes single dot ✅
- Parser parses standalone dot as CurrentNoteRef ✅
- Parser parses dot with property as PropertyAccess ✅
- `[.]` returns current note when in environment ✅
- `[.]` throws when no current note ✅
- `[.path]` returns current note's path ✅
- `[.id]` returns note ID ✅
- `[.name]` returns first line of content ✅
- `[.created]` returns creation date ✅
- `[.modified]` returns modified date ✅
- `[.viewed]` returns viewed date ✅
- Date properties throw when null ✅
- Unknown property throws error ✅
- Property access on non-NoteVal throws ✅

---

## Milestone 7: Note Mutation & Hierarchy Navigation ✅ COMPLETE

**Target:** `[.path: "x"]`, `[maybe_new(...)]`, `[.append(...)]`, `[.up]`, `[.root]`

### Implementation Summary

**All features completed:**

1. **Lexer**: Added `SEMICOLON` token type for statement separation

2. **AST nodes**: Added `Assignment`, `StatementList`, `VariableRef`, `MethodCall`

3. **Parser** handles:
   - Variable assignment: `[x: 5]`
   - Property assignment: `[.path: "value"]`
   - Statement separation: `[x: 5; y: 10; add(x, y)]`
   - Method calls: `[.append("text")]`

4. **Executor**: Evaluates all new AST types

5. **NoteOperations interface** for mutations with `NoteRepositoryOperations` implementation

6. **NoteMutation tracking**: `MutationType` enum (PATH_CHANGED, CONTENT_CHANGED, CONTENT_APPENDED)

7. **NotePropertyHandler**: Property access/assignment including `.up`, `.root`

8. **NoteMethodHandler**: Method calls `.append()`, `.up(n)`

9. **NoteFunctions**: `new()` and `maybe_new()` functions

10. **DynamicCallAnalyzer**: Extracted from BuiltinRegistry for AST analysis

### Hierarchy Navigation

| Member | Form | Description |
|--------|------|-------------|
| `up` | `.up` | Returns parent note (1 level up) |
| `up` | `.up()` | Returns parent note (0-arg form) |
| `up` | `.up(n)` | Returns ancestor n levels up |
| `root` | `.root` | Returns root ancestor (top-level note) |

**Equivalences:**
- `[.up(0)]` is equivalent to `[.]` (current note)
- `[.up]`, `[.up()]`, `[.up(1)]` all return the parent
- `[.up.up]`, `[.up(2)]` both return the grandparent

**Boundary behavior:**
- If `.up(n)` exceeds the hierarchy depth, returns `UndefinedVal` (not an error)
- `.root` on a top-level note returns that note itself

### Lexer Additions
| Token Type | Pattern |
|------------|---------|
| `SEMICOLON` | `;` |

### AST Nodes (Expression.kt)
```kotlin
data class Assignment(val target: Expression, val value: Expression, override val position: Int) : Expression()
data class StatementList(val statements: List<Expression>, override val position: Int) : Expression()
data class VariableRef(val name: String, override val position: Int) : Expression()
data class MethodCall(val target: Expression, val methodName: String, val args: List<Expression>, val namedArgs: List<NamedArg>, override val position: Int) : Expression()
```

### NoteOperations Interface (NoteOperations.kt)
```kotlin
interface NoteOperations {
    suspend fun createNote(path: String, content: String): Note
    suspend fun findByPath(path: String): Note?
    suspend fun noteExistsAtPath(path: String): Boolean
    suspend fun getNoteById(noteId: String): Note?
    suspend fun updatePath(noteId: String, newPath: String): Note
    suspend fun updateContent(noteId: String, newContent: String): Note
    suspend fun appendToNote(noteId: String, text: String): Note
}
```

### NotePropertyHandler (NotePropertyHandler.kt)
```kotlin
object NotePropertyHandler {
    fun getProperty(noteVal: NoteVal, property: String, env: Environment): DslValue
    fun setProperty(noteVal: NoteVal, property: String, value: DslValue, env: Environment)
    internal fun getUp(note: Note, levels: Int, env: Environment): DslValue
}
```

Handles:
- Read properties: `id`, `path`, `name`, `created`, `modified`, `viewed`, `up`, `root`
- Write properties: `path`, `name`
- Hierarchy navigation via `getUp()` helper (recursive with `UndefinedVal` boundary)

### NoteMethodHandler (NoteMethodHandler.kt)
```kotlin
object NoteMethodHandler {
    fun callMethod(noteVal: NoteVal, methodName: String, args: Arguments, env: Environment, position: Int): DslValue
}
```

Handles:
- `append(text)`: Appends text to note content
- `up()`: Returns parent (delegates to NotePropertyHandler.getUp)
- `up(n)`: Returns ancestor n levels up

### NoteMutation Tracking (NoteMutation.kt)
```kotlin
data class NoteMutation(
    val noteId: String,
    val updatedNote: Note,
    val mutationType: MutationType
)

enum class MutationType {
    PATH_CHANGED,
    CONTENT_CHANGED,
    CONTENT_APPENDED
}
```

### Environment (Environment.kt)
```kotlin
class Environment private constructor(
    private val parent: Environment?,
    private val context: NoteContext
) {
    // Variable management
    fun define(name: String, value: DslValue)
    fun get(name: String): DslValue?
    fun child(): Environment
    fun capture(): Environment

    // Note context
    fun getNotes(): List<Note>?
    fun getCurrentNote(): NoteVal?
    fun getNoteOperations(): NoteOperations?

    // Hierarchy navigation
    fun getNoteById(noteId: String): Note?
    fun getParentNote(note: Note): Note?

    // Mutation tracking
    fun registerMutation(mutation: NoteMutation)
    fun getMutations(): List<NoteMutation>
    fun clearMutations()

    companion object {
        fun withContext(context: NoteContext): Environment
        fun withNotes(notes: List<Note>): Environment
        fun withCurrentNote(currentNote: Note): Environment
        fun withNotesAndCurrentNote(notes: List<Note>, currentNote: Note): Environment
        fun withNoteOperations(noteOperations: NoteOperations): Environment
        fun withAll(notes: List<Note>, currentNote: Note, noteOperations: NoteOperations): Environment
    }
}
```

### NoteContext (NoteContext.kt)
```kotlin
data class NoteContext(
    val notes: List<Note>? = null,
    val currentNote: Note? = null,
    val noteOperations: NoteOperations? = null
) {
    companion object {
        val EMPTY = NoteContext()
    }
}
```

### Tests (all passing)
**NoteMutationTest.kt:**
- Variable assignment and references ✅
- Statement separation with semicolons ✅
- Property assignment on notes (.path, .name) ✅
- Method calls (.append) ✅
- `new()` creates note, errors on duplicate path ✅
- `maybe_new()` is idempotent (returns existing or creates new) ✅
- Error handling for missing note operations ✅

**NotePropertiesTest.kt (hierarchy navigation):**
- `.up` returns parent note ✅
- `.up()` returns parent note (0-arg form) ✅
- `.up(1)` returns parent note ✅
- `.up(2)` returns grandparent note ✅
- `.up(0)` returns current note ✅
- `.up.up` chains correctly ✅
- `.up(n)` returns `UndefinedVal` when n exceeds hierarchy depth ✅
- `.root` returns top-level ancestor ✅
- `.root` on root note returns itself ✅
- `.up.path` accesses parent's path ✅
- `.up(n)` with variable levels ✅

---

## Milestone 8: Lambda

**Target:** `[lambda[parse_date i.path]]`

### Builtins Additions
```kotlin
"parse_date" to { args, env ->
    val str = args[0].asString()
    DateVal(LocalDate.parse(str))
}
```

### Lexer Additions
- `LAMBDA` keyword token

### Parser Additions
- `lambda[...]` syntax
- Implicit `i` parameter inside lambda body

### AST Nodes
```kotlin
data class LambdaExpr(
    val params: List<String>,  // ["i"] for implicit form
    val body: Expression
) : Expression()
```

### Types
```kotlin
data class LambdaVal(
    val params: List<String>,
    val body: Expression,
    val capturedEnv: Environment
) : DslValue()
```

### Executor
```kotlin
is LambdaExpr -> LambdaVal(expr.params, expr.body, env.capture())

// Invoking a lambda:
fun invokeLambda(lambda: LambdaVal, args: List<DslValue>): DslValue {
    val localEnv = lambda.capturedEnv.child()
    lambda.params.zip(args).forEach { (param, arg) ->
        localEnv.define(param, arg)
    }
    return evaluate(lambda.body, localEnv)
}
```

### Integration with Find
```kotlin
"find" to { args, env ->
    val whereLambda = args.named("where") as? LambdaVal
    // ...
    val filtered = notes.filter { note ->
        val pathMatches = pathPattern?.compiledRegex?.matches(note.path) ?: true
        val whereMatches = whereLambda?.let {
            invokeLambda(it, listOf(NoteVal(note))).asBoolean()
        } ?: true
        pathMatches && whereMatches
    }
    ListVal(filtered.map { NoteVal(it) })
}
```

### Tests
- Lambda creation and invocation
- Variable capture works
- `find(where: lambda[...])` filters correctly

---

## Milestone 9: Sort ✅ COMPLETE

**Target:** `[sort(list(3, 1, 4), order: descending)]`, `[sort(find(...), key: lambda[i.path])]`

### Implementation Summary

**Changes made:**
1. Created `ListFunctions.kt` in `builtins/` with `list`, `sort`, and `first` functions
2. Created `SortConstants.kt` with `ascending` and `descending` builtin constants
3. Registered `ListFunctions` and `SortConstants` in `BuiltinRegistry`
4. Implemented comparison logic for all DslValue types without requiring Comparable interface

### Builtins: SortConstants.kt
```kotlin
/**
 * Sort order constants for mobile-friendly sorting.
 *
 * Full words like "ascending" and "descending" are preferred over abbreviations
 * because mobile keyboards with autocorrect make complete words easier to type.
 */
object SortConstants {
    const val ASCENDING = "ascending"
    const val DESCENDING = "descending"

    private val ascendingConstant = BuiltinFunction("ascending") { args, _ ->
        StringVal(ASCENDING)
    }
    private val descendingConstant = BuiltinFunction("descending") { args, _ ->
        StringVal(DESCENDING)
    }
}
```

### Builtins: ListFunctions.kt
```kotlin
/**
 * list(a, b, c, ...) - Create a list from arguments.
 * Returns: A ListVal containing all the arguments
 */
private val listFunction = BuiltinFunction(
    name = "list",
    isDynamic = false
) { args, _ ->
    ListVal(args.positional)
}

/**
 * sort(list, key: lambda, order: ascending|descending) - Sort a list of values.
 *
 * Parameters:
 * - First positional argument: The list to sort
 * - key: Optional lambda to extract sort key from each item
 * - order: Optional `ascending` (default) or `descending` for sort direction
 *
 * Returns: A new sorted ListVal
 */
private val sortFunction = BuiltinFunction(
    name = "sort",
    isDynamic = false
) { args, env ->
    val list = args[0] as? ListVal ?: throw ExecutionException(...)
    val keyLambda = args.getLambda("key")
    val isDescending = when (val orderArg = args["order"]) {
        null -> false
        is StringVal -> orderArg.value == SortConstants.DESCENDING
        else -> throw ExecutionException(...)
    }
    val sorted = sortList(list.items, keyLambda, env)
    ListVal(if (isDescending) sorted.reversed() else sorted)
}

/**
 * first(list) - Get the first item from a list.
 * Returns UndefinedVal if list is empty (graceful undefined access).
 */
private val firstFunction = BuiltinFunction(
    name = "first",
    isDynamic = false
) { args, _ ->
    val list = args[0] as? ListVal ?: throw ExecutionException(...)
    list.items.firstOrNull() ?: UndefinedVal
}
```

### Comparison Logic
Implemented via `compareValues(a: DslValue, b: DslValue): Int` in `ListFunctions`:
- **Same types**: Compare by value semantics
- **Different types**: Compare by type precedence

Type precedence (lowest to highest):
- undefined < boolean < number < string < date < time < datetime < note < list < other

Type-specific comparison:
- Numbers: numerically
- Strings: lexicographically
- Dates/Times/DateTimes: chronologically
- Booleans: false < true
- Notes: by path, then name, then id
- Lists: by size, then element-by-element

### Tests (ListFunctionsTest.kt) ✅ All passing
- list() creates empty list ✅
- list() creates list of numbers/strings/mixed ✅
- list() is classified as static ✅
- ascending/descending constants work ✅
- Sort empty/single-item lists ✅
- Sort notes by default (path) ✅
- Sort with key lambda ✅
- Sort with order ascending/descending ✅
- Sort with key lambda and order descending ✅
- Sort list of numbers ascending/descending ✅
- Sort list of strings ✅
- Type-specific comparison (numbers, strings, dates, times, datetimes, booleans) ✅
- Undefined sorts first ✅
- Cross-type comparison uses type precedence ✅
- Error handling (non-list, invalid order, wrong types) ✅
- first() on non-empty list ✅
- first() on empty list returns undefined ✅
- first() with sort descending ✅
- first(sort(list(...))) integration ✅
- Integration: sort + find + lambda ✅
- Functions classified as static ✅

---

## Milestone 10: View ✅ COMPLETE

**Target:** `[view find(...)]`, `[view sort find(...), ...]`

### Implementation Summary

**Changes made:**
1. Created `ViewVal` type in `runtime/values/ViewVal.kt` with serialization support
2. Added `view()` builtin function to `NoteFunctions.kt`
3. Implemented circular dependency detection via view stack in `NoteContext` and `Environment`
4. Created `ViewContentTracker` in `directives/ViewContentTracker.kt` for position mapping
5. Updated `DirectiveLineRenderer` to render `ViewVal` content with subtle left border indicator
6. Added `ViewIndicator` and `ViewDivider` colors to `DirectiveColors`
7. Updated `DirectiveDisplayRange` with `isView` flag for UI differentiation
8. Added type alias re-export for `ViewVal` in `runtime/DslValue.kt`

### Builtins: NoteFunctions.kt
```kotlin
"view" to { args, env ->
    val listArg = args[0] as? ListVal
        ?: throw ExecutionException("'view' argument must be a list")
    val notes = listArg.items.mapIndexed { index, item ->
        (item as? NoteVal)?.note
            ?: throw ExecutionException("'view' list item at index $index must be a note")
    }
    // Check for circular dependencies
    for (note in notes) {
        if (env.isInViewStack(note.id)) {
            throw ExecutionException("Circular view dependency: ${env.getViewStackPath()} → ${note.id}")
        }
    }
    ViewVal(notes)
}
```

### Types
```kotlin
data class ViewVal(val notes: List<Note>) : DslValue() {
    override val typeName = "view"
    override fun toDisplayString(): String = when (notes.size) {
        0 -> "[empty view]"
        1 -> notes.first().content
        else -> notes.joinToString("\n---\n") { it.content }
    }
}
```

### Environment Additions (for circular detection)
```kotlin
// In NoteContext:
val viewStack: List<String> = emptyList()

// In Environment:
fun getViewStack(): List<String>
fun isInViewStack(noteId: String): Boolean
fun getViewStackPath(): String
fun pushViewStack(noteId: String): Environment
```

### UI: DirectiveLineRenderer.kt
- When result is `ViewVal`, render content inline with subtle left border indicator
- Uses `viewIndicator()` modifier for styling
- Standard directive results still render in dashed boxes

### UI: ViewContentTracker.kt
```kotlin
data class ViewRange(
    val startOffset: Int,
    val endOffset: Int,
    val sourceNoteId: String,
    val sourceStartLine: Int,
    val originalContent: String
)

class ViewContentTracker {
    fun mapOffsetToSource(offset: Int): ViewRange?
    fun updateRangesAfterEdit(editStart: Int, editEnd: Int, newLength: Int)
    fun getModifiedRanges(currentContent: String): List<ViewRange>
    fun findRangesInSelection(selectionStart: Int, selectionEnd: Int): List<ViewRange>

    companion object {
        const val NOTE_DIVIDER = "\n---\n"
        fun buildFromNotes(notes: List<Note>): ViewContentTracker
    }
}
```

### Tests (all passing)
**NoteFunctionsTest.kt:**
- `view with empty list returns empty view` ✅
- `view with single note returns view with that note` ✅
- `view with multiple notes returns view with all notes` ✅
- `view with sorted list preserves order` ✅
- `ViewVal displays empty view message for empty list` ✅
- `ViewVal displays single note content` ✅
- `ViewVal displays multiple notes with dividers` ✅
- `ViewVal serializes and deserializes` ✅
- `empty ViewVal serializes and deserializes` ✅
- `view with non-list argument throws error` ✅
- `view with no arguments throws error` ✅
- `view with list containing non-notes throws error` ✅
- `view is classified as static` ✅
- `view detects circular dependency` ✅
- `view with empty view stack does not throw error` ✅

**ViewContentTrackerTest.kt:**
- `buildFromNotes with empty list creates empty tracker` ✅
- `buildFromNotes with single note creates single range` ✅
- `buildFromNotes with multiple notes creates ranges with dividers` ✅
- `mapOffsetToSource returns null for offset outside all ranges` ✅
- `mapOffsetToSource returns correct range for offset within range` ✅
- `mapOffsetToSource handles offset in divider area` ✅
- `ViewRange contains returns true for offset within range` ✅
- `ViewRange contains returns false for offset outside range` ✅
- `updateRangesAfterEdit shifts ranges after edit point` ✅
- `updateRangesAfterEdit handles deletion` ✅
- `getModifiedRanges returns empty list when content unchanged` ✅
- `getModifiedRanges returns range when content changed within range` ✅
- `getModifiedRanges detects content expansion after updateRangesAfterEdit` ✅
- `findRangesInSelection returns ranges that overlap selection` ✅
- `findRangesInSelection returns only overlapping ranges` ✅

### Future Enhancements
- Editable view sync (propagating edits back to source notes on save)
- Recursive directive execution in viewed notes
- Full integration with refresh directive (Milestone 11)

---

## Milestone 11: Refresh (Target 2)

**Target:** `[refresh view sort find(path: pattern(...)), key: lambda[...], order: descending]`

### Parser Additions
- `refresh` as top-level modifier
- Wraps the inner expression

### AST Nodes
```kotlin
data class RefreshDirective(val inner: Expression) : Expression()
```

### Dependency Tracking
```kotlin
data class DirectiveDependencies(
    val pathPatterns: List<PatternVal>,  // From find(path: ...)
    val noteIds: List<String>            // Direct note references
)

fun extractDependencies(ast: Expression): DirectiveDependencies {
    // Static analysis: walk AST, find all find() calls and note refs
}
```

### Storage
- Store dependencies alongside directive result
- On note change: find directives that depend on changed note's path
- Mark those directives as stale

### Execution
- On view: check if directive is stale
- If stale: re-execute, update cache
- Show loading indicator during re-execution

### Tests
- Dependencies extracted correctly
- Re-execution triggered on dependency change
- Cache invalidation works

---

## Milestone 12: Deferred Execution

**Target:** `[later date]`, `[later[...]]`

### Lexer Additions
| Token Type | Pattern |
|------------|---------|
| `LATER` | `later` keyword |
| `RUN` | `run` keyword |

### Parser Additions
- `later expr` → defer single expression
- `later[...]` → defer entire scope
- `run expr` → force evaluation in deferred scope

### AST Nodes
```kotlin
data class DeferredExpr(val inner: Expression) : Expression()
data class DeferredScope(val inner: Expression) : Expression()
data class RunExpr(val inner: Expression) : Expression()
```

### Types
```kotlin
data class DeferredVal(
    val ast: Expression,
    val capturedEnv: Environment
) : DslValue()
```

### Auto-Propagation
```kotlin
// In builtin functions that take arguments, handle deferred values:
"format" to { args, env ->
    val dateArg = args[0]
    if (dateArg is DeferredVal) {
        // Return deferred that will format when resolved
        DeferredVal(CallExpr("format", listOf(dateArg.ast)), env)
    } else {
        StringVal((dateArg as DateVal).format())
    }
}
```

### Executor
```kotlin
is DeferredExpr -> DeferredVal(expr.inner, env.capture())
is DeferredScope -> DeferredVal(expr.inner, env.capture())
is RunExpr -> evaluate(expr.inner, env)  // Force evaluation

fun resolveDeferred(deferred: DeferredVal): DslValue {
    return evaluate(deferred.ast, deferred.capturedEnv)
}
```

### Tests
- Deferred values don't execute immediately
- Auto-propagation works
- `run` forces evaluation
- Variable capture preserves values

---

## Milestone 13: Schedule (Target 1)

**Target:** `[schedule(daily_at("9:00"), later[maybe_new(...)])]`

### Builtins: DateFunctions.kt Additions
```kotlin
"parse_time" to { args, env ->
    val str = args[0].asString()
    TimeVal(LocalTime.parse(str))
}

"daily_at" to { args, env ->
    val time = args[0] as TimeVal
    ScheduleRuleVal(DailyAt(time.value))
}

"at" to { args, env ->
    val datetime = args[0] as DateTimeVal
    ScheduleRuleVal(At(datetime.value))
}
```

### Types
```kotlin
sealed class ScheduleRule
data class DailyAt(val time: LocalTime) : ScheduleRule()
data class At(val datetime: LocalDateTime) : ScheduleRule()

data class ScheduleRuleVal(val rule: ScheduleRule) : DslValue()
```

### Builtins: NoteFunctions.kt Additions
```kotlin
"schedule" to { args, env ->
    val rule = args[0] as ScheduleRuleVal
    val action = args[1] as DeferredVal

    val directiveText = env.getCurrentDirectiveText()
    val hash = directiveText.sha256()

    // Check for existing schedule with same hash
    val existing = scheduleRepository.findByHash(hash)
    if (existing != null && existing.noteId != env.getCurrentNoteId()) {
        error("Schedule already exists in another note")
    }

    scheduleRepository.upsertSchedule(
        hash = hash,
        noteId = env.getCurrentNoteId(),
        rule = rule.rule,
        deferredAst = serializeAst(action.ast),
        capturedEnv = serializeEnv(action.capturedEnv)
    )

    ScheduleVal(hash, rule.rule)
}
```

### Storage: ScheduleRepository.kt
```kotlin
class ScheduleRepository(private val firestore: FirebaseFirestore) {
    private val collection = firestore.collection("schedules")

    suspend fun upsertSchedule(...)
    suspend fun findByHash(hash: String): Schedule?
    suspend fun getSchedulesForNote(noteId: String): List<Schedule>
    suspend fun getAllActiveSchedules(): List<Schedule>
    suspend fun markExecuted(hash: String, success: Boolean, error: String?)
    suspend fun cancelSchedule(hash: String)
}
```

### Firebase Cloud Function
```typescript
// functions/src/scheduleExecutor.ts

import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

export const executeSchedules = functions.pubsub
    .schedule('every 1 minutes')
    .onRun(async (context) => {
        const now = admin.firestore.Timestamp.now();
        const db = admin.firestore();

        const dueSchedules = await db.collection('schedules')
            .where('status', '==', 'active')
            .where('nextExecution', '<=', now)
            .get();

        for (const doc of dueSchedules.docs) {
            await executeSchedule(doc);
        }
    });

async function executeSchedule(doc: FirebaseFirestore.DocumentSnapshot) {
    const schedule = doc.data();
    try {
        // Deserialize and execute the deferred AST
        const result = await executeDeferredAst(
            schedule.deferredAst,
            schedule.capturedEnv,
            schedule.noteId
        );

        // Update schedule
        await doc.ref.update({
            lastExecution: admin.firestore.FieldValue.serverTimestamp(),
            lastError: null,
            failureCount: 0,
            nextExecution: calculateNextExecution(schedule.rule)
        });
    } catch (error) {
        // Exponential backoff
        const failureCount = schedule.failureCount + 1;
        const backoffMinutes = Math.min(Math.pow(2, failureCount), 60 * 24); // Max 24h

        await doc.ref.update({
            lastExecution: admin.firestore.FieldValue.serverTimestamp(),
            lastError: error.message,
            failureCount,
            nextExecution: new Date(Date.now() + backoffMinutes * 60 * 1000)
        });
    }
}
```

### WorkManager Fallback (Android)
```kotlin
class ScheduleExecutorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduleRepository = // inject
        val executor = // inject

        val dueSchedules = scheduleRepository.getDueSchedules()

        for (schedule in dueSchedules) {
            try {
                executor.executeDeferred(schedule.deferredAst, schedule.capturedEnv)
                scheduleRepository.markExecuted(schedule.hash, true, null)
            } catch (e: Exception) {
                scheduleRepository.markExecuted(schedule.hash, false, e.message)
            }
        }

        return Result.success()
    }
}

// Schedule periodic execution
fun scheduleBackgroundExecution(context: Context) {
    val request = PeriodicWorkRequestBuilder<ScheduleExecutorWorker>(
        15, TimeUnit.MINUTES  // Minimum for periodic work
    ).build()

    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            "schedule_executor",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
}
```

### UI: Schedule Indicator
- Notes with active schedules show indicator icon
- Tap indicator to see schedule status
- Global schedule view lists all schedules

### On Note Deletion
```kotlin
fun deleteNote(noteId: String) {
    val schedules = scheduleRepository.getSchedulesForNote(noteId)
    if (schedules.isNotEmpty()) {
        // Show confirmation dialog
        showScheduleDeletionWarning(schedules) { confirmed ->
            if (confirmed) {
                schedules.forEach { scheduleRepository.cancelSchedule(it.hash) }
                noteRepository.deleteNote(noteId)
            }
        }
    } else {
        noteRepository.deleteNote(noteId)
    }
}
```

### Tests
- Schedule creation and hash identity
- Duplicate detection across notes
- Execution triggers correctly
- Retry with backoff on failure
- Cleanup on note deletion

---

## Later Milestones

### Variables and Conditionals
- Parser: `:` for definition, `;` for separation
- Executor: variable scoping in `Environment`
- Builtins: comparison functions, `if`

### List Operations
- `first`, `list`, `maybe`
- Special values: `undefined`, `empty`
- **Design principle**: `first` on empty list returns `undefined` (not error)

### Button
- UI rendering
- Tap to execute
- Error state display

### Polish
- Parse error display on focus-out
- Global error navigation
- Schedule view screen
- Retry policy visibility
- Comments (`#`)
- Bracket escaping (`[[`, `]]`)
- Timezone parameter support
- `run` in deferred scope
- `lambda(params)[...]` named parameters

### Design Principles to Apply Throughout
- **Graceful undefined access**: Accessing non-existent data returns `undefined` rather than errors:
  - Out-of-bounds list access (e.g., `first` on empty list) → `undefined`
  - Missing properties → `undefined`
  - Hierarchy navigation beyond available ancestors (e.g., `.up` on root) → `undefined`
- **Minimize special characters**: Prefer built-in identifiers/functions over symbolic operators

---

## Integration Points

### NoteRepository
```kotlin
// Additions needed:
suspend fun getAllNotes(): List<Note>
suspend fun findByPath(path: String): Note?
suspend fun noteExistsAtPath(path: String): Boolean
suspend fun appendToNote(noteId: String, text: String)
suspend fun updateNoteContent(noteId: String, content: String, startLine: Int)
```

### CurrentNoteScreen
- Detect directives in note content
- Render `DirectiveChip` composables
- Handle editable view content tracking
- Sync viewed content on save

### LineGutter (rendering/LineGutter.kt)
- Computes gutter-specific layouts via `computeGutterLineLayouts()` that account for expanded directive edit row gaps
- Uses `GutterLayoutData` class to pass layout computation parameters to gesture handlers
- Gesture handling uses computed layouts for accurate hit testing regardless of directive expansion state

### EditorController
- Awareness of directive boundaries for undo/redo
- Track view content ranges
- **Selection preservation during IME sync**: `updateLineContent()` only clears selection when content actually changes.
  This prevents IME's `finishComposingText()` from clearing gutter selections during drag operations.

### NoteLineTracker
- May need awareness of directive vs. regular content

---

## Testing Strategy

### Unit Tests
- Lexer: all token types
- Parser: all syntax constructs
- Executor: all builtins and evaluation rules
- Types: serialization/deserialization

### Integration Tests
- End-to-end directive execution
- Firestore subcollection operations
- Schedule creation and execution

### UI Tests
- Directive chip rendering
- Collapse/expand behavior
- View content editing and sync
