# Directive Caching — Detailed Design

This document provides detailed design specifications for the directive caching system. For the high-level overview, see [directive-caching.md](directive-caching.md).

## Dependency Tracking Approach

Every directive needs dependency tracking, not just views. This enables:
1. Any directive can expose its dependencies
2. Directives that reference other directives inherit transitive dependencies
3. Views are just one case of this general pattern

Directives determine their dependencies through **AST analysis** of their code. Dependencies are surfaced so other directives can compute transitive dependencies.

### Dependency Sources

| Source | How dependencies are determined |
|--------|--------------------------------|
| Inline code | AST analysis of the directive's expression |
| Lambda reference | The referenced directive exposes its dependencies |
| Transitive | If directive A calls directive B, A inherits B's dependencies |

### Example: Lambda Reference Dependencies

```
Note X: [[gt(i.modified, add_days(date, neg(7)))]]  // Directive "myFilter"
Note Y: [view find(where: myFilter)]                    // References myFilter
```

- Directive "myFilter" analyzes its AST → depends on `FIELD_MODIFIED`
- Directive in Note Y references "myFilter" → inherits `FIELD_MODIFIED` dependency
- Note Y's view cache invalidates when any note's `modified` changes

### Deferred: Dynamic Lambdas

Lambdas passed dynamically (not determinable until runtime) are out of scope for now. Will address later if needed.

## Time-Based Refresh

### Time Types

| Type | Meaning | Example |
|------|---------|---------|
| `date` | Just the date (no time) | `2024-06-01` |
| `time` | Just the time (no date) | `14:30:00` |
| `datetime` | Full date and time | `2024-06-01 14:30:00` |

### Execution Blocks and Implicit Lambdas

`[...]` meaning depends on context:
- **Top-level:** Directive (executes immediately)
- **As argument:** Deferred block / lambda (executes when called/triggered)

| Wrapper | Meaning | Example |
|---------|---------|---------|
| `once[...]` | Compute once, cache forever (snapshot) | `[once[datetime]]` |
| `refresh[...]` | Recompute at analyzed trigger times | `[refresh[if(time.gt("12:00"), X, Y)]]` |
| `button(label, [...])` | Execute on user click | `[button("Create", [new(path:"inbox")])]` |
| `schedule(when, [...])` | Execute on schedule | `[schedule(daily, [new(path:date)])]` |

**Lambdas:** The `lambda` keyword is replaced by `[...]` in all contexts:

```
// Old syntax (deprecated)
[find(where: lambda[i.path.startsWith("inbox")])]

// New syntax - [...] in argument position is implicitly a deferred block
[find(where: [i.path.startsWith("inbox")])]
```

The inner `[...]` is implicitly a deferred block that receives `i` as its parameter.

### `[...]` as Regular AST Node

The deferred block `[...]` is a regular AST node, not special syntax. This has implications:

**Parentheses equivalence:** Functions accepting a single deferred block can use either syntax:
```
[once[date]]        # Block syntax
[once([date])]      # Parentheses syntax - equivalent
```

Both produce the same AST and should hash to the same cache key.

**Immediate invocation:** Since `[...]` creates a callable lambda, it can be invoked immediately:
```
[[i.path](.)]       # Create lambda with parameter i, call with current note
[.path]             # Equivalent - direct property access
```

**Implications for caching:**
- AST analysis normalizes equivalent forms before hashing
- `once[date]` and `once([date])` share the same cache entry
- Immediately-invoked lambdas are analyzed as if inlined (their dependencies are the lambda body's dependencies evaluated in the call context)

### Trigger Analysis: Backtrace + Verify

For `refresh` directives, the system finds flip points using backtrace analysis:

```
1. Walk AST to find all time/date literals
2. Resolve variables through constant propagation
3. For each literal:
   a. Backtrace to the time input (time, date, datetime)
   b. Reverse any math operations (plus → minus)
   c. This gives a candidate trigger time
4. For each candidate, evaluate whole expression at ±1 minute
5. If results differ → confirmed flip; add to trigger list
```

**Example:**
```
[refresh[if(time.plus(minutes:10).gt("12:00"), X, Y)]]
```

| Step | Action |
|------|--------|
| Find literal | `"12:00"` |
| Backtrace path | `time` → `.plus(minutes:10)` → `.gt("12:00")` |
| Reverse math | `12:00 - 10min = 11:50` |
| Candidate | `11:50` |
| Verify 11:49 | `11:49 + 10min = 11:59 > 12:00?` → false |
| Verify 11:51 | `11:51 + 10min = 12:01 > 12:00?` → true |
| Result | **Flip confirmed at 11:50** |

**Example with variables:**
```
[refresh[start: "09:00"; end: "17:00"; if(time.gt(start).and(time.lt(end)), "Working", "Off")]]
```

| Step | Action |
|------|--------|
| Resolve variables | `start = "09:00"`, `end = "17:00"` |
| Candidates | 09:00, 17:00 |
| Verify 09:00 | 08:59 → Off, 09:01 → Working → flip ✓ |
| Verify 17:00 | 16:59 → Working, 17:01 → Off → flip ✓ |

**Example where literal doesn't cause flip (OR logic):**
```
[refresh[if(time.gt("09:00").or(time.gt("12:00")), X, Y)]]
```

| Candidate | Before | After | Flip? |
|-----------|--------|-------|-------|
| 09:00 | Y | X | ✓ Yes |
| 12:00 | X | X | ✗ No (shadowed) |

### Variable Resolution for Time Literals

| Variable Source | Analysis Approach |
|-----------------|-------------------|
| Literal in same directive | Constant propagation at parse time |
| Computed in same directive | Evaluate binding expression at analysis time |
| Note property (`.xxx`) | Evaluate with current note; re-analyze if property changes |
| External directive | Load dependency's result; re-analyze if dependency changes |

### Trigger Types by Time Expression

| Expression | Trigger Behavior |
|------------|------------------|
| `date` comparisons | One-time trigger on that date |
| `time` comparisons | Daily recurring trigger at analyzed time(s) |
| `datetime` comparisons | One-time trigger at that exact moment |
| `date.plus(...)` comparisons | Backtrace math, trigger at computed date |
| `time.plus(...)` comparisons | Backtrace math, trigger at computed time (daily) |

### Constraints

- **Maximum refresh frequency:** Hourly (no sub-hour periodic refresh)
- **Intra-hour triggers allowed:** Via specific time comparisons (e.g., trigger at 14:30)
- **Live/constant refresh:** Disallowed (no "every minute" or "always")
- **Bare time values are errors:** `[datetime]`, `[time]`, `[date]` without `once[...]` or `refresh[...]` → error
- **Functions returning time values:** If a function returns date/time/datetime, result must be wrapped in `once[...]` or `refresh[...]` → error otherwise

```
[datetime]                              // ERROR: must use once[...] or refresh[...]
[once[datetime]]                        // OK: snapshot
[refresh[datetime]]                     // ERROR: no comparison to analyze triggers from
[refresh[if(time.gt("12:00"), X, Y)]]   // OK: trigger at 12:00

[my_time_func()]                        // ERROR if returns time/date/datetime
[once[my_time_func()]]                  // OK: snapshot the result
```

## Dependency Types in Detail

### 1. Content Dependencies (per-note hashes)

These track what content was actually used from specific notes.

| Dependency Type | What it tracks | Hash scope |
|----------------|----------------|------------|
| `FIRST_LINE` | First line of content (name) | Per viewed note |
| `NON_FIRST_LINE` | Content after first line | Per viewed note |

**Hash strategy:** Hash the actual content of viewed notes.
- `firstLineHash = hash(note.content.firstLine)`
- `nonFirstLineHash = hash(note.content.dropFirstLine)`

### 2. Metadata Dependencies (on-demand collection hashes)

These track which metadata fields were accessed in query predicates.

| Dependency Type | Example usage | Hash scope |
|----------------|---------------|------------|
| `FIELD_PATH` | `find(path: ...)`, `note.path` | All notes |
| `FIELD_MODIFIED` | `find(where: [gt(i.modified, ...)])` | All notes |
| `FIELD_CREATED` | `find(where: [gt(i.created, ...)])` | All notes |
| `FIELD_VIEWED` | `find(where: [gt(i.viewed, ...)])` | All notes |

**Hash strategy:** On-demand, lazy, field-level hashing.
- Computed fresh on each staleness check (on note open, on save)
- Only compute hashes for fields the directive actually depends on
- Short-circuit: stop checking at first stale field
- Stateless - no version tracking or mutation hooks needed
- O(n) per field, but most directives depend on 1-2 fields
- Typical cost: ~1-2ms for 200 notes (imperceptible)

```kotlin
fun computeFieldHash(notes: List<Note>, field: MetadataField): String {
    val values = notes.sortedBy { it.id }.map { "${it.id}:${field.getValue(it)}" }
    return hash(values.joinToString("\n"))
}
```

### 3. Special Dependencies

| Dependency Type | When triggered | Hash scope |
|----------------|----------------|------------|
| `NOTE_EXISTENCE` | `find()` is used (note creation/deletion affects results) | All note IDs |

### 4. Hierarchy Dependencies (for `.up`, `.root`)

When a directive accesses properties through hierarchy navigation (`.up`, `.up(n)`, `.root`), it creates a **resolved hierarchy dependency**.

**Challenge:** `.up` resolves to different notes depending on where the directive is located. The dependency must track:
1. The hierarchy path used (e.g., `.up`, `.up(2)`, `.root`)
2. The note it resolved to at cache time
3. Which field was accessed on that note

**Staleness scenarios:**

| Scenario | Detection |
|----------|-----------|
| Parent note's field changed | Field hash mismatch |
| Note moved to different parent | Resolved note ID changed |
| Parent note deleted | Resolved ID is null (was non-null) |

**Data structure:**

```kotlin
data class HierarchyDependency(
    val path: HierarchyPath,           // The hierarchy path used
    val resolvedNoteId: String?,       // Note ID it resolved to (null if didn't exist)
    val field: NoteField?,             // Which field was accessed (null = note itself)
    val fieldHash: String?,            // Hash of field value at cache time
)

enum class HierarchyPath {
    UP,        // .up or .up()
    UP_N(n),   // .up(n) where n > 1
    ROOT,      // .root
}

enum class NoteField {
    NAME, PATH, MODIFIED, CREATED, VIEWED
}
```

**Examples:**

| Code | Hierarchy Dependency |
|------|---------------------|
| `.up` | `HierarchyDependency(UP, "noteXYZ", null, null)` |
| `.up.modified` | `HierarchyDependency(UP, "noteXYZ", MODIFIED, "abc123")` |
| `.up(2).path` | `HierarchyDependency(UP_N(2), "noteABC", PATH, "def456")` |
| `.root.name` | `HierarchyDependency(ROOT, "noteROOT", NAME, "ghi789")` |

## Transitive Dependencies

When View A renders Note B, and Note B contains View C:

```
View A
└── renders Note B
    └── contains View C
        └── depends on FIELD_MODIFIED
```

View A inherits View C's dependencies. A's cache is invalidated if `FIELD_MODIFIED` collection hash changes.

When rendering a note's content, dependencies from nested views are collected and merged into the parent view's dependencies.

## Code Analysis: What Triggers Each Dependency

### Property Access Tracking

| Code | Dependencies Added |
|------|-------------------|
| `note.name` or `note.content` first line | `FIRST_LINE` for that note |
| `note.content` beyond first line | `NON_FIRST_LINE` for that note |
| `note.path` | `FIELD_PATH` |
| `note.modified` | `FIELD_MODIFIED` |
| `note.created` | `FIELD_CREATED` |
| `note.viewed` | `FIELD_VIEWED` |
| `.up`, `.up(n)`, `.root` | `HierarchyDependency` with resolved note ID |
| `.up.path`, `.root.modified`, etc. | `HierarchyDependency` with resolved note ID + field hash |

### Function Call Tracking

| Code | Dependencies Added |
|------|-------------------|
| `find(...)` | `NOTE_EXISTENCE` |
| `find(path: ...)` | `FIELD_PATH` |
| `find(name: ...)` | `FIRST_LINE` for all matching notes |
| `find(where: [...])` | Analyze lambda body for field access |
| `view(notes)` | Content deps for viewed notes + transitive deps |

**Current note exclusion:** The note containing a directive never appears in its own find() results. This prevents accidental infinite loops.

## Data Structures

### DirectiveDependencies

```kotlin
data class DirectiveDependencies(
    // Per-note content dependencies
    val firstLineNotes: Set<String>,      // Note IDs where first line was accessed
    val nonFirstLineNotes: Set<String>,   // Note IDs where content beyond first line was accessed

    // Metadata dependencies (true = this directive depends on this field)
    val dependsOnPath: Boolean,
    val dependsOnModified: Boolean,
    val dependsOnCreated: Boolean,
    val dependsOnViewed: Boolean,
    val dependsOnNoteExistence: Boolean,  // Any find() was used

    // Hierarchy dependencies (for .up, .root access)
    val hierarchyDeps: List<HierarchyDependency>,
) {
    /** Merge with another DirectiveDependencies (for transitive dependencies) */
    fun merge(other: DirectiveDependencies): DirectiveDependencies = DirectiveDependencies(
        firstLineNotes = firstLineNotes + other.firstLineNotes,
        nonFirstLineNotes = nonFirstLineNotes + other.nonFirstLineNotes,
        dependsOnPath = dependsOnPath || other.dependsOnPath,
        dependsOnModified = dependsOnModified || other.dependsOnModified,
        dependsOnCreated = dependsOnCreated || other.dependsOnCreated,
        dependsOnViewed = dependsOnViewed || other.dependsOnViewed,
        dependsOnNoteExistence = dependsOnNoteExistence || other.dependsOnNoteExistence,
        hierarchyDeps = hierarchyDeps + other.hierarchyDeps,
    )
}
```

### MetadataHashes (stored in cached result)

```kotlin
data class MetadataHashes(
    val pathHash: String?,        // null if directive doesn't depend on path
    val modifiedHash: String?,
    val createdHash: String?,
    val viewedHash: String?,
    val existenceHash: String?,   // hash of sorted note IDs
)
```

### CachedDirectiveResult

```kotlin
data class CachedDirectiveResult(
    val result: DslValue,                               // The computed value
    val dependencies: DirectiveDependencies,

    // Hashes at time of caching (only for fields this directive depends on)
    val noteContentHashes: Map<String, ContentHashes>,  // noteId -> content hashes
    val metadataHashes: MetadataHashes,                 // collection-level hashes
)

data class ContentHashes(
    val firstLineHash: String?,   // null if not depended on
    val nonFirstLineHash: String?, // null if not depended on
)
```

## Staleness Check Algorithm

```kotlin
fun isStale(
    cached: CachedDirectiveResult,
    currentNotes: List<Note>,
    currentNote: Note? = null  // Required for hierarchy dependency checks
): Boolean {
    val deps = cached.dependencies
    val cachedHashes = cached.metadataHashes

    // Check metadata dependencies (lazy, on-demand hashing)
    // Only compute hash if directive depends on this field
    // Short-circuit: return true at first stale field

    if (deps.dependsOnNoteExistence) {
        val currentHash = MetadataHasher.computeExistenceHash(currentNotes)
        if (cachedHashes.existenceHash != currentHash) return true
    }

    if (deps.dependsOnPath) {
        val currentHash = MetadataHasher.computePathHash(currentNotes)
        if (cachedHashes.pathHash != currentHash) return true
    }

    if (deps.dependsOnModified) {
        val currentHash = MetadataHasher.computeModifiedHash(currentNotes)
        if (cachedHashes.modifiedHash != currentHash) return true
    }

    if (deps.dependsOnCreated) {
        val currentHash = MetadataHasher.computeCreatedHash(currentNotes)
        if (cachedHashes.createdHash != currentHash) return true
    }

    if (deps.dependsOnViewed) {
        val currentHash = MetadataHasher.computeViewedHash(currentNotes)
        if (cachedHashes.viewedHash != currentHash) return true
    }

    // Check per-note content dependencies
    for (noteId in deps.firstLineNotes) {
        val note = currentNotes.find { it.id == noteId } ?: return true  // Note deleted
        val currentHash = hashFirstLine(note.content)
        val cachedHash = cached.noteContentHashes[noteId]?.firstLineHash ?: return true
        if (currentHash != cachedHash) return true
    }

    for (noteId in deps.nonFirstLineNotes) {
        val note = currentNotes.find { it.id == noteId } ?: return true
        val currentHash = hashNonFirstLine(note.content)
        val cachedHash = cached.noteContentHashes[noteId]?.nonFirstLineHash ?: return true
        if (currentHash != cachedHash) return true
    }

    // Check hierarchy dependencies (.up, .root)
    for (dep in deps.hierarchyDeps) {
        if (isHierarchyStale(dep, currentNote, currentNotes)) return true
    }

    return false
}
```

## Inline Editing of Viewed Content

### The Scenario

```
Note A: "My Dashboard"
├── [view find(path: "inbox")]
│   └── renders Note B: "Buy groceries"
│   └── renders Note C: "Call mom"
```

User is viewing Note A, but edits "Buy groceries" → "Buy groceries today".

### Requirements

1. **Edit targets the viewed note**: The edit saves to Note B, not Note A
2. **Cache invalidation propagates**: Note B's content changed, so any view depending on Note B should invalidate
3. **No refresh artifacts for active editor**: Note A's view should NOT flicker/re-render while user is editing inline

### Edit Origin Tracking

Track which note originated an edit, and suppress cache invalidation for views in that note.

```kotlin
data class EditContext(
    val editedNoteId: String,      // Note B - the note whose content changed
    val originatingNoteId: String, // Note A - the note where user was editing
    val editStartTime: Long,       // For timeout-based fallback
)
```

**Cache invalidation rule:**
- When Note B's content changes with origin Note A:
  - Invalidate all caches that depend on Note B
  - **EXCEPT** caches in Note A (the originating note)
- Note A's view continues displaying the just-edited content without refresh

**When does Note A's view eventually refresh?**
- When user navigates away and back
- When another change occurs that Note A depends on (not originated from A)
- When user explicitly requests refresh

### Data Flow

```
User edits "Buy groceries" → "Buy groceries today" in Note A's view
│
├─ 1. Identify edit target: Note B (the viewed note)
├─ 2. Save Note B with EditContext(editedNoteId=B, originatingNoteId=A)
├─ 3. Update collection hashes (first-line hash changes)
├─ 4. Invalidate caches depending on Note B's first-line
│     ├─ Note A's view: SKIP (origin = A)
│     ├─ Note X's view: INVALIDATE (depends on B, origin ≠ X)
│     └─ Note Y's view: INVALIDATE (depends on B, origin ≠ Y)
└─ 5. Note A continues displaying edited content seamlessly
```

### Edge Cases

**Case 1: Nested views** — Note A views Note B, Note B views Note C, user edits Note C via Note A. Origin = A, so Note A's view suppression propagates through the chain.

**Case 2: Tab switch during edit** — Note B auto-saves, Note A is no longer active. When user returns, view refreshes with saved content.

**Case 3: External change while editing** — Invalidations are queued as `PendingInvalidation` entries and applied when the edit session ends. This prevents flicker during editing.

### Edit Session Lifecycle

An edit session ends when:
- Focus leaves the editable area (blur)
- Save / auto-save completes
- User navigates away from the note

On session end:
1. Clear the `activeEditContext`
2. Apply all pending invalidations
3. Refresh the current view

**Important:** Confirming a directive edit does NOT end the edit session. The user stays in edit mode until they tap out.

## Mutations and Caching

### Top-Level Mutations

Mutating functions (`new`, `maybe_new`) at the top level of a directive require explicit user interaction:

```
[new(path: "inbox/todo")]                           // ERROR: mutation requires button or schedule
[once[new(path: "inbox/todo")]]                     // ERROR: mutation requires button or schedule
[button("Create", [new(path: "inbox/todo")])]       // OK: user clicks button to execute
[schedule(daily, [new(path: "inbox/todo")])]        // OK: runs on schedule
```

### Nested Idempotent Mutations

Idempotent mutations inside an executed directive CAN be cached:

```
[button("Setup", [let note = maybe_new(path: "config"); note.name])]
```

When the button is clicked, `maybe_new` executes and the result is cached. Subsequent clicks return the cached result.

## Multi-Statement Directive Validation

Directives can contain multiple statements separated by semicolons. If ANY statement violates validation rules, the entire directive is rejected.

### Validation Rules

| Rule | Violation |
|------|-----------|
| Bare time values | `date`, `time`, `datetime` without `once`/`refresh` wrapper |
| Bare mutations | `new`, `maybe_new`, `.append` at top level without `button`/`schedule` |
| Return type | Final expression returns date/time/datetime without wrapper |

```
// ERROR: bare mutation in second statement
[notes: find(path: "inbox"); first(notes).append("done")]

// OK: mutation inside button
[button("Process", [notes: find(path: "inbox"); first(notes).append("done")])]
```

### Caching Implications

Dependencies of a multi-statement directive are the **union** of all statements' dependencies:

```
[a: find(path: "inbox"); b: find(where: [i.modified.gt("2026-01-01")]); list(a, b)]
```

Dependencies: `NOTE_EXISTENCE` + `FIELD_PATH` + `FIELD_MODIFIED`. Staleness is checked against all of them.

## Error Classification

```kotlin
sealed class DirectiveError {
    abstract val message: String
    abstract val isDeterministic: Boolean
}
```

**Deterministic errors** (cached — re-execution produces the same error):
- Syntax errors, type errors, missing required arguments, invalid field access, validation errors, unknown identifiers, circular dependencies, arithmetic errors

**Non-deterministic errors** (never cached — retry on next access):
- Network errors, timeout errors, resource unavailability, permission errors, external service errors

```kotlin
fun shouldReExecute(cached: CachedDirectiveResult): Boolean {
    if (cached.error != null && !cached.error.isDeterministic) {
        return true  // Always retry non-deterministic errors
    }
    return isStale(cached, ...)
}
```

## Memory Management

### Cache Size Limits

| Cache | Max Size | Eviction Policy |
|-------|----------|-----------------|
| Per-note in-memory cache | 100 entries per note, max 500 notes | LRU |
| Total in-memory | ~50MB estimated | Evict oldest when exceeded |

### Eviction Triggers

- Cache exceeds max size → evict LRU entries
- Android low memory callback → clear 50% of caches
- App backgrounded (after 5 minutes) → optionally trim caches

### Cache Clearing

| Event | Action |
|-------|--------|
| Note deleted | Clear that note's cache |
| User logs out | Clear all caches |
| App force stopped | In-memory lost; Firestore L2 persists |
| Manual refresh request | Clear specific directive's cache |
