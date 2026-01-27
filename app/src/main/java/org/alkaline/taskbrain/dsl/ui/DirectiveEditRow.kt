package org.alkaline.taskbrain.dsl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Layout constants
private val RowStartPadding = 4.dp  // Almost to the gutter
private val RowEndPadding = 8.dp
private val RowVerticalPadding = 2.dp
private val TextFieldStartPadding = 4.dp  // Small gap after indicator
private val TextFieldEndPadding = 8.dp
private val ButtonSize = 32.dp
private val IconSize = 20.dp
private val IndicatorSize = 16.dp
private val IndicatorEndPadding = 0.dp  // No extra padding on indicator
private const val FontSizeScale = 0.9f


/**
 * An editable row for editing a directive's source text.
 * Appears below the main line when a directive is expanded.
 *
 * Uses the same BasicText + custom IME pattern as the main editor,
 * which properly handles wrapped text and tap-to-position cursor.
 *
 * @param initialText The initial directive source text (e.g., "[42]")
 * @param textStyle The text style to use
 * @param errorMessage Optional error message to display (from failed execution)
 * @param warningMessage Optional warning message to display (from no-effect execution)
 * @param onRefresh Called when user taps refresh to recompute without closing
 * @param onConfirm Called when user confirms the edit (check button or collapse)
 * @param onCancel Called when user cancels the edit (X button)
 * @param onHeightMeasured Called with the measured height in pixels after layout
 *
 * Milestone 8: Added warningMessage parameter.
 */
@Composable
fun DirectiveEditRow(
    initialText: String,
    textStyle: TextStyle,
    errorMessage: String? = null,
    warningMessage: String? = null,
    onRefresh: ((currentText: String) -> Unit)? = null,
    onConfirm: (newText: String) -> Unit,
    onCancel: () -> Unit,
    onHeightMeasured: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(initialText) }
    var cursorPosition by remember { mutableIntStateOf(initialText.length) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus and show keyboard when shown
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DirectiveColors.EditRowBackground)
            .onGloballyPositioned { coordinates ->
                onHeightMeasured?.invoke(coordinates.size.height)
            }
    ) {
        // Edit row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = RowStartPadding, end = RowEndPadding, top = RowVerticalPadding, bottom = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Edit indicator
            DirectiveEditIndicator()

            // Text input using custom IME component (same pattern as main editor)
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

        // Error message row (if present) - below the edit row, aligned with text field
        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DirectiveColors.ErrorBackground)
                    .padding(start = RowStartPadding + IndicatorSize + TextFieldStartPadding, end = RowEndPadding, top = 4.dp, bottom = 4.dp),
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

        // Warning message row (if present) - below the edit row, aligned with text field
        // Milestone 8: Added for no-effect warnings.
        if (warningMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DirectiveColors.WarningBackground)
                    .padding(start = RowStartPadding + IndicatorSize + TextFieldStartPadding, end = RowEndPadding, top = 4.dp, bottom = 4.dp),
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

/**
 * Visual indicator showing this is an edit row (continuation from line above).
 */
@Composable
private fun DirectiveEditIndicator() {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(IndicatorSize)
            .padding(end = IndicatorEndPadding)
    ) {
        val strokeWidth = 1.5.dp.toPx()
        val horizontalPosition = size.width * 0.3f
        val verticalMidpoint = size.height * 0.5f

        // Vertical line from top
        drawLine(
            color = DirectiveColors.EditIndicator,
            start = Offset(horizontalPosition, 0f),
            end = Offset(horizontalPosition, verticalMidpoint),
            strokeWidth = strokeWidth
        )

        // Horizontal line to the right
        drawLine(
            color = DirectiveColors.EditIndicator,
            start = Offset(horizontalPosition, verticalMidpoint),
            end = Offset(size.width, verticalMidpoint),
            strokeWidth = strokeWidth
        )
    }
}
