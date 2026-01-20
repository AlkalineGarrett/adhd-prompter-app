package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * A text input component that uses our custom ImeConnection for keyboard input.
 *
 * IMPORTANT: This does NOT use BasicTextField.
 *
 * WHY:
 * BasicTextField comes with built-in gesture handling, selection UI (magnifier, handles),
 * and context menus that CANNOT be fully disabled. Even with NoOpTextToolbar and
 * transparent selection colors, the magnifier still appears during selection.
 *
 * This implementation uses:
 * - BasicText for text rendering (display only, no IME)
 * - ImeConnection for keyboard input (our custom PlatformTextInputModifierNode)
 * - Manual cursor rendering with blinking animation
 *
 * DO NOT REPLACE THIS WITH BasicTextField.
 * If you're tempted to use BasicTextField, read docs/compose-ime-learnings.md first.
 */
@Composable
internal fun ManualTextInput(
    text: String,
    cursorPosition: Int,
    isFocused: Boolean,
    hasExternalSelection: Boolean,
    textStyle: TextStyle,
    focusRequester: FocusRequester,
    onTextChange: (String, Int) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onTextLayoutResult: (TextLayoutResult) -> Unit,
    onBackspaceAtStart: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Create ImeState that syncs with our text/cursor
    val imeState = remember { ImeState(text, cursorPosition) }

    // Set up callback for IME edits - update reference on every recomposition
    imeState.setOnTextChangeListener(onTextChange)
    DisposableEffect(Unit) {
        onDispose {
            imeState.setOnTextChangeListener { _, _ -> }
        }
    }

    // Set up callback for backspace at start (empty content)
    // Use Unit as key to set this once and update via reference
    DisposableEffect(Unit) {
        onDispose {
            imeState.setOnBackspaceAtStartListener {}
        }
    }
    // Update the listener reference whenever it changes
    imeState.setOnBackspaceAtStartListener(onBackspaceAtStart ?: {})

    // Sync external text changes to ImeState
    LaunchedEffect(text, cursorPosition) {
        if (text != imeState.text || cursorPosition != imeState.cursorPosition) {
            imeState.updateFromExternal(text, cursorPosition)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    // Track text layout for cursor rendering
    var textLayoutResultState: TextLayoutResult? by remember { mutableStateOf(null) }

    // Cursor blink animation
    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocusChanged(focusState.isFocused)
            }
            // imeConnection MUST be BEFORE focusable() so FocusEventModifierNode
            // can observe the focus target created by focusable()
            .imeConnection(
                state = imeState,
                imeOptions = ImeOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.None,
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
            .focusable(interactionSource = interactionSource)
    ) {
        // Render text using BasicText (display only, no IME integration)
        BasicText(
            text = text,
            style = textStyle,
            onTextLayout = { layoutResult ->
                textLayoutResultState = layoutResult
                onTextLayoutResult(layoutResult)
            },
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()

                    // Draw blinking cursor when focused and no external selection
                    // When there's a selection, the selection highlight replaces the cursor
                    if (isFocused && !hasExternalSelection && textLayoutResultState != null) {
                        val layout = textLayoutResultState!!
                        val cursorPos = cursorPosition.coerceIn(0, text.length)

                        // Get cursor position from layout
                        val cursorRect = try {
                            layout.getCursorRect(cursorPos)
                        } catch (e: Exception) {
                            // Fallback if position is invalid
                            Rect(0f, 0f, 2.dp.toPx(), layout.size.height.toFloat())
                        }

                        // Draw cursor line with blink animation
                        drawLine(
                            color = Color.Black.copy(alpha = cursorAlpha),
                            start = Offset(cursorRect.left, cursorRect.top),
                            end = Offset(cursorRect.left, cursorRect.bottom),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
        )
    }
}
