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
