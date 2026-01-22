# IME Implementation - Failed Approaches

DO NOT TRY THESE AGAIN. They don't work.

## The Problem

Characters were duplicated during typing, especially when IME does auto-capitalization
(e.g., typing "Now i" resulted in "Now I I" instead of "Now I").

## Root Cause (Found!)

The bug was in `LineInputConnection.commitCorrection()`:

```kotlin
// WRONG - was doing:
override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean {
    state.setComposingRegion(offset, offset + oldText.length)
    state.commitText(newText, 1)  // ← THIS MODIFIES TEXT - WRONG!
    return true
}
```

According to Android docs, `commitCorrection` is **metadata** about a correction - it does NOT
perform the edit. The actual text change comes through a separate `commitText` call from the IME.

The IME sequence for auto-capitalization is:
1. `beginBatchEdit`
2. `deleteSurroundingText(1, 0)` - delete 'i'
3. `commitCorrection(offset=4, old='i', new='I ')` - **metadata only**
4. `commitText('I ', 1)` - **actual edit**
5. `endBatchEdit`

Our implementation was modifying text in step 3 AND step 4, causing duplication.

**Fix**: `commitCorrection` should only log and return true - never modify text.

## Failed Approaches (Before Finding Root Cause)

### Failed Approach 1: Adding syncFromController() to query methods
- Added sync calls to getTextBeforeCursor, getTextAfterCursor, etc.
- Theory: IME was getting stale data from queries
- Result: FAILED - didn't fix duplication

### Failed Approach 2: IME Notification Infrastructure
- Added ImeNotificationCallback interface
- Called InputMethodManager.updateSelection() after mutations
- Theory: IME's internal buffer was out of sync
- Result: FAILED - didn't fix duplication

### Failed Approach 3: Batch Edit Implementation (deferred notifications)
- Implemented beginBatchEdit/endBatchEdit to defer notifications
- Theory: Notifications during batch were confusing IME
- Result: FAILED - didn't fix duplication

### Failed Approach 4: Tracking lastNotifiedCursor to prevent double notifications
- Added tracking to avoid notifying twice for same cursor position
- Theory: Double notifications were causing IME to commit twice
- Result: FAILED - didn't fix duplication

### Failed Approach 5: Reporting imeSelection in notifications
- When IME sets selection via setSelection(), report that in updateSelection()
- Theory: IME was confused because we reported collapsed cursor instead of selection
- Result: FAILED - didn't fix duplication

### Failed Approach 6: Direct cache update after controller.updateLineContent()
- After calling controller.updateLineContent(), directly set cachedContent/cachedCursor
- Theory: Controller reads were returning stale data due to Compose snapshots
- Result: FAILED - didn't fix duplication

### Failed Approach 7: Removing syncFromController() from all mutation methods
- Made cache the "source of truth", never sync during mutations
- Theory: Any sync during mutations could read stale data
- Result: FAILED - still duplicating

### Failed Approach 8: Command batching with deferred application
- Queued EditCommand objects during batch edit, applied them at endBatchEdit
- Theory: Apply all changes atomically at end of batch
- Result: FAILED - Query methods returned stale state during batch, confusing IME

## What Finally Worked

1. **Apply commands immediately to buffer** - so queries return accurate state
2. **Defer sync/notification during batch** - avoid intermediate updates
3. **Remove text modification from commitCorrection** - it's metadata only!

See `IME_IMPLEMENTATION.md` for the correct implementation details.
