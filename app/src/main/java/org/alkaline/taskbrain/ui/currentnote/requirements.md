# CurrentNoteScreen Editor Requirements

## Text Selection

### Selection Gestures
1. **Tap to position cursor**: Single tap places cursor at tapped position
2. **Long-press to select word**: Long press (~500ms) selects the word at press location
3. **Drag to extend selection**: After long press triggers, dragging extends selection
4. **Long-press outside selection**: Creates new selection (doesn't extend existing)
5. **Tap in existing selection**: Toggles context menu visibility (retains selection)
6. **Tap outside selection**: Collapses selection and positions cursor at tap location

### Selection Handles
1. **Teardrop handles**: Start and end handles appear at selection boundaries
2. **Handle dragging**: Both handles can be dragged to adjust selection
3. **Accumulated delta tracking**: Handles use delta accumulation for smooth dragging
4. **Handle positioning**: Start handle points left, end handle points right

### Gutter Selection
1. **Line selection by tap**: Tapping gutter selects entire line
2. **Multi-line selection by drag**: Dragging in gutter selects range of lines
3. **Visual feedback**: Selected lines show highlight in gutter area
4. **Menu on completion**: Context menu appears after gutter selection completes

## Selection Context Menu

### Menu Behavior
1. **Auto-show on selection**: Menu appears after finger lifts (not during drag)
2. **Positioning**: Menu positions left or right of selection based on available space
3. **Multi-line positioning**: Multi-line selections position menu at right edge
4. **Vertical centering**: Menu vertically centers on the selection
5. **Menu items order**: Copy, Cut, Select All, Unselect, Delete

### Menu Actions
1. **Copy**: Copies selected text to clipboard, keeps selection
2. **Cut**: Copies to clipboard, removes text, places cursor at deletion point
3. **Select All**: Selects entire document text
4. **Unselect**: Collapses selection to cursor at start of selection
5. **Delete**: Removes selected text, places cursor at deletion point

### Full Line Selection Extension
1. **Newline inclusion**: When full lines are selected (selection starts at line beginning and ends at line end), the trailing newline is included in copy/cut
2. **Clean deletion**: This ensures deleting full lines removes the newline too, avoiding empty lines
3. **Partial line exclusion**: Selections starting mid-line do not extend to include trailing newline

### Empty Line Cleanup
1. **Auto-removal**: Deleting/cutting that leaves an empty line removes that line
2. **Last line preservation**: Always keeps at least one line for continued typing
3. **Pre-existing preservation**: Pre-existing empty lines (spacers) are not removed

## Line Prefixes

### Prefix Types
1. **Bullet**: Display `• ` (user types `* `)
2. **Unchecked checkbox**: Display `☐ ` (user types `[]`)
3. **Checked checkbox**: Display `☑ ` (user types `[x]`)
4. **Tab indentation**: Leading tabs increase nesting level

### Prefix Behavior
1. **Prefix extraction**: System separates prefix (tabs + bullet/checkbox) from content
2. **Prefix continuation**: Enter key continues prefix on new line
3. **Checked → unchecked**: New line from checked checkbox becomes unchecked
4. **Prefix-aware backspace**: Backspace at content start removes prefix character before merging lines

### Toggle Behavior
1. **Toggle bullet**: Cycles: none → bullet → none; replaces checkbox if present
2. **Toggle checkbox**: Cycles: none → unchecked → checked → none; replaces bullet if present
3. **Checkbox state toggle**: Checked ↔ unchecked only (via gutter tap on checkbox lines)
4. **Indentation preserved**: Toggling prefix preserves leading tabs

## Indentation

### Basic Operations
1. **Indent**: Adds tab at line start (toolbar button)
2. **Unindent**: Removes tab from line start if present (toolbar button)
3. **Multi-line support**: Indent/unindent applies to all selected lines

### Space Key with Selection
1. **Single space indents**: Pressing space with selection indents all selected lines
2. **Double-space unindents**: Two spaces within 250ms undoes indent and unindents further
3. **Selection preserved**: Selection is maintained and adjusted after indent/unindent

## Text Editing

### Basic Operations
1. **Character insertion**: Type text at cursor position
2. **Backspace**: Delete character before cursor
3. **Forward delete**: Delete character after cursor
4. **Line joining**: Backspace at line start merges with previous line
5. **Line splitting**: Enter splits line at cursor with prefix continuation

### IME Integration
1. **Composing text**: Handles autocomplete suggestions from IME
2. **Text commit**: Accepts committed text from IME
3. **Hardware keyboard**: Backspace, delete, and enter work from physical keyboard

## Cursor Behavior

1. **Blinking cursor**: Cursor blinks at 500ms interval when focused
2. **Hidden during selection**: Cursor not shown when text is selected
3. **Auto-positioning**: Cursor moves to selection start when selection is made

## Toolbar (CommandBar)

### Buttons
1. **Bullet toggle**: Toggles bullet prefix on current/selected lines
2. **Checkbox toggle**: Toggles checkbox on current/selected lines
3. **Indent**: Indents current/selected lines
4. **Unindent**: Unindents current/selected lines
5. **Paste**: Pastes clipboard content at cursor position
6. **Alarm**: Opens alarm configuration dialog

### Paste Button State
1. **Enabled**: Only when editor is focused AND no selection (just cursor)
2. **Disabled**: Grayed out when not focused or when there's a selection
3. **Action**: Pastes clipboard text directly at cursor position

### Alarm Button State
1. **Enabled**: Only when editor is focused AND no selection (just cursor)
2. **Disabled**: When not focused or when there's a selection

## System Menu

1. **Disabled**: System text toolbar/context menu not used
2. **Custom implementation**: Selection handled via custom ImeConnection + BasicText

## Undo/Redo

### Core Behavior
1. **Line-level granularity**: Edits to a line are grouped until focus leaves that line
2. **Structural operations**: Enter commits prior edits (then groups with subsequent typing); line merges are separate undo steps
3. **Undo stack**: Stores up to 50 snapshots of editor state
4. **Redo stack**: Populated when undo is performed; cleared when new edit is made

### Undo Boundaries (when a snapshot is captured)
1. **Focus change**: Moving cursor to a different line commits pending edits
2. **Enter key**: Commits preceding typing, then groups Enter + subsequent typing together
3. **Line merge**: Backspace at line start or delete at line end creates its own undo step (subsequent typing is separate)
4. **Save button**: Commits before saving, then starts new undo group for subsequent typing
5. **Undo/Redo buttons**: Commits current edits before performing operation
6. **Navigate away**: ON_STOP lifecycle event commits pending state
7. **Alarm button**: Commits before showing alarm dialog, then starts new undo group

### Save Creates Undo Boundary
Saving creates an undo point while allowing continued editing on the same line:
1. **Example**: Type "hello", save, type " world" → first undo removes " world", second undo removes "hello"
2. **Rationale**: Users often save mid-edit and expect to be able to undo back to the saved state
3. **Subsequent typing**: After save, typing starts a new undo group (not merged with pre-save typing)

### Enter Key Behavior
1. **Enter + subsequent typing merged**: Enter and any typing on the new line are grouped as one undo step
2. **Preceding typing separate**: Typing before Enter is committed as a separate undo step when Enter is pressed
3. **Example**: Type "hello", Enter, type "world" → first undo removes Enter + "world", second undo removes "hello"
4. **Without preceding typing**: Enter alone creates one undo step
5. **Prefix continuation**: Undo removes the automatically-added prefix on the new line

**Design Note**: The Enter + subsequent typing grouping is intentionally non-standard (most text editors treat Enter as a separate undo step from subsequent typing). This design reduces undo steps for common task entry patterns where users type a line and continue to the next. The tradeoff is that users cannot undo just the typing on the new line without also undoing the Enter. This behavior was explicitly chosen and should not be changed without careful consideration.

### Command Bar Grouping
1. **Bullet toggle**: Each press is a separate undo step
2. **Checkbox toggle**: Each press is a separate undo step
3. **Indent**: Consecutive presses are grouped into one undo step
4. **Unindent**: Consecutive presses are grouped with indent
5. **Indent/Unindent sequence**: Any sequence of indent/unindent is one undo step
6. **Sequence broken by**: Typing, focus change, or other command breaks the sequence

### Selection Menu Actions
1. **Cut**: Creates an undo step before copying to clipboard and deleting selection
2. **Delete**: Creates an undo step before deleting selection
3. **Copy**: No undo point (doesn't modify content)
4. **Select All**: No undo point (doesn't modify content)
5. **Unselect**: No undo point (doesn't modify content)

### Other Undoable Operations
1. **Gutter checkbox toggle**: Tapping checkbox in gutter creates an undo step
2. **Space with selection**: Pressing space to indent selected lines uses indent grouping (consecutive presses grouped)
3. **Paste**: Always creates an undo step before pasting, regardless of whether there were prior edits on the line

### Alarm Undo/Redo
1. **Undo alarm creation**: Permanently deletes the alarm from Firestore (not cancellation)
2. **Redo alarm creation**: Recreates alarm with same time configuration (new ID assigned)
3. **Multiple cycles**: Alarm ID is updated in snapshot after redo for future undo/redo
4. **Redo failure handling**: If alarm recreation fails during redo, the document is automatically rolled back and a warning dialog explains what happened. If rollback itself fails, a stronger warning indicates potential inconsistency
5. **Protection during operations**: Undo/redo buttons are disabled while alarm operations are in progress to prevent race conditions

### UI
1. **Undo button**: Located in StatusBar, right-aligned; enabled when there are uncommitted changes OR undo stack is not empty
2. **Redo button**: Located in StatusBar, right-aligned; disabled when redo stack empty
3. **State after undo/redo**: Document marked as unsaved
4. **Immediate activation**: Undo button becomes active as soon as user starts typing (even before focus changes), because uncommitted changes can be undone

### Persistence
1. **Session-scoped**: Undo history persists while app is backgrounded but clears on app restart
2. **Per-note storage**: Each note has its own persisted undo history (keyed by note ID)
3. **Save trigger**: State saved to disk when app goes to ON_STOP lifecycle state
4. **Restore trigger**: State restored when note is loaded (if persisted state exists)
5. **Storage location**: JSON files in app's cache directory (`/cache/undo_state/`)
6. **Cold start cleanup**: All persisted undo state cleared in Application.onCreate()

### Baseline (Floor) State
1. **Purpose**: Prevents accidentally undoing past the loaded note state to an empty or wrong document
2. **When set**: First time a note is loaded since app start (if no persisted state exists)
3. **Behavior**: When undo stack is empty, undo returns to the baseline instead of doing nothing
4. **Persistence**: Baseline is persisted with undo state when app backgrounds and restored on resume
5. **CRITICAL Implementation Note**: `editorState.updateFromText()` MUST be called BEFORE `setBaseline()`. Otherwise the baseline captures empty content and defeats its purpose. This is an integration timing issue that unit tests cannot catch - see test "CRITICAL - baseline must contain actual content"

### Edge Cases
1. **Empty edits**: If user focuses a line but makes no changes, no undo point created
2. **Multi-line paste**: Treated as single undo operation
3. **New note**: Undo history is reset when loading a note (unless persisted state exists from backgrounding)

### Design Decisions

**History Size Limit (50 snapshots, silent drop)**
When the undo stack reaches 50 entries and a new snapshot is added, the oldest snapshot is silently dropped. No warning is shown to the user. Rationale: Users rarely need to undo more than 50 steps, and showing a warning would be distracting without providing actionable information.

**Session-Scoped Persistence (cleared on cold start)**
Undo history is persisted while the app is backgrounded but is cleared when the app is cold-started (killed and reopened). Rationale: Undo history is session-scoped by design. After an app restart, the user is effectively starting a new editing session, and stale undo history from a previous session could be confusing or lead to unexpected behavior.

**Command Bar Grouping Inconsistency (indent grouped, bullet/checkbox not grouped)**
Consecutive indent/unindent presses are grouped into a single undo step, but consecutive bullet/checkbox toggles are NOT grouped (each press is a separate undo step). Rationale: Indent is incremental (each press adds one level), so grouping makes sense—users often want to undo all indent changes at once. Bullet/checkbox is binary state toggle, so each press is a meaningful discrete action that users may want to undo individually.

**Selection Cleared on Undo**
When undo/redo is performed, any existing text selection is cleared. The selection is not stored in snapshots. Rationale: Selections are transient UI state, not document state. Restoring a selection when the underlying content has changed doesn't make semantic sense—the selected range may not correspond to the same text anymore.

### Implementation Architecture

**Operation-Based Undo System**
All discrete editing operations (commands, clipboard, etc.) flow through `EditorController` which handles undo boundary management automatically. This centralizes undo logic and prevents bypass:

1. **EditorController** is the single channel for state modifications that need undo tracking
2. **OperationType enum** classifies operations for undo handling:
   - `COMMAND_BULLET`, `COMMAND_CHECKBOX`: Each creates a separate undo step
   - `COMMAND_INDENT`: Consecutive presses are grouped
   - `PASTE`, `CUT`, `DELETE_SELECTION`: Always create their own undo step
   - `CHECKBOX_TOGGLE`: Gutter checkbox tap (same as COMMAND_CHECKBOX)
   - `ALARM_SYMBOL`: Commits pending state, creates undo point
3. **Operation executor** (`executeOperation`) wraps operations with proper pre/post undo handling
4. **EditorState internal methods**: Mutation methods on EditorState are `internal` to prevent direct access from UI code (must go through EditorController)

**Operations NOT in OperationType**
- **TYPING**: Handled implicitly via focus changes (when cursor moves to different line)
- **ENTER**: Has specialized handling in `splitLine()` - commits prior typing, groups with subsequent typing
- **LINE_MERGE**: Has specialized handling in `mergeToPreviousLine()`/`mergeNextLine()` - always creates own undo step

**Flow Example (Toolbar Button Press)**
```
User presses Bullet button
  → CurrentNoteScreen calls controller.toggleBullet()
  → EditorController.executeOperation(COMMAND_BULLET) {
      handlePreOperation() - calls undoManager.recordCommand()
      state.toggleBulletInternal() - actually modifies state
      handlePostOperation() - calls undoManager.commitAfterCommand()
    }
```

**Flow Example (Typing)**
```
User types characters on line 1
  → IME sends updates to LineImeState
  → LineImeState calls controller.updateLineContent()
  → EditorController updates state (no undo boundary yet)

User taps on line 2
  → Focus change triggers controller.focusLine(2)
  → EditorController calls undoManager.commitPendingUndoState()
  → Previous typing on line 1 is committed as undo point
  → EditorController calls undoManager.beginEditingLine(2)
```
