package org.alkaline.taskbrain.dsl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Layout constants
private val RowStartPadding = 24.dp
private val RowEndPadding = 8.dp
private val RowVerticalPadding = 2.dp
private val TextFieldHorizontalPadding = 8.dp
private val ButtonSize = 32.dp
private val IconSize = 20.dp
private val IndicatorSize = 16.dp
private val IndicatorEndPadding = 4.dp
private const val FontSizeScale = 0.9f


/**
 * An editable row for editing a directive's source text.
 * Appears below the main line when a directive is expanded.
 *
 * @param initialText The initial directive source text (e.g., "[42]")
 * @param textStyle The text style to use
 * @param errorMessage Optional error message to display (from failed execution)
 * @param onRefresh Called when user taps refresh to recompute without closing
 * @param onConfirm Called when user confirms the edit (check button or collapse)
 * @param onCancel Called when user cancels the edit (X button)
 * @param onHeightMeasured Called with the measured height in pixels after layout
 */
@Composable
fun DirectiveEditRow(
    initialText: String,
    textStyle: TextStyle,
    errorMessage: String? = null,
    onRefresh: ((currentText: String) -> Unit)? = null,
    onConfirm: (newText: String) -> Unit,
    onCancel: () -> Unit,
    onHeightMeasured: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }

    val focusRequester = remember { FocusRequester() }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
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

        // Text field area with pointer handling - wrapped to not affect buttons
        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    handleTextFieldPointerInput(
                        paddingPx = TextFieldHorizontalPadding.toPx(),
                        getTextLayout = { textLayoutResult },
                        onTap = { cursorOffset ->
                            focusRequester.requestFocus()
                            textFieldValue = textFieldValue.copy(selection = TextRange(cursorOffset))
                        }
                    )
                }
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                textStyle = textStyle.copy(
                    fontSize = (textStyle.fontSize.value * FontSizeScale).sp
                ),
                onTextLayout = { layout -> textLayoutResult = layout },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(horizontal = TextFieldHorizontalPadding)
            )
        }

        // Refresh button (recompute without closing)
        if (onRefresh != null) {
            IconButton(
                onClick = { onRefresh(textFieldValue.text) },
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
            onClick = { onConfirm(textFieldValue.text) },
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

        // Error message row (if present) - below the edit row
        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DirectiveColors.ErrorBackground)
                    .padding(start = RowStartPadding + IndicatorSize, end = RowEndPadding, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = errorMessage,
                    color = DirectiveColors.ErrorText,
                    fontSize = (textStyle.fontSize.value * FontSizeScale * 0.9f).sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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

/**
 * Handles pointer input for the text field, consuming events to prevent parent handlers
 * from processing and positioning the cursor on tap.
 *
 * @param paddingPx Horizontal padding of the text field (to offset tap position)
 * @param getTextLayout Provides the current TextLayoutResult for cursor positioning
 * @param onTap Called with the cursor offset when a tap is detected
 */
private suspend fun PointerInputScope.handleTextFieldPointerInput(
    paddingPx: Float,
    getTextLayout: () -> TextLayoutResult?,
    onTap: (cursorOffset: Int) -> Unit
) {
    awaitPointerEventScope {
        while (true) {
            // Consume in Initial pass - parent checks consumption in Main pass
            val down = awaitPointerEvent(PointerEventPass.Initial)
            val downPos = down.changes.firstOrNull()?.position ?: continue
            down.changes.forEach { it.consume() }

            // Track pointer until release
            var lastPos = downPos
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                change.consume()

                if (!change.pressed) {
                    // Pointer released - calculate cursor position
                    val layout = getTextLayout()
                    if (layout != null) {
                        val localX = lastPos.x - paddingPx
                        val cursorOffset = if (localX >= 0) {
                            val localY = layout.size.height / 2f
                            layout.getOffsetForPosition(Offset(localX, localY))
                        } else {
                            0 // Tap was before text area
                        }
                        onTap(cursorOffset)
                    }
                    break
                }
                lastPos = change.position
            }
        }
    }
}
