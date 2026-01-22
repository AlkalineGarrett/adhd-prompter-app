# IME Implementation Notes

## Architecture Overview

The IME (Input Method Editor) implementation uses a layered architecture:

```
Android IME Framework
        ↓
LineInputConnection (InputConnection implementation)
        ↓
LineImeState (buffer management, command execution)
        ↓
EditorController (application state)
```

### Key Components

1. **LineInputConnection** (`LineTextInput.kt`)
   - Implements Android's `InputConnection` interface
   - Thin wrapper that delegates to `LineImeState`
   - Created per IME session

2. **LineImeState** (`LineImeState.kt`)
   - Manages the `EditingBuffer` - source of truth during IME operations
   - Handles batch edits (deferred sync/notification)
   - Syncs with `EditorController` after operations

3. **EditingBuffer** (`LineImeState.kt`)
   - Tracks text, selection (cursor), and composition region
   - All IME edits apply to this buffer first

## Key Design Decisions

### 1. Buffer is Source of Truth During IME Operations

The `EditingBuffer` holds the current text state during IME operations. Commands are applied
immediately to the buffer so that IME query methods (`getTextBeforeCursor`, etc.) always
return accurate, up-to-date state.

### 2. Batch Edits Defer Sync/Notification Only

During `beginBatchEdit`/`endBatchEdit`:
- Commands ARE applied immediately to the buffer
- Controller sync and IME notifications are DEFERRED until batch ends

This ensures the IME sees correct state when it queries mid-batch, while avoiding
unnecessary intermediate syncs.

### 3. commitCorrection is Metadata Only

**CRITICAL**: `commitCorrection()` must NOT modify text!

The Android IME calls `commitCorrection` to inform the app about a correction, but the actual
text change comes through a separate `commitText` call. Our implementation just returns true:

```kotlin
override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean {
    // Metadata only - do NOT modify text
    return true
}
```

### 4. Query Methods Don't Sync

Query methods (`getTextBeforeCursor`, `getTextAfterCursor`, etc.) read directly from the
buffer without calling `syncFromController()`. The buffer is the source of truth during
IME operations.

## IME Call Sequences

### Simple Character Input
```
IC.commitText('a', 1)
  → apply CommitText to buffer
  → syncBufferToController
  → NOTIFY updateSelection
```

### Auto-Capitalization (e.g., "Now i" → "Now I ")
```
IC.beginBatchEdit()              → batchDepth = 1
IC.deleteSurroundingText(1, 0)   → delete 'i', deferred
IC.commitCorrection(...)         → metadata only, no buffer change
IC.commitText('I ', 1)           → insert 'I ', deferred
IC.endBatchEdit()                → sync to controller, notify
```

### Spell Check Replacement
```
IC.beginBatchEdit()
IC.setComposingRegion(start, end)  → mark word as composing
IC.commitText(correctedWord, 1)    → replace with correction
IC.endBatchEdit()
```

## Invariants

The implementation maintains these invariants:

1. **Buffer invariants**: selection/composition always in valid ranges
2. **Batch invariants**: batchDepth ≥ 0; if batchDepth = 0, needsSyncAfterBatch = false
3. **Sync invariants**: after sync, controller matches buffer
4. **Notification invariants**: after notification, lastNotified* values match buffer

## Historical Bugs

See `IME_FAILED_APPROACHES.md` for documentation of bugs encountered and failed approaches.

The main bug was `commitCorrection` calling `state.commitText()`, causing double text insertion
when the IME sent both `commitCorrection` (metadata) and `commitText` (actual edit).
