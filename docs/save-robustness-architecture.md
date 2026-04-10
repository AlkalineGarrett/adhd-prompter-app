# Save System Robustness: Architectural Research

**Date**: 2026-04-09
**Context**: Recurring content drop guard triggers caused by noteId loss in editor state. Three independent architecture proposals were evaluated.

## Problem Statement

The save system relies on editor-tracked noteIds (line -> Firestore document ID mappings) to determine which documents to update, create, or soft-delete. These noteIds live only in editor memory (`LineState.noteIds`). Various conditions cause them to be silently lost (all become null), at which point the save system interprets every line as "new" and schedules all existing descendants for deletion.

**Causes of noteId loss observed so far:**
- Writing to the currently-edited note's Firestore document triggers a snapshot -> live sync rebuilds editor state -> noteIds lost
- Paste operations that go through `updateFromText` instead of `initFromNoteLines`
- Race conditions between save completion and live sync updates
- Any new feature that touches editor state without understanding noteId preservation

**Current defenses:**
- Content drop guard (Android only, aborts if >50% of direct children would be deleted)
- `saveNoteWithFullContent` (content-matching save path, but only used for inline editing)
- NoteLineTracker two-phase matching (exact content, then positional fallback) — works at load time but not at save time

**Critical gap:** Web has NO content drop guard at all.

## Approach 1: Verify-Before-Save

**Core idea**: Add a `verifyAndRepairNoteIds()` step at the top of `saveNoteWithChildren`. Count how many editor noteIds are null; if >50% are missing, fall back to content-matching via `NoteStore.getNoteLinesById()` + `matchLinesToIds()`.

**Where it acts**: Inside `saveNoteWithChildren`, before the transaction. Plus diagnostics in `EditorState.toNoteLines()`.

**Changes:**
- `NoteRepository.kt` / `.ts`: Add `verifyAndRepairNoteIds()` private method using `NoteStore.getNoteLinesById()` + `matchLinesToIds()`. Call at top of `saveNoteWithChildren()`.
- `EditorState.kt` / `.ts`: Add diagnostic logging in `toNoteLines()` when a loaded note suddenly has most noteIds missing.

**Strengths:**
- Zero-cost happy path (just a count check, no Firestore reads)
- Uses existing `matchLinesToIds` and `NoteStore.getNoteLinesById` — no new infrastructure
- Transparent to all callers of `saveNoteWithChildren`

**Weaknesses:**
- Recovery is all-or-nothing (threshold-based)
- Doesn't address web's missing content drop guard
- Doesn't prevent noteId loss at the source — only recovers at save time

## Approach 2: Verify-Then-Write with Content-Matching Fallback

**Core idea**: Same verification as #1, but explicitly scoped. Uses `existingDescendantIds` already fetched by the save transaction for zero-cost verification. Adds the missing content drop guard to web.

**Where it acts**: Inside `saveNoteWithChildren` on both platforms, plus porting content drop guard to web.

**Changes:**
- `NoteRepository.kt` / `.ts`: Add `verifyNoteIdIntegrity()` pure function. Integrate into `saveNoteWithChildren()`.
- `NoteRepository.ts` (web): Port the content drop guard from Android.
- Return structured error types (not generic exceptions) so the UI can offer "retry with recovery."

**Trade-off analysis (why not always content-match):**
- Content matching is heuristic (similarity threshold, positional fallback) and can make wrong assignments for heavily edited notes
- Editor-tracked noteIds are more precise for split/merge/move operations
- Always content-matching adds a mandatory NoteStore read to every save, even when editor noteIds are correct

**Strengths:**
- Cross-platform parity — specifically addresses web's missing guard
- Well-argued trade-off for keeping editor noteIds as primary (precision) with content-matching as fallback (safety)

**Weaknesses:**
- Very similar to #1 in practice
- Only save-time recovery, no prevention

## Approach 3: Three-Layer Defense in Depth

**Core idea**: Three independent layers, any one of which prevents data loss.

### Layer 1: SaveValidator (save-time verification + recovery)

New `SaveValidator` module (`SaveValidator.kt` / `.ts`) called inside `saveNoteWithChildren` before the transaction.

1. **NoteId deficit detection**: Compare proposed noteIds against `existingDescendantIds`. If `validCount < existingCount * 0.5`, trigger recovery.
2. **Content-based recovery**: `recoverLostNoteIds(parentNoteId, proposedLines, existingLines)` — matches editor content against NoteStore's authoritative tree data to re-derive noteIds.
3. **Mass-deletion hard block**: Replace Android's threshold guard with a cross-platform rule: reject if deletions exceed `max(2, existingCount * 0.3)`. Lower threshold than current 50%.
4. **Web parity**: Port guard to web.

### Layer 2: Editor hardening (reduce frequency of noteId loss)

1. **`updateFromText` positional fallback guarantee**: After content matching, if resulting lines have fewer noteIds than input by more than lines removed, apply positional fallback for remaining unmatched IDs. Ensures `updateFromText` never produces total noteId loss.
2. **Debug-mode invariant checks**: In `EditorState.notifyChange()`, assert that if a line had noteIds before an operation and still exists, it should still have noteIds. `check()` in debug builds (Android), `console.error` in dev mode (web).

### Layer 3: Structural refactoring (reduce surface area long-term)

- Extract noteId transfer logic from split/merge/move operations into dedicated helpers (`transferNoteIdsOnSplit`, `transferNoteIdsOnMerge`) so it's unit-testable and reusable.
- New operations can call these helpers instead of reimplementing noteId threading.

**Phased implementation:**
- Phase 1: SaveValidator + web content drop guard (highest value, lowest risk)
- Phase 2: `updateFromText` hardening + debug invariants (prevention)
- Phase 3: Extract noteId transfer helpers (maintainability)

**Strengths:**
- Defense in depth — any single layer prevents data loss
- Addresses root cause (editor noteId loss) not just symptom (save-time detection)
- Debug invariants catch future features that break noteId tracking during development, not production
- Makes noteId loss "recoverable rather than catastrophic"

**Weaknesses:**
- Larger scope (Phase 2/3 are more invasive)

## Consensus

All three approaches converge on the same critical first step:

> **At save time, verify noteIds against NoteStore's in-memory data. When noteIds are missing, recover them via content matching before writing.**

This works because:
- `NoteStore.getNoteLinesById()` is already in memory on both platforms (no Firestore reads)
- `matchLinesToIds()` already exists on both platforms
- The happy path (noteIds intact) is a simple count check with zero cost
- The recovery path reuses existing content-matching infrastructure

All three also identified that the **web has no content drop guard** — a critical gap.

Approach 3's additional layers (editor hardening, debug invariants) address *why* noteIds get lost and *when* you find out, making it the most complete long-term solution. But Phase 1 alone solves the immediate problem.

## Files involved

- `app/src/main/java/org/alkaline/taskbrain/data/NoteRepository.kt` — Android save path
- `web/src/data/NoteRepository.ts` — Web save path (also needs content drop guard)
- `app/src/main/java/org/alkaline/taskbrain/data/NoteStore.kt` — In-memory note data for recovery
- `web/src/data/NoteStore.ts` — Same on web
- `app/src/main/java/org/alkaline/taskbrain/ui/currentnote/EditorState.kt` — Editor noteId tracking (hardening target)
- `web/src/editor/EditorState.ts` — Same on web
- `app/src/main/java/org/alkaline/taskbrain/data/NoteLineTracker.kt` — Content matching logic
