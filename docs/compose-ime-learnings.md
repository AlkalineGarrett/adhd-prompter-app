# Android IME (Input Method Editor) Handling: Key Learnings

---

## WARNING: DO NOT USE BasicTextField

**BasicTextField is BANNED in this codebase.**

### Why BasicTextField Cannot Be Used

#### The Fundamental Problem: Single-Field Selection

BasicTextField's selection model is **confined to a single text field**. Our note editor requires:
- Multiple lines, each as a separate focusable/editable unit
- Cross-line text selection via long-press and drag
- Coordinated cursor movement across lines (arrow keys, Enter to create new line, Backspace to merge lines)

**BasicTextField cannot support multi-field selection.** Its selection handles, magnifier, and gesture handling all assume a single contiguous text buffer. When you have multiple BasicTextFields (one per line), each operates in isolation - you cannot select text spanning multiple lines.

#### Built-in Behaviors That Cannot Be Disabled

BasicTextField comes with built-in UI behaviors that **CANNOT be fully disabled**:

1. **Magnifier bubble** - Appears during text selection, follows finger position
2. **Selection handles** (teardrops) - Can be hidden with transparent colors but behavior remains
3. **Context menu** - Can be disabled with NoOpTextToolbar but gesture handling remains
4. **Internal gesture handling** - Interferes with custom selection implementations

Even with all these workarounds:
```kotlin
CompositionLocalProvider(
    LocalTextToolbar provides NoOpTextToolbar,
    LocalTextSelectionColors provides transparentSelectionColors
) {
    BasicTextField(
        modifier = Modifier.pointerInput(Unit) {
            // Consume all events
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                }
            }
        }
    )
}
```

**The magnifier STILL appears** because BasicTextField's internal gesture handling runs before our modifiers can intercept.

### The Correct Approach: ImeConnection

This codebase uses a custom `ImeConnection` modifier that implements `PlatformTextInputModifierNode` directly. This provides:

- Direct IME connection without any UI baggage
- Full control over text state
- No magnifier, no handles, no context menu, no surprises

See `ui/currentnote/ImeConnection.kt` for the implementation.

```kotlin
// CORRECT - Use ImeConnection
Box(
    modifier = Modifier
        .focusRequester(focusRequester)
        .focusable()
        .imeConnection(state = imeState)
) {
    BasicText(text = text) // Display only, no IME
}

// WRONG - DO NOT USE
BasicTextField(state = textFieldState) // Has unwanted UI behaviors
```

---

## Overview

This document captures learnings from implementing custom text input with IME handling in Jetpack Compose, specifically when using `TextInputService` directly instead of `BasicTextField`.

## How Android IME Handles Backspace

### Modern Android (API 16+)

The IME typically sends `deleteSurroundingText(1, 0)` rather than key events. In Compose, this translates to `DeleteSurroundingTextCommand`.

**Critical warning from Android documentation:**
> "As soft input methods can use multiple and inventive ways of inputting text, there is no guarantee that any key press on a soft keyboard will generate a key event: this is left to the IME's discretion, and in fact sending such events is discouraged."

The default Android keyboard does NOT send `KEYCODE_DEL` key events. Instead, it calls `deleteSurroundingText(1, 0)` on the `InputConnection`.

## The Internal Buffer Problem

### Why Backspace Sometimes Doesn't Work

**The IME maintains an internal buffer.** When this buffer is empty or out of sync, the IME won't send delete commands because it thinks there's nothing to delete.

Scenarios where no command is sent:
1. **Empty internal IME buffer** - If the IME's internal composition buffer is empty, backspace presses are ignored
2. **State desynchronization** - If `updateState()` doesn't properly sync the IME's buffer with your text
3. **Cursor at position 0 with no composition** - Some IMEs won't send commands if `getTextBeforeCursor()` returns empty

### The Three Copies of State Problem

TextField implementations involve holding 3 copies of state:
1. **IME state** - What the keyboard thinks the text is
2. **User state holder** - Your `TextFieldValue` or state
3. **Internal snapshot** - Compose's internal representation

These copies can fall out of sync, causing issues like backspace not working.

## TextInputService Usage

### Starting an Input Session

```kotlin
val textInputService = LocalTextInputService.current

textInputSession = textInputService?.startInput(
    value = TextFieldValue(
        text = initialText,
        selection = TextRange(cursorPosition)
    ),
    imeOptions = ImeOptions(
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.None
    ),
    onEditCommand = { commands ->
        // Process edit commands from IME
        for (command in commands) {
            when (command) {
                is CommitTextCommand -> { /* insert text */ }
                is DeleteSurroundingTextCommand -> { /* delete text */ }
                is SetComposingTextCommand -> { /* handle composition */ }
                is BackspaceCommand -> { /* delete char before cursor */ }
                // etc.
            }
        }
    },
    onImeActionPerformed = { action -> /* handle IME actions */ }
)
```

### Updating IME State

```kotlin
textInputSession?.updateState(
    oldValue = previousValue,  // What IME currently thinks
    newValue = newValue        // What we want it to be
)
```

**Warning:** `updateState()` may not reliably sync the IME's internal buffer, especially for large text changes.

## Common Edit Commands

| Command | Description |
|---------|-------------|
| `CommitTextCommand` | Insert finalized text at cursor |
| `SetComposingTextCommand` | Set temporary composition text (autocomplete) |
| `DeleteSurroundingTextCommand` | Delete characters around cursor |
| `BackspaceCommand` | Delete character before cursor |
| `FinishComposingTextCommand` | Finalize any active composition |
| `SetSelectionCommand` | Change cursor/selection position |

## Composition (Autocomplete) Handling

The IME uses a "composing region" for autocomplete/suggestions:

1. `SetComposingTextCommand` sets temporary text that may change
2. The composing region should be tracked separately
3. `CommitTextCommand` finalizes composition and clears the region
4. `FinishComposingTextCommand` commits without adding text

```kotlin
data class EditResult(
    val text: String,
    val cursor: Int,
    val composingStart: Int,  // -1 if no composition
    val composingEnd: Int     // -1 if no composition
)
```

## Known Issues and Workarounds

### Issue: Backspace stops working after switching context

**Cause:** IME's internal buffer doesn't match your text content.

**Solution:** Restart the input session when text changes externally:
```kotlin
LaunchedEffect(text) {
    if (textInputSession != null && text != imeKnownText) {
        textInputSession?.dispose()
        textInputSession = textInputService?.startInput(
            value = TextFieldValue(text = text, selection = ...),
            // ... same parameters
        )
    }
}
```

### Issue: IME buffer becomes empty

**Workaround:** Some developers keep a "garbage character" in the IME buffer that the keyboard thinks it's editing, separate from the actual displayed text.

### Issue: State synchronization race conditions

**Solution:** Use `rememberUpdatedState` for values accessed in callbacks:
```kotlin
val currentText by rememberUpdatedState(text)
val currentOnTextChange by rememberUpdatedState(onTextChange)

// In callback:
onEditCommand = { commands ->
    // currentText always has latest value
}
```

## BasicTextField vs Manual TextInputService

### Why BasicTextField works better

1. Uses `RecordingInputConnection` for proper IME communication
2. Automatically syncs state between Compose and IME
3. Properly manages composition spans
4. Handles edge cases (empty text, cursor at boundaries)

### Why you might use TextInputService directly

1. Need complete control over gesture handling
2. BasicTextField's built-in gestures conflict with your needs
3. Building a highly custom text editing experience

### Recommendation

If possible, use **BasicTextField2** (now stable as `BasicTextField` in Foundation 1.8+) with `TextFieldState`. It avoids async state issues entirely.

If you must use `TextInputService` directly:
1. Track what the IME thinks the text is separately
2. Restart sessions on external text changes rather than using `updateState()`
3. Properly handle composition regions
4. Test extensively with different keyboards (GBoard, Samsung, SwiftKey, etc.)

## Best Practices

1. **Always track IME-known state** - Know what the IME thinks the text is
2. **Restart sessions on external changes** - Don't rely solely on `updateState()`
3. **Handle all edit commands** - Even ones you think won't be sent
4. **Test with multiple keyboards** - Different IMEs behave differently
5. **Log commands during development** - Essential for debugging
6. **Preserve composition state** - Don't accidentally clear it

## The Modern Approach: BasicTextField with TextFieldState

### Why TextInputService is Deprecated

`TextInputService` and `TextInputSession` are deprecated as of Compose Foundation 1.7+. The deprecation message says: "Use PlatformTextInputModifierNode instead."

The core problem with `TextInputService.startInput()` is that it doesn't reliably populate the IME's internal editing buffer. Even when you pass a `TextFieldValue` with text, the IME may not internalize it for deletion purposes. This causes backspace to silently fail because the IME thinks there's nothing to delete.

### The Recommended Solution

Use **modern BasicTextField with TextFieldState**:

```kotlin
@Composable
fun MyTextInput(
    text: String,
    onTextChange: (String) -> Unit
) {
    val textFieldState = rememberTextFieldState(text)

    // Sync external text changes TO TextFieldState
    LaunchedEffect(text) {
        if (text != textFieldState.text.toString()) {
            textFieldState.edit {
                replace(0, length, text)
            }
        }
    }

    // Observe TextFieldState changes and notify parent
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collectLatest { newText ->
                if (newText != text) {
                    onTextChange(newText)
                }
            }
    }

    BasicTextField(
        state = textFieldState,
        // ... other parameters
    )
}
```

### Disabling BasicTextField's Built-in Context Menu

BasicTextField shows a "Paste / Select All" menu on long press. To disable it, provide a no-op `TextToolbar`:

```kotlin
private object NoOpTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        // Do nothing - disables the context menu
    }

    override fun hide() {}
}

// Usage:
CompositionLocalProvider(
    LocalTextToolbar provides NoOpTextToolbar
) {
    BasicTextField(...)
}
```

### Hiding BasicTextField's Selection Handles (Teardrops)

BasicTextField shows teardrop selection handles. To hide them (e.g., when implementing custom selection), use transparent `TextSelectionColors`:

```kotlin
val transparentSelectionColors = TextSelectionColors(
    handleColor = Color.Transparent,
    backgroundColor = Color.Transparent
)

CompositionLocalProvider(
    LocalTextToolbar provides NoOpTextToolbar,
    LocalTextSelectionColors provides transparentSelectionColors
) {
    BasicTextField(...)
}
```

### Complete Example: Custom Text Input with Disabled Selection UI

```kotlin
@Composable
fun ManualTextInput(
    text: String,
    cursorPosition: Int,
    onTextChange: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val textFieldState = rememberTextFieldState(text)
    var isSyncingFromExternal by remember { mutableStateOf(false) }

    // Sync external changes to TextFieldState
    LaunchedEffect(text, cursorPosition) {
        if (text != textFieldState.text.toString()) {
            isSyncingFromExternal = true
            textFieldState.edit {
                replace(0, length, text)
                selection = TextRange(cursorPosition.coerceIn(0, text.length))
            }
            isSyncingFromExternal = false
        }
    }

    // Notify parent of TextFieldState changes
    LaunchedEffect(textFieldState) {
        snapshotFlow {
            textFieldState.text.toString() to textFieldState.selection.start
        }.collectLatest { (newText, newCursor) ->
            if (!isSyncingFromExternal && (newText != text || newCursor != cursorPosition)) {
                onTextChange(newText, newCursor)
            }
        }
    }

    val transparentSelectionColors = TextSelectionColors(
        handleColor = Color.Transparent,
        backgroundColor = Color.Transparent
    )

    CompositionLocalProvider(
        LocalTextToolbar provides NoOpTextToolbar,
        LocalTextSelectionColors provides transparentSelectionColors
    ) {
        BasicTextField(
            state = textFieldState,
            textStyle = textStyle,
            cursorBrush = SolidColor(Color.Black),
            modifier = modifier
        )
    }
}
```

### Key Imports for Modern Approach

```kotlin
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
```

## The Nuclear Option: Custom PlatformTextInputModifierNode

When BasicTextField's built-in behaviors are unacceptable (magnifier, selection handles, etc.), you can implement IME integration directly using `PlatformTextInputModifierNode`.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Your Composable                          │
│  ┌─────────────────┐  ┌──────────────────────────────────┐ │
│  │   BasicText     │  │         ImeConnection            │ │
│  │  (display only) │  │  (PlatformTextInputModifierNode) │ │
│  └─────────────────┘  └──────────────────────────────────┘ │
│                                    │                        │
└────────────────────────────────────│────────────────────────┘
                                     │
                    ┌────────────────▼────────────────┐
                    │   establishTextInputSession()   │
                    │   startInputMethod(request)     │
                    └────────────────┬────────────────┘
                                     │
                    ┌────────────────▼────────────────┐
                    │   PlatformTextInputMethodRequest │
                    │   createInputConnection()        │
                    └────────────────┬────────────────┘
                                     │
                    ┌────────────────▼────────────────┐
                    │     Android InputConnection      │
                    │   commitText(), deleteSurrounding│
                    │   Text(), setComposingText()     │
                    └────────────────┬────────────────┘
                                     │
                    ┌────────────────▼────────────────┐
                    │         Soft Keyboard (IME)      │
                    └──────────────────────────────────┘
```

### Key Components

1. **ImeState** - Holds text, cursor position, and composition state
2. **ImeConnectionNode** - Implements `PlatformTextInputModifierNode` and `FocusEventModifierNode`
3. **SimpleInputConnection** - Implements Android's `InputConnection` interface

### Implementation Pattern

```kotlin
class ImeConnectionNode : DelegatingNode(),
    PlatformTextInputModifierNode,
    FocusEventModifierNode {

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused) {
            coroutineScope.launch {
                establishTextInputSession {
                    val request = PlatformTextInputMethodRequest { outAttrs ->
                        configureEditorInfo(outAttrs)
                        SimpleInputConnection(state)
                    }
                    startInputMethod(request) // Suspends until cancelled
                }
            }
        }
    }
}
```

### CRITICAL: Modifier Order

`imeConnection` MUST come BEFORE `focusable()` in the modifier chain:

```kotlin
// CORRECT - imeConnection observes the focusable below it
Modifier
    .focusRequester(focusRequester)
    .onFocusChanged { ... }
    .imeConnection(state = imeState)  // BEFORE focusable
    .focusable()

// WRONG - imeConnection has no focus target to observe
Modifier
    .focusRequester(focusRequester)
    .onFocusChanged { ... }
    .focusable()
    .imeConnection(state = imeState)  // AFTER focusable - won't receive focus events!
```

`FocusEventModifierNode` observes focus changes on focusables **below** it in the modifier chain. If `imeConnection` is after `focusable()`, there's no focus target below it to observe, and the keyboard won't appear.
```

### InputConnection Methods You Must Implement

| Method | Purpose |
|--------|---------|
| `commitText()` | Insert finalized text |
| `setComposingText()` | Handle autocomplete/composition |
| `deleteSurroundingText()` | Handle backspace/delete |
| `getTextBeforeCursor()` | IME needs context for predictions |
| `getTextAfterCursor()` | IME needs context for predictions |
| `sendKeyEvent()` | Hardware keyboard support |
| `performEditorAction()` | Handle IME action button |

### Full Implementation

See `ui/currentnote/ImeConnection.kt` for the complete implementation used in this codebase.

## References

### AndroidX Source Code (Mining for Parts)

We extracted our IME implementation by studying BasicTextField's internals. These raw GitHub links are useful for mining additional functionality:

**BasicTextField and Text Input:**
- [BasicTextField.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/BasicTextField.kt) - The main composable (study structure, don't use directly)
- [TextFieldDecoratorModifier.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/input/internal/TextFieldDecoratorModifier.kt) - How IME session is managed
- [AndroidTextInputSession.android.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/input/internal/AndroidTextInputSession.android.kt) - Android-specific IME session

**Platform Text Input (Low-Level IME):**
- [PlatformTextInputModifierNode.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/PlatformTextInputModifierNode.kt) - The interface we implement
- [PlatformTextInputMethodRequest.android.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/PlatformTextInputMethodRequest.android.kt) - Android request interface
- [AndroidPlatformTextInputSession.android.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidPlatformTextInputSession.android.kt) - How startInputMethod works

**InputConnection (Android IME Protocol):**
- [StatelessInputConnection.android.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/input/internal/StatelessInputConnection.android.kt) - Full InputConnection implementation to study

**Browse the directories:**
- [compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/](https://github.com/androidx/androidx/tree/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text) - Common text components
- [compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/input/internal/](https://github.com/androidx/androidx/tree/androidx-main/compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/input/internal) - Android-specific internals

### Documentation

- [TextInputService - Android Developers](https://developer.android.com/reference/kotlin/androidx/compose/ui/text/input/TextInputService)
- [InputConnection - Android Developers](https://developer.android.com/reference/android/view/inputmethod/InputConnection)
- [PlatformTextInputModifierNode - Composables](https://composables.com/docs/androidx.compose.ui/ui/interfaces/PlatformTextInputModifierNode)
- [Effective state management for TextField - Android Developers Medium](https://medium.com/androiddevelopers/effective-state-management-for-textfield-in-compose-d6e5b070fbe5)
- [BasicTextField2: A TextField of Dreams - ProAndroidDev](https://proandroiddev.com/basictextfield2-a-textfield-of-dreams-1-2-0103fd7cc0ec)

### Bug Reports (Context for Why This Is Hard)

- [Google Issue #36983666 - LatinIME fails to delete pre-existing characters](https://issuetracker.google.com/issues/36983666)
- [Google Issue #165956313 - TextField doesn't allow backspace removal](https://issuetracker.google.com/issues/165956313)
