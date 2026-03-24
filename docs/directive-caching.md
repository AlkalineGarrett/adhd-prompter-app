# Directive Caching Specification

This document defines the intended caching behavior for directive results across both Android and web platforms. It is the authoritative reference for how caching should work.

## Cache Key Structure

Every cached directive result is keyed by two components:

- **Parent note ID**: The note that contains the directive
- **Directive hash**: FNV-1a 64-bit hash of the directive source text (including brackets)

The composite key is `(parentNoteId, directiveHash)`. Two identical directives in the same note share one cache entry. The same directive text in different notes has separate cache entries.

### FNV-1a 64-bit Hash

Both platforms use the same hash algorithm with identical output:

- **Constants**: offset = `0xcbf29ce484222325`, prime = `0x00000100000001b3`
- **Algorithm**: For each character, XOR the hash with the character code, then multiply by the prime (all operations mod 2^64)
- **Output**: 16-character lowercase hex string

Cross-platform consistency is verified by test: `"hello"` produces `a430d84680aabd0b` on both platforms.

### No position-based keys

Directive cache keys must never include line numbers, character offsets, or line IDs. These are unstable across edits and cause cache misses when lines are reordered, inserted, or deleted.

## Per-Note Cache Isolation

All directive results are cached per parent note. There is no global (cross-note) cache sharing. This ensures:

- A directive's result in note A is never served when evaluating the same directive text in note B
- Directives that reference the current note (`.`) produce different results per note
- Directives that don't reference the current note still get separate cache entries, avoiding cross-contamination when the note context matters (e.g., `find()` results displayed in different views)

## Staleness Checking

A cached result is valid until its dependencies change. The `StalenessChecker` compares cached dependency hashes against current data.

### Dependency types

| Dependency | Tracked by | Example directive |
|---|---|---|
| Note existence | Hash of sorted note IDs | `find()` |
| All note names | Hash of all first lines | `find(name: ...)` |
| Note paths | Hash of all paths | `find(path: ...)` |
| Specific note content (first line) | Hash of first line of specific note | `view()` of a note (name change) |
| Specific note content (body) | Hash of lines 2+ of specific note | `view()` of a note (body change) |
| Modified/created/viewed timestamps | Hash of all timestamps | Directives accessing `.modified`, `.created`, `.viewed` |
| Hierarchy resolution | Resolved note ID + field hash | `.up`, `.root` navigation |

### View directive dependencies

When a directive produces a `ViewVal`, the IDs of all viewed notes are automatically added to both `firstLineNotes` and `nonFirstLineNotes` dependencies. This means any content change to a viewed note invalidates the cache.

### Error caching

- **Deterministic errors** (syntax, type, validation): cached normally, not retried
- **Non-deterministic errors** (network, timeout, permission): never cached, always retried

## When Directives Execute

### Web

Directive results are computed **synchronously during render** via `useMemo`. The `CachedDirectiveExecutor`'s L1 cache makes cache hits instant (in-memory map lookup). Cache misses execute the directive and populate the cache for the next render.

The `useMemo` depends on:
- `noteId` (actually `loadedNoteId` from `useEditor` — only changes after editor content is populated)
- `notes` array
- `currentNote`
- `generation` counter (bumped on explicit invalidation)

This eliminates all async race conditions — content and directive results are always consistent within the same render.

### Android

Directive results are computed **synchronously during Compose recomposition** via `computeDirectiveResults()` wrapped in a `remember` block. The `CachedDirectiveExecutor`'s L1 cache makes cache hits instant.

The `remember` depends on:
- `userContent` (the editor text)
- `directiveCacheGeneration` (bumped after async cache population or explicit invalidation)

On cold start, the L1 cache is empty and directives execute fresh. An async path (`loadDirectiveResults`) loads cached results from Firestore L2 and populates the L1 cache, then bumps the generation counter to trigger recomposition with cache hits.

## Cache Invalidation

Cache is invalidated (cleared) when:

- The user saves the note
- A directive is edited (source text changed)
- Undo/redo is performed
- An inline-edited viewed note is saved
- A button directive executes a mutation

Invalidation calls `cachedExecutor.clearAll()` (clears all L1 entries) and bumps the generation counter. The next render/recomposition recomputes all directives, with the `StalenessChecker` determining which need fresh execution vs which can use cached results.

### Edit session suppression

During inline editing of a viewed note, cache invalidation for the host note is suppressed by `EditSessionManager`. This prevents the view from flickering while the user types. Deferred invalidations are applied when the edit session ends.

## Cache Tiers

### L1: In-memory (both platforms)

- `PerNoteDirectiveCache`: LRU cache per note (max 100 entries per note, max 500 notes)
- Keyed by `(noteId, directiveHash)`
- Cleared on invalidation
- Lost on app/page restart

### L2: Firestore (Android only)

- Stored at `users/{userId}/notes/{noteId}/directiveResults/{directiveHash}`
- Loaded on note open, populates L1
- Written after fresh execution
- **Excluded**: `ViewVal` results (depend on other notes, stale quickly), alarm results (trivial to re-execute)
- Survives app restarts

### Web has no L2

Web loses all cached results on page reload. All directives re-execute on every page visit. The synchronous `useMemo` approach ensures no flash of raw directive text during this re-execution.

## Flash Prevention

Neither platform should show raw directive source text (e.g., `[view(find(...))]`) during navigation. The mechanisms:

### Web

- `loadedNoteId` from `useEditor` only changes after `editorState.text` is populated
- `useDirectives` uses `loadedNoteId` as a `useMemo` dependency, not the URL's `noteId`
- When navigating, the old note's rendered view stays on screen until the new note's content and directive results are both ready in the same render

### Android

- `computeDirectiveResults()` runs synchronously in a `remember` block during recomposition
- On cache hits, results are instant — no frame with empty results
- On cache misses (cold start), the async `loadDirectiveResults` populates L1 and bumps `directiveCacheGeneration`, triggering recomposition with cached results
- `directiveCacheGeneration` is not bumped until results are ready, preventing premature recomposition with empty results

## Mutating Directives

Directives that modify notes (e.g., `[.root.name: "x"]`) receive special handling:

- **Never re-executed due to staleness** — once executed, the cached result is used indefinitely
- This prevents user edits from being overwritten by the original mutation value
- The `DependencyAnalyzer` detects mutating directives via AST analysis
