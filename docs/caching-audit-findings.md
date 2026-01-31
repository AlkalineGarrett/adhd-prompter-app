# Caching and Data Refresh Audit Findings

This document tracks issues and recommendations from the caching system audit.

## Architecture Overview

The caching system has a layered architecture:

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
- [x] When view() renders notes, record those note IDs in `firstLineNotes`/`nonFirstLineNotes` ✅ (Phase 1 - done)
- [x] Store rendered content hashes in the view directive's cache entry ✅ (Phase 1 - done via dependency tracking)
- [ ] Add a small delay after save before refresh (workaround for eventual consistency)
  - Developer feedback: I want principled solutions instead of small delays. Add hooks on completions, for example.
- [ ] Use Firestore snapshot listeners for real-time updates

- Developer feedback: Try out the proposed fixes above. ✅ Phase 1 implemented.

---

### MEDIUM: Transitive Dependencies Not Propagated from View

**Status**: ✅ FIXED (Phase 1)

**Location**: `TransitiveDependencyCollector.kt:71-83` and `dsl/builtins/NoteFunctions.kt:306-310`

**Issue**: The collector has methods `addReferencedDependencies()` and `addNestedViewDependencies()`, but `view()` directive execution NEVER calls these methods.

**Investigation Results**:
1. `addNestedViewDependencies()` and `addReferencedDependencies()` are ONLY used in tests - NOT in production code
2. **Root Cause Found**: In `NoteFunctions.kt:306-310`, the `view()` function renders notes like this:
   ```kotlin
   val renderedContents = notes.map { note ->
       renderNoteContent(note, env)
   }
   ViewVal(notes, renderedContents)
   ```
3. The `renderNoteContent()` method (line 318) executes nested directives via `DirectiveFinder.executeDirective()` (line 342)
4. This **completely bypasses `CachedDirectiveExecutor`** - no cache entries are created for nested directives, and no dependency collection occurs

**Impact**:
- Nested directive dependencies are NOT merged to the parent view directive's cache entry
- When a viewed note's content changes, the parent view directive's staleness check doesn't detect it
- This is a core contributor to Issue 5

**Fix Required** (✅ Completed in Phase 1):
- [x] Make `view()` execute nested directives through `CachedDirectiveExecutor`
- [x] Transitive dependencies propagate via the collector's stack mechanism (automatic when using same executor)
- [x] Pass `CachedExecutorInterface` through the Environment (via `NoteContext.cachedExecutor`)

---

### MEDIUM: Race Condition in executeDirectivesLive

**Status**: ✅ MITIGATED (Phase 1 + Phase 2)

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
  - Developer feedback: Please try to fix this

---

### MEDIUM: cachedNotes Set to Null After Stale Read

**Status**: ✅ ACCEPTABLE (with Phase 1 dependency tracking)

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

**Resolution**: With Phase 1's dependency tracking in place, if directives execute with stale data:
1. Content hashes are stored in the cache entry
2. On next execution, the StalenessChecker compares current hashes with cached hashes
3. If content changed, the cache is stale and the directive re-executes with fresh data
4. This is self-correcting - stale data is detected and fixed on the next access

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
  - Developer feedback: Only one edit session is fine. Just make sure everything gets properly closed out and saved in the last session.
---

### LOW: Global Metadata Hashes Are Expensive

**Status**: INVESTIGATION COMPLETE - NO CACHING EXISTS

**Location**: `MetadataHasher.kt`

**Issue**: Each staleness check for directives with global dependencies (like `find()`) computes hashes over ALL notes:
- `computePathHash()` - sorts all notes by ID, joins paths, SHA-256
- `computeModifiedHash()` - same pattern for timestamps
- `computeAllNamesHash()` - same pattern for first lines

**Investigation Result**:
The code comment at line 10-13 says "Hashes are computed on-demand, not stored." There is NO caching of metadata hashes - they are recomputed on every staleness check. Searched for `cachedPathHash`, `cachedModifiedHash`, `cacheMetadata`, `metadataCache` - no matches.

**Required Fix**:
- [ ] Add caching layer to `MetadataHasher` that stores computed hashes
- [ ] Invalidate cached hashes when `cachedNotes` changes (set to null)
- [ ] Consider storing cached hashes at the `CachedDirectiveExecutor` level

**Future Optimization** (not blocking):
- [ ] Use incremental hash updates instead of full recomputation (e.g., XOR-based rolling hash)

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

1. **Atomic Save + Refresh**: ✅ IMPLEMENTED (Phase 2) - `saveInlineNoteContent` now waits for Firestore write confirmation before clearing caches and triggering refresh.

2. **Use Firestore Listeners**: For real-time scenarios, Firestore snapshot listeners could provide immediate updates without eventual consistency issues. (Not implemented - Phase 1+2 provide sufficient correctness)

---

## Testing Gaps

- [ ] No integration test for inline editing -> save -> view refresh flow
- [ ] No test for transitive dependency propagation from view() directives
- [ ] No test for eventual consistency handling after saves

- Developer feedback: Fix these test gaps

---

## Related Documentation

- `docs/view-inline-editing-caching-issues.md` - Detailed issue tracking for Issue 5
- `docs/view-caching-plan.md` - Original implementation plan

---

## Comprehensive Fix Plan

This plan addresses all issues in dependency order.

### Phase 1: Fix Transitive Dependency Propagation (Foundation)

**Status**: ✅ COMPLETED

**Goal**: Make `view()` properly track dependencies from nested directives.

**Why First**: This is the root cause of Issue 5. Without proper dependency tracking, staleness detection can never work correctly.

**What was implemented**:
1. Added `CachedExecutorInterface` to `NoteContext`/`Environment` to break circular dependency
2. `CachedDirectiveExecutor` implements the interface and passes itself to `DirectiveFinder.executeDirective()`
3. `renderNoteContent()` in `NoteFunctions.kt` uses the cached executor for nested directives
4. `enrichWithViewDependencies()` extracts viewed note IDs from `ViewVal` results and adds them to BOTH `firstLineNotes` and `nonFirstLineNotes` for full content tracking
5. The `TransitiveDependencyCollector` stack mechanism propagates nested dependencies to parent directives
6. Added integration tests verifying staleness detection for viewed note changes

#### 1.1 Pass CachedDirectiveExecutor to view() execution

**Files**: `dsl/builtins/NoteFunctions.kt`, `dsl/runtime/Environment.kt`

1. Add `getCachedExecutor(): CachedDirectiveExecutor?` to `Environment`
2. Modify `renderNoteContent()` to use cached executor instead of `DirectiveFinder.executeDirective()`
3. Pass the executor through the environment when creating child contexts

```kotlin
// In NoteFunctions.kt renderNoteContent():
val cachedExecutor = env.getCachedExecutor()
val execResult = if (cachedExecutor != null) {
    cachedExecutor.execute(
        sourceText = directive.sourceText,
        notes = env.getNotes() ?: emptyList(),
        currentNote = viewedNote,
        viewStack = viewStack
    )
} else {
    // Fallback for tests without caching
    DirectiveFinder.executeDirective(...)
}
```

#### 1.2 Collect and merge transitive dependencies

**Files**: `dsl/cache/CachedDirectiveExecutor.kt`, `dsl/builtins/NoteFunctions.kt`

1. After each nested directive execution in `renderNoteContent()`, collect its dependencies
2. Call `collector.addNestedViewDependencies(nestedDeps)` to merge them
3. The final `ViewVal` cache entry should have merged dependencies from all nested executions

#### 1.3 Track viewed note IDs as dependencies

**Files**: `dsl/cache/DirectiveDependencies.kt`, `dsl/builtins/NoteFunctions.kt`

1. When `view()` renders notes, record each note ID in `nonFirstLineNotes` set
2. This ensures staleness is detected when any viewed note's content changes

**Tests added** (in `CachingIntegrationTest.kt`):
- [x] `view directive tracks viewed note IDs as dependencies`
- [x] `view becomes stale when viewed note content changes`
- [x] `multiple viewed notes are all tracked as dependencies`
- [x] `nested directives in viewed notes propagate dependencies`

---

### Phase 2: Atomic Save + Refresh (Race Condition Fix)

**Status**: ✅ COMPLETED

**Goal**: Ensure save completion before cache refresh.

**Why Second**: Once dependencies are properly tracked, we need to ensure the data is consistent when we check staleness.

**What was implemented**:
1. `saveInlineNoteContent()` now awaits save completion before clearing caches
2. Caches are only cleared in the onSuccess callback (after Firestore confirms the write)
3. On save failure, the edit session is aborted (no pending invalidations applied)
4. Updated `executeAndStoreDirectives` comment to explain how Phase 1's dependency tracking handles potential stale reads

#### 2.1 Wait for Firestore write confirmation before refresh

**Files**: `ui/currentnote/CurrentNoteViewModel.kt`

**Current flow** (`saveInlineNoteContent`):
```
1. Clear caches (sync)
2. Start async Firestore save
3. End edit session (triggers refresh)
4. Firestore save completes later
```

**New flow**:
```
1. Start Firestore save
2. Wait for save completion (await)
3. Clear caches
4. End edit session (triggers refresh)
```

```kotlin
// In saveInlineNoteContent():
viewModelScope.launch {
    try {
        // Wait for save to complete
        repository.saveNoteWithFullContent(noteId, newContent).getOrThrow()

        // NOW invalidate caches (after save confirmed)
        cachedNotes = null
        directiveCacheManager.clearAll()

        // NOW end session (triggers refresh with fresh data)
        endInlineEditSession()
    } catch (e: Exception) {
        // Handle error - show dialog
    }
}
```

#### 2.2 Fix executeAndStoreDirectives stale read issue

**Files**: `ui/currentnote/CurrentNoteViewModel.kt`

The current pattern at line 864-869 invalidates cache AFTER execution. Change to:
1. Don't execute directives immediately after save
2. Instead, wait for save confirmation, THEN trigger refresh

**Tests to add**:
- [ ] Test that save + refresh uses post-save data (mock Firestore timing)

---

### Phase 3: Global Metadata Hash Caching

**Goal**: Cache expensive hash computations.

**Why Third**: Performance optimization, doesn't affect correctness.

#### 3.1 Add cached hash storage to MetadataHasher

**Files**: `dsl/cache/MetadataHasher.kt`

```kotlin
object MetadataHasher {
    // Cache for expensive hash computations
    private var cachedHashes: CachedMetadataHashes? = null

    data class CachedMetadataHashes(
        val notesVersion: Int,  // Incremented when notes change
        val pathHash: String?,
        val modifiedHash: String?,
        val createdHash: String?,
        val viewedHash: String?,
        val existenceHash: String?,
        val allNamesHash: String?
    )

    fun invalidateCache() {
        cachedHashes = null
    }

    fun computePathHash(notes: List<Note>): String {
        val cached = cachedHashes
        if (cached?.pathHash != null) return cached.pathHash

        val hash = computePathHashInternal(notes)
        // Store in cache
        cachedHashes = (cachedHashes ?: CachedMetadataHashes(...)).copy(pathHash = hash)
        return hash
    }
    // ... similar for other hash methods
}
```

#### 3.2 Invalidate hash cache when notes change

**Files**: `ui/currentnote/CurrentNoteViewModel.kt`

When `cachedNotes = null` is set, also call `MetadataHasher.invalidateCache()`.

---

### Phase 4: Edit Session Cleanup

**Goal**: Ensure proper cleanup when switching edit sessions.

#### 4.1 Verify pending invalidations are flushed

**Files**: `dsl/cache/EditSessionManager.kt`

When `startEditSession()` ends an existing session, ensure:
1. Pending invalidations from the old session are applied
2. Any unsaved changes are handled (or at least logged)

Current code at line 130-139 calls `endEditSession()` which should handle this, but verify the flow.

---

### Phase 5: Integration Tests

**Goal**: Prevent regressions.

#### 5.1 Inline editing -> save -> view refresh flow

```kotlin
@Test
fun `view updates after inline edit is saved`() {
    // 1. Create note A with directive [view find(path: "B")]
    // 2. Create note B with content "Original"
    // 3. Execute directives on A - view shows "Original"
    // 4. Inline edit note B from A's view, change to "Modified"
    // 5. Save inline edit
    // 6. Execute directives on A again
    // 7. Assert view now shows "Modified"
}
```

#### 5.2 Transitive dependency propagation

```kotlin
@Test
fun `nested directive dependencies propagate to parent view`() {
    // 1. Create note A with [view find(path: "B")]
    // 2. Create note B with [.path] directive
    // 3. Execute directives on A
    // 4. Assert the cached result for A's view has dependencies on B's path
    // 5. Change B's path
    // 6. Assert A's view is detected as stale
}
```

#### 5.3 Race condition handling

```kotlin
@Test
fun `refresh uses post-save data not pre-save cache`() {
    // Use mock Firestore with controlled timing
    // 1. Start with cachedNotes containing old data
    // 2. Save new content
    // 3. Trigger refresh
    // 4. Assert refresh uses new content, not old cachedNotes
}
```

---

### Implementation Order Summary

| Phase | Priority | Effort | Risk | Status |
|-------|----------|--------|------|--------|
| 1. Transitive Dependencies | Critical | High | Medium | ✅ DONE |
| 2. Atomic Save + Refresh | High | Medium | Low | ✅ DONE |
| 3. Metadata Hash Caching | Medium | Low | Low | Pending |
| 4. Edit Session Cleanup | Low | Low | Low | Pending |
| 5. Integration Tests | High | Medium | Low | Partial (Phase 1 tests added) |

**Recommended approach**:
1. Start with Phase 1.3 (track viewed note IDs) - smallest change that might fix Issue 5
2. If still broken, implement full Phase 1 (transitive deps)
3. Then Phase 2 (atomic save)
4. Phase 3 and 4 can be done in parallel
5. Phase 5 throughout to prevent regressions
