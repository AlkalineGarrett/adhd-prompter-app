# Caching and Data Refresh Audit Findings

This document tracks issues and recommendations from the caching system audit.

## Architecture Overview

The caching system has a well-designed layered architecture:

| Layer | Component | Purpose |
|-------|-----------|---------|
| L1 | `DirectiveCacheManager` | In-memory LRU cache (global + per-note) |
| L2 | `FirestoreDirectiveCache` | Persistent cross-session cache |
| Staleness | `StalenessChecker` | Hash-based dependency validation |
| Dependencies | `DependencyAnalyzer` | Static AST analysis |
| Runtime | `TransitiveDependencyCollector` | Collects dependencies during execution |
| Edit | `EditSessionManager` | Suppresses invalidation during inline editing |

### Key Files

| File | Purpose |
|------|---------|
| `dsl/cache/CachedDirectiveExecutor.kt` | Main orchestrator for cached execution |
| `dsl/cache/StalenessChecker.kt` | Determines if cache entry is stale |
| `dsl/cache/DependencyAnalyzer.kt` | Static AST analysis for dependencies |
| `dsl/cache/DirectiveDependencies.kt` | Data structures for dependency tracking |
| `dsl/cache/TransitiveDependencyCollector.kt` | Runtime dependency collection |
| `dsl/cache/MetadataHasher.kt` | Computes hashes for global metadata |
| `dsl/cache/ContentHasher.kt` | Computes hashes for note content |
| `dsl/cache/EditSessionManager.kt` | Manages inline edit sessions |
| `ui/currentnote/CurrentNoteViewModel.kt` | Integration point with UI |

---

## Issues Identified

### CRITICAL: Issue 5 - View Shows Old Content After Inline Directive Edit

**Status**: UNRESOLVED (also documented in `docs/view-inline-editing-caching-issues.md`)

**Symptom**: After editing a directive within a viewed note and confirming:
1. The inline editor recalculates and shows updated result
2. Going to the source note shows the OLD directive and OLD rendered value
3. Going back to the view shows the OLD rendered value
4. Tapping on the note recalculates with the NEW directive
5. Tapping outside shows the OLD rendered value again

**Location**: `CurrentNoteViewModel.kt:1276-1315` (`saveInlineNoteContent`)

**Root Cause Analysis**: Multiple cache layers become out-of-sync:

1. **Firestore Eventual Consistency**:
   - `saveInlineNoteContent` invalidates `cachedNotes = null` and `directiveCacheManager.clearAll()` synchronously BEFORE the async Firestore save
   - The `onSuccess` callback happens AFTER the coroutine context
   - `endInlineEditSession()` triggers `executeDirectivesLive` - but Firestore may not have propagated the write yet

2. **View Directive Cache Not Properly Invalidated**:
   - The host note's view directive caches a `ViewVal` containing `renderedContents`
   - When the viewed note changes, the viewed note's cache entry is cleared
   - But the HOST note's view directive cache still contains old `ViewVal`
   - The staleness check uses `firstLineNotes`/`nonFirstLineNotes` to track note IDs
   - If the view directive doesn't explicitly track the edited note's content, staleness check may pass

3. **Missing Dependency Tracking for View Contents**:
   In `DependencyAnalyzer.kt:272-278`:
   ```kotlin
   "view" -> {
       // view() inherits dependencies from its input
       // The actual transitive dependencies are handled at runtime
       for (arg in expr.args) {
           analyzeExpression(arg, ctx)
       }
   }
   ```
   The view directive's static analysis doesn't record dependencies on the CONTENT of the notes it renders - only on the `find()` expression that produces them.

**Potential Fixes**:
- [ ] When view() renders notes, record those note IDs in `firstLineNotes`/`nonFirstLineNotes`
- [ ] Store rendered content hashes in the view directive's cache entry
- [ ] Add a small delay after save before refresh (workaround for eventual consistency)
- [ ] Use Firestore snapshot listeners for real-time updates

---

### MEDIUM: Transitive Dependencies Not Propagated from View

**Status**: NEEDS INVESTIGATION

**Location**: `TransitiveDependencyCollector.kt:71-83`

**Issue**: The collector has methods `addReferencedDependencies()` and `addNestedViewDependencies()`, but I don't see evidence that `view()` directive execution actually calls these methods.

**Impact**: When a view directive renders notes containing their own directives, those nested directive dependencies may not be merged up to the parent view directive's cache entry.

**Investigation Needed**:
- [ ] Verify where `addNestedViewDependencies()` is called during view rendering
- [ ] Trace the code path from view directive execution to dependency collection
- [ ] Add test case for transitive dependency propagation

---

### MEDIUM: Race Condition in executeDirectivesLive

**Status**: PARTIALLY MITIGATED

**Location**: `CurrentNoteViewModel.kt:665-717`

**Issue**: The code correctly addresses one race condition at lines 707-714:
```kotlin
// Merge new results into CURRENT state (not the captured snapshot)
val latestResults = _directiveResults.value?.toMutableMap() ?: mutableMapOf()
```

However, there's a potential race between:
1. `ensureNotesLoaded()` at line 687 (suspends and may fetch from Firestore)
2. Another save operation updating `cachedNotes`

**Impact**: Low - the staleness checker should catch this on next refresh.

---

### MEDIUM: cachedNotes Set to Null After Stale Read

**Status**: BY DESIGN (but suboptimal)

**Location**: `CurrentNoteViewModel.kt:864-869`

```kotlin
// IMPORTANT: Invalidate notes cache after execution completes.
// The refreshNotesCache() above may have fetched stale data from Firestore
// due to eventual consistency (the save write hasn't propagated yet).
cachedNotes = null
```

**Issue**: This is the correct mitigation, but means:
1. During `executeAndStoreDirectives`, directives execute with potentially stale notes
2. Only AFTER execution, the cache is invalidated

**Result**: View directives rendered during save may show stale data until next tab switch/refresh.

---

### LOW: EditSessionManager Has Only One Active Session

**Status**: DOCUMENTED LIMITATION

**Location**: `EditSessionManager.kt:130-139`

```kotlin
fun startEditSession(editedNoteId: String, originatingNoteId: String) {
    // End any existing session first
    if (activeEditContext != null) {
        endEditSession()
    }
    ...
}
```

**Issue**: Only ONE edit session can be active at a time. If a user starts editing Note A from a view in Note X, then starts editing Note B from a view in Note Y, the first session ends prematurely.

**Impact**: Potentially losing pending invalidations from the first session.

---

### LOW: Global Metadata Hashes Are Expensive

**Status**: DOCUMENTED (performance consideration)

**Location**: `MetadataHasher.kt`

**Issue**: Each staleness check for directives with global dependencies (like `find()`) computes hashes over ALL notes:
- `computePathHash()` - sorts all notes by ID, joins paths, SHA-256
- `computeModifiedHash()` - same pattern for timestamps
- `computeAllNamesHash()` - same pattern for first lines

The code claims "~1-2ms for 200 notes", but this adds up when multiple directives need checking.

**Potential Optimizations**:
- [ ] Cache the global hashes and invalidate on any note change
- [ ] Use incremental hash updates instead of full recomputation

---

## Well-Designed Aspects

1. **Targeted Staleness Checking**: The `StalenessChecker` short-circuits at the first stale dependency (line 66-108). Only the dependencies that changed trigger re-execution.

2. **Separation of Static vs Runtime Dependencies**: `DependencyAnalyzer` provides static analysis, while `TransitiveDependencyCollector` captures runtime-resolved dependencies (like which note `.up` resolves to).

3. **Mutating Directive Protection**: `CachedDirectiveExecutor.kt:96-113` correctly prevents re-execution of mutating directives:
   ```kotlin
   if (isMutating) {
       val cachedResult = cacheManager.get(cacheKey, noteId, usesSelfAccess)
       if (cachedResult != null) {
           // Return cached - don't re-execute
           return CachedExecutionResult(...)
       }
   }
   ```

4. **Edit Session Suppression**: The `EditSessionManager` correctly queues invalidations during inline editing to prevent UI flicker.

5. **Error Caching Logic**: Deterministic errors are cached; non-deterministic errors trigger retry.

6. **L1/L2 Cache Layering**: Clean separation between in-memory and persistent caches.

---

## Recommendations

### For Issue 5 (Critical)

1. **Track View Content Dependencies**: When a `view()` directive renders notes, record those note IDs in `firstLineNotes`/`nonFirstLineNotes` dependencies so staleness is properly detected.

2. **Include Rendered Content Hashes**: Store the hash of rendered content in the view directive's cache entry. If the hash doesn't match current content, the cache is stale.

3. **Verify Transitive Dependency Collection**: Ensure that `view()` directive execution calls `addNestedViewDependencies()` to propagate nested dependencies.

4. **Consider Content-Based Cache Keys for Views**: Instead of caching based on directive hash alone, include rendered content hashes in the view directive's cache entry.

### For Race Conditions

1. **Atomic Save + Refresh**: Consider making the save and cache refresh atomic by waiting for Firestore write confirmation before refreshing.

2. **Use Firestore Listeners**: For real-time scenarios, Firestore snapshot listeners could provide immediate updates without eventual consistency issues.

---

## Testing Gaps

- [ ] No integration test for inline editing -> save -> view refresh flow
- [ ] No test for transitive dependency propagation from view() directives
- [ ] No test for eventual consistency handling after saves

---

## Related Documentation

- `docs/view-inline-editing-caching-issues.md` - Detailed issue tracking for Issue 5
- `docs/view-caching-plan.md` - Original implementation plan
