# DSL Implementation Plan

Detailed implementation plan for the TaskBrain DSL, based on the spec and implementation decisions.

---

## Phase 0: Foundation Infrastructure

### 0.1 Package Structure

```
app/src/main/java/org/alkaline/taskbrain/dsl/
├── lexer/
│   ├── Lexer.kt              # Tokenizes input string
│   ├── Token.kt              # Token data class
│   └── TokenType.kt          # Enum of token types
├── parser/
│   ├── Parser.kt             # Recursive descent parser
│   └── ast/
│       ├── AstNode.kt        # Base AST node
│       ├── Expression.kt     # Expression node types
│       └── Directive.kt      # Top-level directive wrapper
├── types/
│   ├── DslValue.kt           # Sealed class for runtime values
│   ├── DslType.kt            # Type enumeration
│   └── Pattern.kt            # Pattern matching implementation
├── executor/
│   ├── Executor.kt           # AST interpreter
│   ├── Environment.kt        # Variable scopes and bindings
│   └── builtins/
│       ├── BuiltinRegistry.kt
│       ├── DateFunctions.kt
│       ├── ArithmeticFunctions.kt
│       ├── ComparisonFunctions.kt
│       ├── StringFunctions.kt
│       ├── ListFunctions.kt
│       └── NoteFunctions.kt
├── storage/
│   ├── DirectiveResultRepository.kt
│   └── ScheduleRepository.kt
└── ui/
    ├── DirectiveChip.kt      # Composable for directive display
    ├── DirectiveRenderer.kt  # Renders execution results
    └── ViewContentTracker.kt # Tracks editable view ranges
```

### 0.2 Firestore Schema Additions

**Subcollection: `notes/{noteId}/directiveResults/{directiveHash}`**
```
{
  result: any,              // Serialized DslValue
  resultType: string,       // Type discriminator for deserialization
  executedAt: timestamp,
  error: string | null,
  collapsed: boolean
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

## Milestone 1: Literals

**Target:** `[1]`, `["hello"]`

### Lexer
| Token Type | Pattern |
|------------|---------|
| `LBRACKET` | `[` |
| `RBRACKET` | `]` |
| `NUMBER` | `[0-9]+(\.[0-9]+)?` |
| `STRING` | `"([^"\\]|\\.)*"` |

### Parser
- `parseDirective()` → expects `[`, expression, `]`
- `parseExpression()` → for now, just `parseLiteral()`
- `parseLiteral()` → NUMBER or STRING token

### AST Nodes
```kotlin
sealed class Expression : AstNode
data class NumberLiteral(val value: Double) : Expression()
data class StringLiteral(val value: String) : Expression()
```

### Executor
```kotlin
fun evaluate(expr: Expression, env: Environment): DslValue = when (expr) {
    is NumberLiteral -> NumberVal(expr.value)
    is StringLiteral -> StringVal(expr.value)
    // ...
}
```

### Types
```kotlin
sealed class DslValue {
    data class NumberVal(val value: Double) : DslValue()
    data class StringVal(val value: String) : DslValue()
    // ... more to come
}
```

### UI Integration
- Regex scan note content for `\[.*?\]` (non-greedy, handling nesting later)
- Replace directive text with `DirectiveChip` composable
- Chip shows result; tap to expand/collapse directive source
- On save: parse, execute, store result in subcollection

### Storage
- `DirectiveResultRepository.saveResult(noteId, directiveHash, result)`
- `DirectiveResultRepository.getResults(noteId): Map<String, DirectiveResult>`

### Tests
- Lexer: tokenizes literals correctly
- Parser: produces correct AST for `[1]`, `["hello"]`
- Executor: evaluates to correct values
- Round-trip: parse → execute → serialize → deserialize

---

## Milestone 2: Function Calls

**Target:** `[date]`, `[iso8601 date]`

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
"now" to { args, env -> DateTimeVal(LocalDateTime.now()) }
"time" to { args, env -> TimeVal(LocalTime.now()) }
"iso8601" to { args, env ->
    val date = args[0] as DateVal
    StringVal(date.value.format(DateTimeFormatter.ISO_LOCAL_DATE))
}
```

### Types Additions
```kotlin
data class DateVal(val value: LocalDate) : DslValue()
data class TimeVal(val value: LocalTime) : DslValue()
data class DateTimeVal(val value: LocalDateTime) : DslValue()
```

### Tests
- `[date]` returns today's date
- `[iso8601 date]` returns formatted string
- Right-to-left nesting works correctly

---

## Milestone 3: Parentheses & Named Arguments

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

## Milestone 4: Patterns

**Target:** `[pattern(digit*4 "-" digit*2 "-" digit*2)]`

### Parser Additions
- Special parsing mode inside `pattern(...)`
- Character classes: `digit`, `letter`, `space`, `punct`, `any`
- Quantifiers: `*4`, `*any`, `*(0..5)`, `*(1..)`
- Literals in quotes: `"-"`

### AST Nodes
```kotlin
sealed class PatternElement
data class CharClass(val type: CharClassType) : PatternElement()
data class PatternLiteral(val value: String) : PatternElement()
data class Quantified(val element: PatternElement, val quantifier: Quantifier) : PatternElement()

sealed class Quantifier
data class Exact(val n: Int) : Quantifier()
data class Range(val min: Int, val max: Int?) : Quantifier()  // null max = unbounded
object Any : Quantifier()
```

### Types
```kotlin
data class PatternVal(
    val elements: List<PatternElement>,
    val compiledRegex: Regex
) : DslValue()
```

### Pattern Compilation
```kotlin
fun compilePattern(elements: List<PatternElement>): Regex {
    val pattern = elements.joinToString("") { elementToRegex(it) }
    return Regex(pattern)
}

fun elementToRegex(el: PatternElement): String = when (el) {
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
```

### Tests
- `pattern(digit*4 "-" digit*2 "-" digit*2)` matches "2026-01-15"
- Quantifier variations work correctly
- Non-matching strings return false

---

## Milestone 5: Find with Pattern

**Target:** `[find(path: pattern(digit*4 "-" digit*2 "-" digit*2))]`

### Builtins: NoteFunctions.kt
```kotlin
"find" to { args, env ->
    val pathPattern = args.named("path") as? PatternVal
    val whereLambda = args.named("where") as? LambdaVal  // Later milestone

    val notes = noteRepository.getAllNotes()  // Or optimized query
    val filtered = notes.filter { note ->
        val pathMatches = pathPattern?.compiledRegex?.matches(note.path) ?: true
        // whereLambda check comes in Milestone 7
        pathMatches
    }
    ListVal(filtered.map { NoteVal(it) })
}
```

### Types Additions
```kotlin
data class NoteVal(val note: Note) : DslValue()
data class ListVal(val items: List<DslValue>) : DslValue()
```

### Query Optimization
- For prefix patterns (e.g., `"journal/" any*(1..)`), use Firestore range query
- For regex patterns, fetch candidates and filter client-side
- Consider indexing common path prefixes

### Tests
- Find returns matching notes
- Empty list when no matches
- Pattern matching is correct

---

## Milestone 6: Note Properties

**Target:** `[.path]`, `[i.path]`, `[parse_date i.path]`

### Lexer Additions
| Token Type | Pattern |
|------------|---------|
| `DOT` | `.` |

### Parser Additions
- `.` alone → current note reference
- `.prop` → current note property access
- `expr.prop` → property access on expression result

### AST Nodes
```kotlin
object CurrentNoteRef : Expression()
data class PropertyAccess(val target: Expression, val property: String) : Expression()
```

### Executor
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

### Note Properties
| Property | Type | Access |
|----------|------|--------|
| `path` | String | Read/Write |
| `content` | String | Read/Write |
| `created` | Date | Read |
| `modified` | Date | Read |
| `viewed` | Date | Read |

### Builtins Additions
```kotlin
"parse_date" to { args, env ->
    val str = args[0].asString()
    DateVal(LocalDate.parse(str))
}
```

### Tests
- `[.path]` returns current note's path
- `[.modified]` returns date
- Property access on found notes works

---

## Milestone 7: Lambda

**Target:** `[lambda[parse_date i.path]]`

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

## Milestone 8: Sort

**Target:** `[sort(find(...), key: lambda[parse_date i.path], order: desc)]`

### Builtins: ListFunctions.kt
```kotlin
"sort" to { args, env ->
    val list = args[0] as ListVal
    val keyLambda = args.named("key") as? LambdaVal
    val order = args.named("order")?.asString() ?: "asc"

    val sorted = list.items.sortedBy { item ->
        keyLambda?.let { invokeLambda(it, listOf(item)) }
            ?: item
    }

    ListVal(if (order == "desc") sorted.reversed() else sorted)
}
```

### Comparison
- DslValue needs `Comparable` implementation
- Dates compare chronologically
- Strings compare lexicographically
- Numbers compare numerically

### Tests
- Sort by extracted key
- Ascending and descending order
- Sort stability

---

## Milestone 9: View

**Target:** `[view find(...)]`, `[view sort find(...), ...]`

### Builtins: NoteFunctions.kt
```kotlin
"view" to { args, env ->
    val notes = args[0] as ListVal
    ViewVal(notes.items.map { (it as NoteVal).note })
}
```

### Types
```kotlin
data class ViewVal(val notes: List<Note>) : DslValue()
```

### UI: DirectiveRenderer.kt
- When result is `ViewVal`, render notes inline
- Each note's content separated by divider
- Track source note for each text range

### UI: ViewContentTracker.kt
```kotlin
data class ViewRange(
    val startOffset: Int,
    val endOffset: Int,
    val sourceNoteId: String,
    val sourceStartLine: Int
)

class ViewContentTracker {
    private val ranges = mutableListOf<ViewRange>()

    fun mapOffsetToSource(offset: Int): ViewRange?
    fun updateRangesAfterEdit(editStart: Int, editEnd: Int, newLength: Int)
}
```

### Editable View Sync (on save)
```kotlin
fun syncViewedContentOnSave(
    directiveResult: ViewVal,
    currentContent: String,
    tracker: ViewContentTracker
) {
    tracker.ranges.forEach { range ->
        val editedContent = currentContent.substring(range.startOffset, range.endOffset)
        if (editedContent != range.originalContent) {
            noteRepository.updateNoteContent(range.sourceNoteId, editedContent, range.sourceStartLine)
        }
    }
}
```

### Circular Dependency Detection
```kotlin
class Executor {
    private val viewStack = mutableListOf<String>()  // Note IDs being viewed

    fun evaluateView(notes: List<Note>): ViewVal {
        notes.forEach { note ->
            if (note.id in viewStack) {
                error("Circular view dependency: ${viewStack.joinToString(" → ")} → ${note.id}")
            }
            viewStack.add(note.id)
            // Execute directives in viewed note
            viewStack.removeLast()
        }
        // ...
    }
}
```

### Tests
- View renders note content
- Circular dependency detected
- Edits propagate back on save

---

## Milestone 10: Refresh (Target 2)

**Target:** `[refresh view sort find(path: pattern(...)), key: lambda[...], order: desc]`

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

## Milestone 11: Deferred Execution

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
// In builtin functions:
"iso8601" to { args, env ->
    val dateArg = args[0]
    if (dateArg is DeferredVal) {
        // Return deferred that will format when resolved
        DeferredVal(CallExpr("iso8601", listOf(dateArg.ast)), env)
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

## Milestone 12: Note Mutation

**Target:** `[.path: "x"]`, `[maybe_new(...)]`, `[.append(...)]`

### Parser Additions
- Assignment syntax: `.prop: value` or `expr.prop: value`
- Statement separation with `;`

### AST Nodes
```kotlin
data class Assignment(val target: Expression, val value: Expression) : Expression()
data class StatementList(val statements: List<Expression>) : Expression()
```

### Builtins: NoteFunctions.kt
```kotlin
"new" to { args, env ->
    val path = args.named("path")!!.asString()
    val content = args.named("content")?.asString() ?: ""

    if (noteRepository.noteExistsAtPath(path)) {
        error("Note already exists at path: $path")
    }

    val note = noteRepository.createNote(path, content)
    NoteVal(note)
}

"maybe_new" to { args, env ->
    val path = args.named("path")!!.asString()
    val maybeContent = args.named("maybe_content")?.asString() ?: ""

    val existing = noteRepository.findByPath(path)
    if (existing != null) {
        NoteVal(existing)
    } else {
        val note = noteRepository.createNote(path, maybeContent)
        NoteVal(note)
    }
}
```

### Method-Style Calls
```kotlin
// .append on notes
fun NoteVal.append(text: String): NoteVal {
    noteRepository.appendToNote(note.id, text)
    return this  // Return note for chaining
}
```

### Executor
```kotlin
is Assignment -> {
    val value = evaluate(expr.value, env)
    when (val target = expr.target) {
        is PropertyAccess -> {
            val targetObj = evaluate(target.target, env)
            when (targetObj) {
                is NoteVal -> targetObj.setProperty(target.property, value)
                else -> error("Cannot assign to property on ${targetObj.type}")
            }
        }
        else -> error("Invalid assignment target")
    }
    value
}
```

### Tests
- `new` creates note, errors on duplicate
- `maybe_new` is idempotent
- `.append` adds content
- Property assignment works

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

### EditorController
- Awareness of directive boundaries for undo/redo
- Track view content ranges

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
