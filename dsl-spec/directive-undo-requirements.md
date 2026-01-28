# Directive Undo/Redo Requirements

Requirements for coherent treatment of undo/redo with respect to Mindl directives.

---

## Context

Directives introduce new editing patterns that need undo support:
- **Display vs source text**: Computed results replace source text visually
- **Edit row**: Directives are edited in a separate row below the line
- **Mixed content**: Lines can contain both plain text and directives
- **Execution timing**: Directives execute on save and load (cached results)
- **Collapsed state**: UI state (collapsed/expanded) stored in Firestore

---

## Core Principle

* Undo/redo operates on source text only.
* Directive results are derived from source text and re-computed as needed. The undo system should not store or restore computed results—only the source text that produces them.

---

## Requirements

### 1. Typing Around Directives

**Scenario**: Line contains `hello [42] world`, user edits surrounding text.

| Requirement | Description |
|-------------|-------------|
| 1.1 | Typing in non-directive portions follows standard line-level grouping (commits on focus change) |
| 1.2 | Undo restores the source text; directive result display updates automatically |
| 1.3 | Cursor positioning accounts for display/source offset differences |

**Example**:
```
Initial:    hello [42] world
User types: hello [42] there world
Undo:       hello [42] world  (cursor at appropriate position)
```

---

### 2. Directive Edit Row

**Scenario**: User taps computed directive, edit row appears, user modifies source.

| Requirement | Description |
|-------------|-------------|
| 2.1 | **Confirm creates undo point**: Confirming edit row changes creates a single undo step containing the source text change |
| 2.2 | **Cancel is not undoable**: Canceling edit row discards changes without affecting undo stack |
| 2.3 | **Edit row edits are atomic**: The entire edit row session (open → modify → confirm) is one undo step |
| 2.4 | **Pre-edit state captured**: Undo after confirm restores the directive source text as it was before the edit row opened |
| 2.5 | **Consecutive confirms not grouped**: Each confirm is a separate undo step (unlike indent grouping) |

**Example**:
```
Initial:    text [42] more
Tap directive, edit row shows: [42]
User changes to: [99]
Confirm
Line now:   text [99] more
Undo:       text [42] more
```

**Rationale for 2.5**: Unlike indent (incremental), directive edits are semantic changes that users likely want to undo individually.

---

### 3. Typing New Directives

**Scenario**: User types `[42]` character by character on a line.

| Requirement | Description |
|-------------|-------------|
| 3.1 | Typing follows standard line-level grouping (no special handling for bracket characters) |
| 3.2 | Directive is not recognized/computed until save (or until `executeDirectivesLive` is called) |
| 3.3 | Undo of partially-typed directive works as normal character undo |
| 3.4 | After undo, directives on affected lines are re-executed to keep results in sync |

**Example**:
```
User types: [4
Undo:       [
Undo:       (empty)

Or if saved between:
User types: [42] then saves (directive computed, shows "42")
Undo:       [4   (re-executed, now shows "[4" as source since incomplete)
Redo:       [42] (re-executed, shows "42" again)
```

---

### 4. Deleting Content with Directives

**Scenario**: User selects and deletes text containing directives.

| Requirement | Description |
|-------------|-------------|
| 4.1 | Delete creates undo point per normal selection delete behavior |
| 4.2 | Undo restores the source text including directive source |
| 4.3 | Undo triggers re-execution of directives on restored lines |
| 4.4 | If directive source matches existing cache (same hash), cached result may be reused |

**Example**:
```
Initial:    hello [42] world
Select all, delete
Line:       (empty)
Undo:       hello [42] world  (directive re-executed; cache hit likely)
```

---

### 5. Cut/Copy/Paste with Directives

| Requirement | Description |
|-------------|-------------|
| 5.1 | Cut/copy copies source text (not display text) |
| 5.2 | Paste inserts source text as normal |
| 5.3 | Cut follows normal cut undo behavior (creates undo point) |
| 5.4 | Paste follows normal paste undo behavior (creates undo point) |
| 5.5 | Pasted directives are not computed until save |

**Example**:
```
Line A:     hello [42] world
Select "[42]", cut
Line A:     hello  world
Paste elsewhere
Line B:     here [42] is pasted  (shows "[42]" as source until save)
```

---

### 6. Line Operations (Move, Merge, Split)

| Requirement | Description |
|-------------|-------------|
| 6.1 | Moving lines with directives follows normal move grouping |
| 6.2 | Line merge (backspace at line start) follows normal merge undo behavior |
| 6.3 | Line split (Enter) follows normal split undo behavior (Enter + subsequent typing grouped) |
| 6.4 | Directive results are position-independent (keyed by hash), so operations don't affect cache |

---

### 7. Collapsed State

| Requirement | Description |
|-------------|-------------|
| 7.1 | Collapsed/expanded state is **not** part of undo history |
| 7.2 | Toggling collapsed state does not create an undo point |
| 7.3 | After undo/redo, collapsed states are preserved (not reset) |

**Rationale**: Collapsed state is UI/view state, not document content. Similar to cursor position, it's not undoable.

---

### 8. Directive Execution Results

| Requirement | Description |
|-------------|-------------|
| 8.1 | Undo invalidates cached results for affected lines and triggers re-execution |
| 8.2 | Redo invalidates cached results for affected lines and triggers re-execution |
| 8.3 | Re-execution happens synchronously before UI updates |
| 8.4 | Only directives on changed lines are re-executed (not entire document) |
| 8.5 | Save also re-executes directives and updates Firestore cache |

**Rationale**: Immediate re-execution ensures displayed results always match source text. This avoids user confusion from seeing stale results after undo. The cost (re-execution time) is acceptable since directive evaluation is fast for simple expressions.

---

## Implementation Considerations

### Edit Row Integration with EditorController

The edit row creates a unique editing context that needs special handling:

```
Option A: Edit row modifies source directly, EditorController sees it as line change
  - Pro: Minimal new infrastructure
  - Con: May interfere with line-level undo grouping

Option B: Edit row is a separate editing mode with its own undo boundary
  - Pro: Clean separation
  - Con: More complexity

Recommendation: Option A with explicit undo boundary on confirm
```

**Proposed flow**:
1. User taps directive → edit row opens (no undo point yet)
2. User edits in edit row (changes not in undo history)
3. User confirms →
   - Commit any pending undo state
   - Apply change to line content
   - Record undo point with pre-edit state
4. User cancels → discard edit row changes (no undo impact)

### New OperationType

Add to `OperationType` enum:
```kotlin
DIRECTIVE_EDIT  // Confirming a directive edit row change
```

Behavior:
- Always creates its own undo step (not grouped with previous operations)
- Not grouped with subsequent operations

### EditorController Addition

```kotlin
fun confirmDirectiveEdit(
    lineIndex: Int,
    directiveSourceRange: IntRange,  // Where in source the directive is
    newDirectiveText: String          // New source text for directive
)
```

This method:
1. Commits pending undo state
2. Replaces the directive text in the line
3. Creates undo point with DIRECTIVE_EDIT operation type

### Undo/Redo Re-execution Hook

After undo or redo completes, the system must re-execute directives on affected lines:

```kotlin
// In UndoManager or EditorController, after applying undo/redo:
fun onUndoRedoComplete(affectedLineIndices: Set<Int>) {
    // Re-execute directives on affected lines
    for (lineIndex in affectedLineIndices) {
        val content = editorState.getLineContent(lineIndex)
        viewModel.executeDirectivesLive(content)  // Or line-specific variant
    }
}
```

**Implementation options**:
1. Track which lines changed during undo/redo and re-execute only those
2. Re-execute all directives in document (simpler but slower for large docs)
3. Invalidate cache for affected lines and let UI trigger lazy re-execution

Recommended: Option 1 for efficiency, since undo snapshots can track changed lines.

---

## Edge Cases

### E1: Edit Row Open During Undo

| Case | Behavior |
|------|----------|
| User has edit row open, presses undo button | Close edit row (discard changes), then perform undo |

**Rationale**: Edit row changes are ephemeral until confirmed. Undo operates on committed state.

### E2: Undo Restores Line with Directive Currently Being Edited

| Case | Behavior |
|------|----------|
| Line has `[42]`, edit row is open editing it, undo reverts line | Close edit row, restore previous line content |

### E3: Multiple Directives on One Line

| Case | Behavior |
|------|----------|
| Line has `[1] and [2]`, user edits `[1]` via edit row | Only `[1]` changes; `[2]` unaffected |
| Undo after confirm | Restores `[1]` to previous value; `[2]` unchanged |

### E4: Directive Text Contains What Looks Like Directive

| Case | Behavior |
|------|----------|
| User types `["[inner]"]` | This is one directive with string content "[inner]" |
| Undo | Works normally on source text; directive re-executed |

---

## Out of Scope (Future Milestones)

These scenarios involve later Mindl features and will need additional requirements:

1. **View directives** (Milestone 9): Edits to viewed content propagate to source notes
2. **Refresh directives** (Milestone 10): Dependency tracking may affect undo
3. **Schedule directives** (Milestone 13): Scheduled actions are not undoable
4. **Note mutation directives** (Milestone 12): Side effects need careful undo design

---

## Summary

The key insight is that **undo operates on source text, then results are recomputed**. This keeps the undo system predictable while ensuring UI consistency:

1. Edit row confirms create undo points
2. Edit row cancels don't affect undo
3. Collapsed state is not undoable
4. Undo/redo invalidates cached results and triggers re-execution on affected lines
5. Displayed results always match source text

The implementation requires:
- New `DIRECTIVE_EDIT` operation type
- New `confirmDirectiveEdit()` method on EditorController
- Edit row cancellation/close on undo when open
- Re-execution hook after undo/redo completes
