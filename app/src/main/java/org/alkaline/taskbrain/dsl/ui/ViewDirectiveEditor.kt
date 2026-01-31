package org.alkaline.taskbrain.dsl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

// Layout constants
private val RowStartPadding = 8.dp
private val RowEndPadding = 8.dp
private val RowVerticalPadding = 8.dp
private val TextFieldStartPadding = 4.dp
private val TextFieldEndPadding = 8.dp
private val ButtonSize = 32.dp
private val IconSize = 20.dp
private const val FontSizeScale = 0.9f
private val BackdropAlpha = 0.5f

/**
 * An overlay editor for editing a view directive's source text.
 * Appears as an overlay at the top of the view content with a semi-transparent backdrop.
 *
 * Features:
 * - Semi-transparent backdrop that dismisses on tap
 * - Same controls as DirectiveEditRow (text input, refresh, confirm, cancel)
 * - Slides in from top with fade animation
 *
 * @param visible Whether the overlay is visible
 * @param initialText The initial directive source text (e.g., "[view find(path: \"inbox\")]")
 * @param textStyle The text style to use
 * @param errorMessage Optional error message to display (from failed execution)
 * @param warningMessage Optional warning message to display (from no-effect execution)
 * @param onRefresh Called when user taps refresh to recompute without closing
 * @param onConfirm Called when user confirms the edit (check button)
 * @param onCancel Called when user cancels the edit (X button or backdrop tap)
 */
@Composable
fun ViewDirectiveEditorOverlay(
    visible: Boolean,
    initialText: String,
    textStyle: TextStyle,
    errorMessage: String? = null,
    warningMessage: String? = null,
    onRefresh: ((currentText: String) -> Unit)? = null,
    onConfirm: (newText: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var cursorPosition by remember(initialText) { mutableIntStateOf(initialText.length) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus and show keyboard when visible
    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Semi-transparent backdrop - tapping dismisses
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = BackdropAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancel
                    )
            )

            // Editor panel at top
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(DirectiveColors.EditRowBackground)
                    .zIndex(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Consume clicks to prevent backdrop dismiss */ }
                    )
            ) {
                // Edit row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = RowStartPadding,
                            end = RowEndPadding,
                            top = RowVerticalPadding,
                            bottom = RowVerticalPadding
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Text input using custom IME component
                    DirectiveTextInput(
                        text = text,
                        cursorPosition = cursorPosition,
                        textStyle = textStyle.copy(
                            fontSize = (textStyle.fontSize.value * FontSizeScale).sp
                        ),
                        focusRequester = focusRequester,
                        onTextChange = { newText, newCursor ->
                            text = newText
                            cursorPosition = newCursor
                        },
                        onFocusChanged = { /* Focus managed by focusRequester */ },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = TextFieldStartPadding, end = TextFieldEndPadding)
                    )

                    // Refresh button (recompute without closing)
                    if (onRefresh != null) {
                        IconButton(
                            onClick = { onRefresh(text) },
                            modifier = Modifier.size(ButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = DirectiveColors.RefreshButton,
                                modifier = Modifier.size(IconSize)
                            )
                        }
                    }

                    // Confirm button
                    IconButton(
                        onClick = { onConfirm(text) },
                        modifier = Modifier.size(ButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm",
                            tint = DirectiveColors.SuccessBorder,
                            modifier = Modifier.size(IconSize)
                        )
                    }

                    // Cancel button
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(ButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = DirectiveColors.CancelButton,
                            modifier = Modifier.size(IconSize)
                        )
                    }
                }

                // Error message row (if present)
                if (errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DirectiveColors.ErrorBackground)
                            .padding(
                                start = RowStartPadding + TextFieldStartPadding,
                                end = RowEndPadding,
                                top = 4.dp,
                                bottom = 4.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = errorMessage,
                                color = DirectiveColors.ErrorText,
                                fontSize = (textStyle.fontSize.value * FontSizeScale * 0.9f).sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Warning message row (if present)
                if (warningMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DirectiveColors.WarningBackground)
                            .padding(
                                start = RowStartPadding + TextFieldStartPadding,
                                end = RowEndPadding,
                                top = 4.dp,
                                bottom = 4.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = warningMessage,
                                color = DirectiveColors.WarningText,
                                fontSize = (textStyle.fontSize.value * FontSizeScale * 0.9f).sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
