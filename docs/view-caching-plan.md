# Directive Caching Plan

This document describes how directive results are cached and when caches are invalidated based on dependency tracking.

## Scope: All Directives, Not Just Views

Every directive needs dependency tracking, not just views. This enables:
1. Any directive can expose its dependencies
2. Directives that reference other directives inherit transitive dependencies
3. Views are just one case of this general pattern

## Approach: Code-Analysis-Based Dependency Tracking

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

### Self-Access Detection

A directive "uses self-access" if its AST contains any `.` (dot) reference to the current note:
- `.name`, `.path`, `.modified`, `.created`, `.viewed`, etc.
- `.` alone (the current note itself)

**During AST analysis:**
```kotlin
data class DirectiveAnalysis(
    val dependencies: DirectiveDependencies,
    val usesSelfAccess: Boolean,  // true if any `.` reference found
)
```

**Cache routing:**
- `usesSelfAccess = false` → eligible for global shared cache
- `usesSelfAccess = true` → must use per-note cache

### Global vs Per-Note Cache

find() and view() have fundamentally different dependency patterns:
- find() → depends on metadata → changes rarely → highly cacheable
- view() → depends on content → changes often → per-note cache

Only directives WITHOUT self-access (`.`) can be shared globally.

| Directive | Uses `.`? | Cache Level |
|-----------|-----------|-------------|
| `find(path: "inbox")` | No | Global (shared by query hash) |
| `find(path: .path)` | Yes | Per-note |
| `[.name]` | Yes | Per-note |
| `view(...)` | N/A | Per-note (renders in note context) |

**Architecture:**
```
Global Cache (shared across notes):
  - Key: hash of self-less directive code
  - Value: find() results + dependencies + metadata hashes at cache time
  - Example: find(path: "inbox") → [noteB, noteC, noteD]

Per-Note Cache:
  - Key: note ID + directive position
  - Value: rendered result + dependencies + hashes at cache time
  - For: any directive using `.`, and all view() rendered content
```

If two notes use the same self-less find() query, they depend on the same metadata fields. If one invalidates, the other would too. Sharing just eliminates redundant computation.

## Dependency Types

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
| `FIELD_PATH` | `find(path: ...)`, `note.path` | Global (all notes) |
| `FIELD_MODIFIED` | `find(where: [gt(i.modified, ...)])` | Global (all notes) |
| `FIELD_CREATED` | `find(where: [gt(i.created, ...)])` | Global (all notes) |
| `FIELD_VIEWED` | `find(where: [gt(i.viewed, ...)])` | Global (all notes) |

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
| `NOTE_EXISTENCE` | `find()` is used (note creation/deletion affects results) | Global (note count + IDs) |

### 4. Hierarchy Dependencies (for `.up`, `.root`)

When a directive accesses properties through hierarchy navigation (`.up`, `.up(n)`, `.root`), it creates a **resolved hierarchy dependency**. These are inherently per-note since they use `.`.

**Challenge:** `.up` resolves to different notes depending on where the directive is located. The dependency must track both:
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

**Staleness check:**

```kotlin
fun isHierarchyStale(
    dep: HierarchyDependency,
    currentNote: Note,
    allNotes: List<Note>
): Boolean {
    // 1. Re-resolve the hierarchy path from current note
    val currentResolved = resolveHierarchy(dep.path, currentNote, allNotes)

    // 2. If resolves to different note (or null), hierarchy changed → stale
    if (currentResolved?.id != dep.resolvedNoteId) return true

    // 3. If same note and a field was accessed, check field hash
    if (dep.field != null && currentResolved != null) {
        val currentHash = hashField(currentResolved, dep.field)
        if (currentHash != dep.fieldHash) return true
    }

    return false
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

**Implementation:** When rendering a note's content, collect dependencies from any nested views and merge them into the parent view's dependencies.

## Code Analysis: What Triggers Each Dependency

### Property Access Tracking

| Code | Dependencies Added |
|------|-------------------|
| `note.name` or `note.content` first line | `FIRST_LINE` for that note |
| `note.content` beyond first line | `NON_FIRST_LINE` for that note |
| `note.path` | `FIELD_PATH` (global) |
| `note.modified` | `FIELD_MODIFIED` (global) |
| `note.created` | `FIELD_CREATED` (global) |
| `note.viewed` | `FIELD_VIEWED` (global) |
| `.up`, `.up(n)`, `.root` | `HierarchyDependency` with resolved note ID |
| `.up.path`, `.root.modified`, etc. | `HierarchyDependency` with resolved note ID + field hash |

### Function Call Tracking

| Code | Dependencies Added |
|------|-------------------|
| `find(...)` | `NOTE_EXISTENCE` (global) |
| `find(path: ...)` | `FIELD_PATH` (global) |
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

    // Global metadata dependencies (true = this directive depends on this field)
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

    companion object {
        val EMPTY = DirectiveDependencies(
            firstLineNotes = emptySet(),
            nonFirstLineNotes = emptySet(),
            dependsOnPath = false,
            dependsOnModified = false,
            dependsOnCreated = false,
            dependsOnViewed = false,
            dependsOnNoteExistence = false,
            hierarchyDeps = emptyList(),
        )
    }
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

// Computed on-demand during staleness check
object MetadataHasher {
    fun computePathHash(notes: List<Note>): String =
        hash(notes.sortedBy { it.id }.map { "${it.id}:${it.path}" }.joinToString("\n"))

    fun computeExistenceHash(notes: List<Note>): String =
        hash(notes.map { it.id }.sorted().joinToString("\n"))

    // ... etc for each field
}
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

    // Check global metadata dependencies (lazy, on-demand hashing)
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
    // Requires currentNote to be passed in for per-note cached directives
    for (dep in deps.hierarchyDeps) {
        if (isHierarchyStale(dep, currentNote, currentNotes)) return true
    }

    return false
}

/** Check if a hierarchy dependency is stale */
fun isHierarchyStale(
    dep: HierarchyDependency,
    currentNote: Note,
    allNotes: List<Note>
): Boolean {
    // 1. Re-resolve the hierarchy path from current note
    val currentResolved = resolveHierarchy(dep.path, currentNote, allNotes)

    // 2. If resolves to different note (or existence changed), hierarchy changed → stale
    if (currentResolved?.id != dep.resolvedNoteId) return true

    // 3. If same note and a field was accessed, check field hash
    if (dep.field != null && currentResolved != null) {
        val currentHash = hashField(currentResolved, dep.field)
        if (currentHash != dep.fieldHash) return true
    }

    return false
}

/** Resolve a hierarchy path from a note */
fun resolveHierarchy(path: HierarchyPath, note: Note, allNotes: List<Note>): Note? {
    return when (path) {
        HierarchyPath.UP -> findParent(note, allNotes)
        is HierarchyPath.UP_N -> findAncestor(note, path.n, allNotes)
        HierarchyPath.ROOT -> findRoot(note, allNotes)
    }
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
2. **Cache invalidation propagates**: Note B's content changed, so:
   - Any view depending on Note B's first-line should invalidate
   - Any view depending on Note B's non-first-line should invalidate (if applicable)
3. **No refresh artifacts for active editor**: Note A's view should NOT flicker/re-render while user is editing inline, even though its dependency (Note B) changed

### The Problem

Normal flow:
1. User edits Note B's content via Note A's view
2. Note B saves → content hash changes
3. Note A's view cache depends on Note B → marked stale
4. Note A's view re-executes → re-renders
5. **User sees flicker/cursor jump** ❌

### Proposed Solution: Edit Origin Tracking

Track which note originated an edit, and suppress cache invalidation for views in that note.

```kotlin
data class EditContext(
    val editedNoteId: String,      // Note B - the note whose content changed
    val originatingNoteId: String, // Note A - the note where user was editing
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

**Case 1: Note A views Note B, Note B views Note C, user edits Note C via Note A**
- Origin = Note A
- Note C saves, hashes update
- Note B's view (depends on C) invalidates → Note B's rendered content changes
- Note A's view (depends on B's rendered content) would normally invalidate
- But origin = A, so Note A's view is suppressed
- Note A continues showing the edit

**Case 2: User switches tabs during edit**
- Edit in progress on Note B via Note A
- User switches to Note X
- Note B auto-saves (if dirty)
- Note A is no longer active, suppress rule no longer applies
- When user returns to Note A, view refreshes with saved content (should match what they typed)

**Case 3: External change while editing**
- User is editing Note B via Note A
- Meanwhile, Note D (also in Note A's view) changes externally
- Note A's view depends on D
- D's change should trigger refresh... but user is mid-edit
- **Decision needed**: Defer all refreshes while editing? Or refresh non-edited portions?

### Implementation Considerations

**Where to track edit origin:**
- ViewModel holds `activeEditContext: EditContext?`
- Set when user starts editing inline content
- Cleared when editing ends (blur, save, tab switch)

**How view identifies its notes:**
- ViewVal already contains list of viewed note IDs
- When checking staleness, also check if current note is the edit origin
- If origin matches, skip staleness → return cached result

### New Data Structures

```kotlin
data class EditContext(
    val editedNoteId: String,
    val originatingNoteId: String,
    val editStartTime: Long,  // For timeout-based fallback
)

// In ViewModel
var activeEditContext: EditContext? = null

// Modified staleness check
fun isStale(
    cached: CachedViewResult,
    currentNotes: List<Note>,
    currentCollectionHashes: CollectionHashes,
    currentNoteId: String,           // The note containing this view
    activeEditContext: EditContext?  // Current edit context
): Boolean {
    // If we're the origin of an active edit, suppress staleness
    if (activeEditContext?.originatingNoteId == currentNoteId) {
        return false  // Not stale - suppress refresh
    }

    // Normal staleness check...
}
```

### External Changes During Edit

When external changes occur while the user is editing inline, invalidations are queued and applied when the edit session ends. This prevents multiple flickers during editing.

```kotlin
data class PendingInvalidation(
    val noteId: String,          // Note whose cache is invalidated
    val reason: InvalidationReason,
    val timestamp: Long,
)

// In ViewModel
val pendingInvalidations: MutableList<PendingInvalidation> = mutableListOf()
```

### Edit Session End

An edit session ends when any of these occur:
- On blur (focus leaves the editable area)
- On save / auto-save
- On navigation away from note

```kotlin
fun onBlur() {
    endEditSession()
}

fun onSave() {  // includes auto-save
    endEditSession()
}

fun onNavigateAway() {
    endEditSession()
}

fun endEditSession() {
    activeEditContext = null
    // Apply all pending invalidations
    for (invalidation in pendingInvalidations) {
        invalidateCache(invalidation.noteId)
    }
    pendingInvalidations.clear()
    refreshCurrentView()
}
```

## Mutations and Caching

### Top-Level Mutations

Mutating functions (`new`, `maybe_new`) at the top level of a directive are **not allowed** to run bare, with `once[...]`, or with `refresh[...]`. They require explicit user interaction:

```
[new(path: "inbox/todo")]                           // ERROR: mutation requires button or schedule
[once[new(path: "inbox/todo")]]                     // ERROR: mutation requires button or schedule
[button("Create", [new(path: "inbox/todo")])]       // OK: user clicks button to execute
[schedule(daily, [new(path: "inbox/todo")])]        // OK: runs on schedule
```

### Nested Idempotent Mutations

Idempotent mutations inside an executed directive CAN be cached, because the result is deterministic:

```
[button("Setup", [let note = maybe_new(path: "config"); note.name])]
```

When the button is clicked:
- `maybe_new` executes (creates note if needed, returns existing if present)
- Result is cached
- Subsequent clicks return cached result (idempotent)

## Multi-Statement Directive Validation

Directives can contain multiple statements separated by semicolons. Validation rules apply to the **entire directive**, not individual statements.

### Whole-Directive Analysis

If ANY statement in a directive violates validation rules, the entire directive is rejected:

```
[matching: find(where: [i.created.gt("2026-01-01")]); new(path: date, content: matching)]
```

This directive has two violations:
1. `new(...)` is a bare mutation (requires `button` or `schedule`)
2. `date` is a bare time value (requires `once[...]` or `refresh[...]`)

Even though the `find()` statement alone would be valid and cacheable, the entire directive errors.

### Validation Scan

The validator walks ALL statements in a directive and checks:

| Rule | Violation |
|------|-----------|
| Bare time values | `date`, `time`, `datetime` without `once`/`refresh` wrapper |
| Bare mutations | `new`, `maybe_new`, `.append` at top level without `button`/`schedule` |
| Return type | Final expression returns date/time/datetime without wrapper |

```
// ERROR: bare mutation in second statement
[notes: find(path: "inbox"); first(notes).append("done")]

// ERROR: bare time value in first statement
[today: date; find(where: [i.created.gt(today)])]

// OK: time value inside refresh wrapper
[refresh[today: date; find(where: [i.created.gt(today)])]]

// OK: mutation inside button
[button("Process", [notes: find(path: "inbox"); first(notes).append("done")])]
```

### Caching Implications

A directive is only cacheable if ALL its statements pass validation. The dependencies of a multi-statement directive are the **union** of all statements' dependencies:

```
[a: find(path: "inbox"); b: find(where: [i.modified.gt("2026-01-01")]); list(a, b)]
```

Dependencies:
- `NOTE_EXISTENCE` (from both `find()` calls)
- `FIELD_PATH` (from first `find()`)
- `FIELD_MODIFIED` (from second `find()`)

The cached result is the final expression (`list(a, b)`), with staleness checked against all dependencies.

## Error Caching

Errors are categorized and cached differently:

### Deterministic Errors (Cached)

Errors that will always occur given the same input:
- Syntax errors
- Type errors
- Missing required arguments
- Invalid field access

These are cached because re-execution will produce the same error.

### Non-Deterministic Errors (Not Cached)

Errors that might not recur:
- Network errors
- Timeout errors
- Temporary resource unavailability
- Permission errors (might be fixed)

These are NOT cached - retry on next staleness check.

### Implementation

```kotlin
sealed class DirectiveError {
    abstract val message: String
    abstract val isDeterministic: Boolean
}

data class SyntaxError(override val message: String) : DirectiveError() {
    override val isDeterministic = true
}

data class NetworkError(override val message: String) : DirectiveError() {
    override val isDeterministic = false
}

// In staleness check:
fun shouldReExecute(cached: CachedDirectiveResult): Boolean {
    if (cached.error != null && !cached.error.isDeterministic) {
        return true  // Always retry non-deterministic errors
    }
    return isStale(cached, ...)
}
```

## Cache Storage: In-Memory + Firestore Hybrid

### Architecture

```
┌─────────────────────────────────────────────────┐
│                   ViewModel                      │
│  ┌─────────────────────────────────────────┐    │
│  │         In-Memory Cache (L1)            │    │
│  │  - Fast access                          │    │
│  │  - Lost on app restart                  │    │
│  │  - Global cache + per-note cache        │    │
│  └─────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────┐
│                   Firestore (L2)                 │
│  notes/{noteId}/directiveResults/{hash}         │
│  - Persistent across sessions                   │
│  - Slower access                                │
│  - Stores: result + dependencies + hashes       │
└─────────────────────────────────────────────────┘
```

### Cache Lookup Flow

```
1. Check in-memory cache (L1)
   - If hit and not stale → return cached result

2. Check Firestore cache (L2)
   - If hit and not stale → populate L1, return cached result

3. Cache miss or stale
   - Execute directive
   - Store in L1 (in-memory)
   - Store in L2 (Firestore) for persistence
```

### What Gets Stored in Firestore

```kotlin
// Firestore document at notes/{noteId}/directiveResults/{directiveHash}
data class PersistedDirectiveResult(
    val result: Map<String, Any?>?,           // Serialized DslValue
    val error: PersistedError?,               // Error info if failed
    val dependencies: PersistedDependencies,  // For staleness check
    val metadataHashes: Map<String, String>,  // Collection hashes at cache time
    val noteContentHashes: Map<String, Map<String, String>>,  // Per-note content hashes
    val cachedAt: Timestamp,
)
```

### Global Cache in Firestore

For self-less directives (shared across notes), store in a global location:

```
directiveCache/{directiveHash}
  - result
  - dependencies
  - metadataHashes
  - cachedAt
```

This allows sharing across notes and sessions.

## Memory Management

### Cache Size Limits

| Cache | Max Size | Eviction Policy |
|-------|----------|-----------------|
| Global in-memory cache | 1000 entries | LRU (Least Recently Used) |
| Per-note in-memory cache | 100 entries per note | LRU |
| Total in-memory | ~50MB estimated | Evict oldest when exceeded |

### Monitoring

Log cache usage every 10 minutes:

```kotlin
// Every 10 minutes
fun logCacheStats() {
    val globalUsage = globalCache.size.toFloat() / GLOBAL_CACHE_MAX_SIZE * 100
    val perNoteUsage = perNoteCaches.values.sumOf { it.size }.toFloat() /
                       (perNoteCaches.size * PER_NOTE_CACHE_MAX_SIZE) * 100

    Log.i("CacheStats", "Global cache: ${globalUsage.toInt()}% used (${globalCache.size}/$GLOBAL_CACHE_MAX_SIZE)")
    Log.i("CacheStats", "Per-note caches: ${perNoteUsage.toInt()}% used across ${perNoteCaches.size} notes")
}
```

### Eviction Triggers

- When cache exceeds max size → evict LRU entries until under limit
- On memory pressure (Android low memory callback) → clear 50% of caches
- On app background (after 5 minutes) → optionally trim caches

### Cache Clearing

| Event | Action |
|-------|--------|
| Note deleted | Clear that note's per-note cache |
| User logs out | Clear all caches |
| App force stopped | In-memory lost; Firestore persists |
| Manual refresh request | Clear specific directive's cache |

## Implementation Phases

### Phase 0: Language updates (prerequisite)

These language changes from the spec must be implemented before or alongside the caching work.

#### 0a: Method-style syntax
- **Parser**: Support method calls on values: `a.method(b)` as alternative to `function(a, b)`
- **Comparison methods**: `.eq()`, `.ne()`, `.gt()`, `.lt()`, `.gte()`, `.lte()`
- **Logical methods**: `.and()`, `.or()` for chaining: `a.gt(x).and(a.lt(y))`
- **Date/time methods**: `.plus(days:)`, `.plus(hours:)`, `.plus(minutes:)` on date/time/datetime
- **String methods**: `.startsWith(prefix)`, `.endsWith(suffix)`, `.contains(substring)`

#### 0b: Implicit lambda syntax `[...]`
- **Parser**: Recognize `[...]` in argument position as implicit lambda with parameter `i`
- **Variable assignment**: `[f: [add(i, 1)]]` creates a lambda bound to `f`
- **Multi-arg lambdas**: `[(a, b)[expr]]` syntax for explicit parameters
- **Legacy support**: `lambda[...]` remains valid but deprecated
- **Nested brackets**: Ensure parser handles `[find(where: [i.path.startsWith("x")])]`

#### 0c: `once[...]` execution block
- **Parser**: Support `once[...]` as execution wrapper
- **Execution**: Evaluate expression once on first execution, cache result permanently
- **Cache key**: Directive text hash (result persists until directive text changes)
- **Bare time error**: `[datetime]`, `[time]`, `[date]` without `once` or `refresh` → error
- **Return type check**: Error if top-level expression returns date/time/datetime without wrapper

#### 0d: `refresh[...]` enhancements
- **Parser**: Support `refresh[...]` block syntax (in addition to `refresh expr`)
- **Time trigger analysis**: Integrate with Phase 3 backtrace + verify

#### 0e: Deferred block syntax `[...]`
- **Equivalence**: `later expr` and `[expr]` are equivalent for deferring
- **Multi-statement**: `[...]` allows semicolon-separated statements; `later` only defers next token
- **Remove redundancy**: `later[...]` is redundant; prefer `[...]` alone

#### 0f: Button and schedule updates
- **Button**: Accept `[...]` as action parameter: `[button("label", [action])]`
- **Schedule identifiers**: Support bare identifiers like `daily` (not just `daily_at("time")`)
- **Schedule action**: Accept `[...]` as action parameter: `[schedule(daily, [action])]`

### Phase 1: Data structures and hashing infrastructure
- Define `DirectiveDependencies`, `MetadataHashes`, `ContentHashes`
- Define `HierarchyDependency`, `HierarchyPath`, `NoteField` for hierarchy tracking
- Define `CachedDirectiveResult` with dependencies and hashes
- Implement `MetadataHasher` for on-demand field hashing
- Implement `hashFirstLine()` and `hashNonFirstLine()` for content
- Implement `hashField()` for hierarchy field hashing
- Implement `isStale()` staleness check algorithm (including hierarchy checks)
- Implement `resolveHierarchy()` for `.up`/`.root` resolution

### Phase 2: AST analysis for dependency detection
- Analyze directive AST to detect field access (path, modified, etc.)
- Detect self-access (`.`) to determine cache shareability
- Detect hierarchy access (`.up`, `.up(n)`, `.root`) and record `HierarchyDependency`
- Resolve hierarchy at analysis time to capture resolved note ID and field hash
- Track which notes' content is accessed (for content dependencies)
- Output: `DirectiveAnalysis(dependencies, usesSelfAccess)`

### Phase 3: Time-based refresh analysis
- Detect `once` and `refresh` modifiers
- Implement backtrace + verify algorithm for finding flip points
- Variable resolution through constant propagation
- Register time triggers for `refresh` directives
- Enforce bare time value errors

### Phase 4: Error classification
- Define `DirectiveError` hierarchy with `isDeterministic` flag
- Tag all error types appropriately
- Implement conditional caching (cache deterministic, skip non-deterministic)

### Phase 5: Cache architecture
- **Global shared cache** for self-less directives (keyed by directive hash)
- **Per-note cache** for self-referencing directives (keyed by note ID + position)
- Separate find() result caching from view() rendered content
- Implement LRU eviction with size limits
- Add cache usage logging (every 10 minutes)

### Phase 6: Firestore persistence layer
- Define Firestore schema for `PersistedDirectiveResult`
- Implement L1 (in-memory) → L2 (Firestore) cache flow
- Global cache storage at `directiveCache/{hash}`
- Per-note cache storage at `notes/{noteId}/directiveResults/{hash}`

### Phase 7: Transitive dependency merging
- When directive A references directive B, inherit B's dependencies
- When view renders nested views, merge their dependencies
- Propagate dependencies up to parent
- Propagate time dependencies through lambda references

### Phase 8: Inline editing support
- Track `EditContext` (edited note, originating note)
- Queue invalidations during active edit session
- Detect edit session end (blur, save, navigation)
- Apply queued invalidations on session end

### Phase 9: Mutation handling
- Enforce `button` or `schedule` requirement for top-level mutations
- Allow caching of nested idempotent mutation results

### Phase 10: Integration and testing
- Hook staleness checks into note open and save flows
- Connect dependency tracking to directive execution
- Ensure current note exclusion from find() results
- Implement time trigger scheduler
- Test with nested views, transitive dependencies, time-based refresh
- Test inline editing scenarios
- Test error caching behavior
