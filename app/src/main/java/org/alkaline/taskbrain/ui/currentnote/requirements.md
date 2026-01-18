# CurrentNoteScreen Selection Menu Requirements

## Requirements

### Selection Menu Behavior
1. **Auto-show menu on selection**: When a text selection is made, automatically show the context menu after the user lifts their finger (not immediately when selection starts)
2. **Menu positioning**: Menu appears near the right of the screen
3. **Menu items order**: Copy, Cut, Select All, Unselect, Delete
4. **No Paste in selection menu**: Paste was removed from the selection context menu

### Selection Gestures
1. **Long-press + drag**: Must work to create selections (native BasicTextField behavior)
2. **Long-press outside selection**: Should create new selection, not be blocked
3. **Tap in existing selection**: Should retain selection (unfortunately in implementation might require restoring selection) and show menu
4. **Gutter selection**: Tapping/dragging in the gutter to select lines should also trigger menu on finger up
5. **Menu timing**: The selection menu should not appear on initial selection dragging or selection resizing, only on finger up

### Menu Actions
1. **Unselect**: Collapses selection to cursor at end, does NOT re-select
2. **Delete**: Removes selected text, places cursor at deletion point, does NOT re-select
3. **Cut**: Copies to clipboard, removes text, places cursor at deletion point, does NOT re-select
4. **Copy**: Copies to clipboard, keeps selection
5. **Select All**: Selects all text
6. **Empty lines**: If a removal of content (Cut/Delete) leaves an empty line because all content from the selected lines was removed, also remove the empty line

### Paste Functionality
1. **Location**: Paste button in CommandBar (bottom toolbar), not floating near cursor
2. **Enabled state**: Only enabled when text field is focused AND there's no selection (just a cursor)
3. **Disabled state**: Grayed out when not focused or when there's a selection
4. **Action**: Pastes clipboard content directly at cursor position

### System Menu
1. **Disabled**: The system text toolbar/context menu is disabled via custom empty TextToolbar implementation

---

## Learnings: What Didn't Work

### Overlay Approaches
1. **detectTapGestures overlay**: Adding a Box with `pointerInput` using `detectTapGestures` on top of BasicTextField blocked long-press gestures entirely, even without consuming events. Compose's gesture system doesn't allow events to "pass through" siblings.

2. **awaitEachGesture overlay**: Same issue - blocked native selection gestures even when trying to detect taps and pass through long-presses.

### Pointer Input on BasicTextField
1. **pointerInput modifier on BasicTextField**: Adding `pointerInput` with `detectTapGestures` directly to BasicTextField's modifier chain didn't receive any tap events. BasicTextField consumes all pointer events internally.

### Floating Paste Button
1. **Floating button near cursor**: Caused the Android magnifier/zoom bubble to appear constantly when the cursor was active. The button's presence near the text input area triggered unwanted system behavior.

2. **Positioning issues**: Converting between pixels and dp was error-prone. `cursorRect` values are in pixels; using them directly with `.dp` caused double-conversion issues.

### Finger Tracking Approaches
1. **pointerInteropFilter**: Not invoked when selection handles are active. Selection handles appear to consume events before they reach the parent's pointerInteropFilter.

2. **PointerEventPass.Initial on parent Box**: Events ARE received when there's no selection, but NOT received when selection handles are visible. The selection handles intercept events.

3. **Fixed timeout fallback (300ms, 800ms)**: Triggered prematurely during natural pauses while dragging selection handles, causing menu to appear mid-drag.

### Selection Handle Touch Events
1. **Selection handles bypass normal touch routing**: When dragging selection handles, touch events don't always flow through the normal Compose pointer event system. The handles may be in a separate window or have special event handling.

---

## Learnings: What Worked

### Activity-Level Touch Tracking
1. **dispatchTouchEvent override**: Overriding `Activity.dispatchTouchEvent()` receives ALL touch events before they reach Compose, including most events that would be consumed by selection handles. Exposed via `StateFlow<Boolean>` for Compose to observe.

### Selection Change Detection
1. **LaunchedEffect keyed on selection**: Using `LaunchedEffect(textFieldValue.selection, textLayoutResult)` to detect selection changes works reliably. The effect restarts whenever selection changes.

2. **Detecting new vs changed selection**: Check if selection is non-collapsed AND (was previously collapsed OR is different from previous). This catches both new selections and resized selections.

### Waiting for Finger Up
1. **StateFlow.first { !it }**: Waiting for `isFingerDownFlow.first { !it }` properly suspends until finger is lifted.

2. **Handling already-up state**: When selection changes but `isFingerDown` is already false (selection handles may bypass dispatchTouchEvent), wait for next down→up cycle: first wait for `first { it }` (finger down), then `first { !it }` (finger up).

### Preventing Re-selection
1. **skipNextRestore flag**: Setting a flag before intentional selection collapse (Unselect, Cut, Delete) prevents the tap-in-selection restore logic from re-selecting the text.

### Disabling System Menu
1. **Custom TextToolbar**: Providing an empty `TextToolbar` implementation via `CompositionLocalProvider(LocalTextToolbar provides emptyTextToolbar)` disables the system's built-in text selection menu.

### CommandBar Paste Button
1. **Focus tracking**: Track focus state via `onFocusChanged` modifier on BasicTextField, lift state to parent, pass to CommandBar.
2. **Enable condition**: `isMainContentFocused && textFieldValue.selection.collapsed` - only enable when focused with cursor (no selection).

---

## Architecture Summary

```
MainActivity
├── dispatchTouchEvent() → MutableStateFlow<Boolean> isFingerDown
└── setContent
    └── MainScreen(isFingerDown: StateFlow<Boolean>)
        └── CurrentNoteScreen(isFingerDownFlow: StateFlow<Boolean>)
            └── MainContentTextField(isFingerDownFlow: StateFlow<Boolean>)
                ├── LaunchedEffect: watches selection changes, waits for finger up
                ├── BasicTextField: wrapped in CompositionLocalProvider with empty TextToolbar
                └── DropdownMenu: selection context menu
```

The key insight is that reliable finger-up detection requires Activity-level event interception because Compose's selection handles don't route events through the normal pointer input system.
