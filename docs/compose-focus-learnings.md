# Jetpack Compose Focus System: Key Learnings

## Overview

This document captures learnings from implementing custom text input with focus handling in Jetpack Compose, specifically for the HangingIndentEditor component.

## Modifier Order is Critical

The order of focus-related modifiers matters significantly:

```kotlin
// CORRECT order (matches CoreTextField)
Modifier
    .focusRequester(focusRequester)  // 1. Attach requester first
    .onFocusChanged { focusState ->  // 2. Listen for changes
        // Handle focus state
    }
    .focusable(interactionSource)    // 3. Create focus target last

// INCORRECT - focusRequester won't find the focus target
Modifier
    .focusable()
    .focusRequester(focusRequester)  // Too late - nothing below to attach to
```

**Key insight**: `focusRequester` associates with the **first focusable component below it** in the modifier chain.

## Focus Modifiers Explained

### `focusRequester(FocusRequester)`
- Associates a `FocusRequester` with a focusable component
- Allows programmatic focus requests via `focusRequester.requestFocus()`
- Must come BEFORE the focus target in the modifier chain

### `focusable(interactionSource)`
- Creates a focus target that can receive focus
- Internally uses `FocusTargetModifierNode`
- The `interactionSource` parameter enables proper focus tracking

### `onFocusChanged { FocusState -> }`
- Callback for focus state changes
- `FocusState` has properties: `isFocused`, `hasFocus`, `isCaptured`

### `focusGroup()`
- Groups child focusables together for navigation purposes
- Makes the group appear as a single entity during focus traversal
- Useful for complex layouts with multiple focusable items
- Import: `androidx.compose.foundation.focusGroup`

### `focusProperties { }`
- Customize focus behavior (e.g., `canFocus`, `next`, `previous`)
- Can override default focus traversal

## Connecting to Soft Keyboard

### Legacy Approach: TextInputService
```kotlin
val textInputService = LocalTextInputService.current

// When focused:
val session = textInputService?.startInput(
    value = TextFieldValue(text, selection),
    imeOptions = ImeOptions(...),
    onEditCommand = { commands -> /* handle edits */ },
    onImeActionPerformed = { action -> /* handle IME actions */ }
)

// When unfocused:
session?.dispose()
```

**Note**: `TextInputService` is now deprecated in favor of `PlatformTextInputModifierNode`.

### Modern Approach: PlatformTextInputModifierNode
```kotlin
class CustomTextInputNode : Modifier.Node(),
    FocusEventModifierNode,
    PlatformTextInputModifierNode {

    private var focusedJob: Job? = null

    override fun onFocusEvent(focusState: FocusState) {
        focusedJob?.cancel()
        focusedJob = if (focusState.isFocused) {
            coroutineScope.launch {
                establishTextInputSession {
                    launch {
                        startInputMethod(request)
                    }
                }
            }
        } else null
    }
}
```

## Focus + Text Input Session Lifecycle

CoreTextField follows this pattern:

1. **Focus Gained** → Start text input session (shows keyboard)
2. **Focus Lost** → End text input session (hides keyboard)
3. **IME Options Changed While Focused** → Restart input session

```kotlin
if (state.hasFocus && enabled && !readOnly) {
    startInputSession(...)
} else {
    endInputSession(state)
}
```

## Common Issues

### `requestFocus()` Does Nothing
- FocusRequester might not be attached to a focus target
- Check modifier order (focusRequester must come before focusable)
- `requestFocus()` silently fails if not attached (no exception)

### Focus Not Working in Scrollable Containers
- Add `focusGroup()` to the scrollable container
- Ensure focusable items are properly composed before focus request
- Consider using `LaunchedEffect` to defer focus requests until after composition

### Multiple Focusable Items
- Each needs its own `FocusRequester`
- Use `remember(key) { List(count) { FocusRequester() } }` for dynamic lists
- Be careful with key changes that recreate FocusRequesters

## CoreTextField Internal Structure

CoreTextField uses these modifiers in order:

1. `focusRequester(focusRequester)`
2. `onFocusChanged { }` - triggers input session start/stop
3. `focusable(interactionSource)`
4. `textFieldFocusModifier()` - custom focus handling
5. `textFieldKeyInput()` - hardware key events
6. `textFieldPointer()` - touch/gesture handling
7. `onPreviewKeyEvent { }` - intercepts certain keys

## Best Practices

1. **Always use `interactionSource` with `focusable()`** for proper focus tracking

2. **Use `focusGroup()` for complex layouts** with multiple focusable children

3. **Defer focus requests** to after composition:
   ```kotlin
   LaunchedEffect(key) {
       focusRequester.requestFocus()
   }
   ```

4. **Don't mix gesture handlers carelessly** - parent `pointerInput` can interfere with child focus handling

5. **For custom text input**, consider using `PlatformTextInputModifierNode` (modern) over `TextInputService` (deprecated)

## Modern BasicTextField Focus Handling

When using modern `BasicTextField` with `TextFieldState`, focus handling is much simpler because BasicTextField handles IME connection internally.

### Simplified Pattern

```kotlin
@Composable
fun MyTextInput(
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit
) {
    val textFieldState = rememberTextFieldState("")

    BasicTextField(
        state = textFieldState,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocusChanged(focusState.isFocused)
            }
    )
}
```

Note: You don't need `.focusable()` when using `BasicTextField` - it's already focusable internally.

### Focus in Multi-Line Editors

When building a multi-line editor with separate focusable lines:

```kotlin
@Composable
fun MultiLineEditor(lines: List<String>) {
    val focusRequesters = remember(lines.size) {
        List(lines.size) { FocusRequester() }
    }
    var focusedLineIndex by remember { mutableIntStateOf(0) }
    var cursorVersion by remember { mutableIntStateOf(0) }

    // Request focus when line changes (e.g., from tap)
    LaunchedEffect(focusedLineIndex, cursorVersion) {
        if (focusedLineIndex in focusRequesters.indices) {
            focusRequesters[focusedLineIndex].requestFocus()
        }
    }

    Column(modifier = Modifier.focusGroup()) {
        lines.forEachIndexed { index, line ->
            LineView(
                text = line,
                focusRequester = focusRequesters[index],
                onFocusChanged = { isFocused ->
                    if (isFocused) focusedLineIndex = index
                }
            )
        }
    }
}
```

Key points:
1. **Use `focusGroup()`** on the parent Column to group child focusables
2. **Track `cursorVersion`** - increment it to force `LaunchedEffect` to re-run and request focus
3. **Create focusRequesters based on line count** - recreate when count changes

### Focus + External Focus Requester

When a parent component wants to request focus on your editor:

```kotlin
@Composable
fun EditorWithExternalFocus(
    externalFocusRequester: FocusRequester?
) {
    val internalFocusRequesters = remember { List(lineCount) { FocusRequester() } }
    var focusedLineIndex by remember { mutableIntStateOf(0) }

    // Forward external focus to the currently focused line
    LaunchedEffect(externalFocusRequester, focusedLineIndex) {
        if (focusedLineIndex in internalFocusRequesters.indices) {
            internalFocusRequesters[focusedLineIndex].requestFocus()
        }
    }

    // ... render lines with their focus requesters
}
```

## References

- [Change focus behavior - Android Developers](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior)
- [focusGroup - Composables](https://composables.com/foundation/focusgroup)
- [Focus in Jetpack Compose - Jamie Sanson (GDE)](https://medium.com/google-developer-experts/focus-in-jetpack-compose-6584252257fe)
- [How to handle focus in Compose - Composables](https://composables.com/jetpack-compose-tutorials/focus-text)
- [CoreTextField.kt - JetBrains Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/CoreTextField.kt)
