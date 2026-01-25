package org.alkaline.taskbrain.dsl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
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

// Colors
private val EditRowBackground = Color(0xFFF5F5F5)
private val IndicatorColor = Color(0xFF9E9E9E)
private val ConfirmColor = Color(0xFF4CAF50)
private val CancelColor = Color(0xFF757575)

/**
 * An editable row for editing a directive's source text.
 * Appears below the main line when a directive is expanded.
 *
 * @param initialText The initial directive source text (e.g., "[42]")
 * @param textStyle The text style to use
 * @param onConfirm Called when user confirms the edit (check button or collapse)
 * @param onCancel Called when user cancels the edit (X button)
 */
@Composable
fun DirectiveEditRow(
    initialText: String,
    textStyle: TextStyle,
    onConfirm: (newText: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }

    val focusRequester = remember { FocusRequester() }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Auto-focus when shown
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(EditRowBackground)
            .padding(start = RowStartPadding, end = RowEndPadding, top = RowVerticalPadding, bottom = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Edit indicator
        DirectiveEditIndicator()

        // Text field area with pointer handling - wrapped to not affect buttons
        Box(
            modifier = Modifier
                .weight(1f)
                // Consume pointer events in Initial pass to prevent parent gesture handler from processing
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            // Consume in Initial pass - parent checks consumption in Main pass
                            val down = awaitPointerEvent(PointerEventPass.Initial)
                            val downPos = down.changes.firstOrNull()?.position ?: continue
                            down.changes.forEach { it.consume() }

                            // Track until release
                            var lastPos = downPos
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: break
                                change.consume()

                                if (!change.pressed) {
                                    // Pointer released - handle tap
                                    val textFieldPaddingPx = TextFieldHorizontalPadding.toPx()
                                    focusRequester.requestFocus()
                                    textLayoutResult?.let { layout ->
                                        // Subtract TextField internal padding (pointer coords are relative to the Box)
                                        val localX = lastPos.x - textFieldPaddingPx
                                        if (localX >= 0) {
                                            val localY = layout.size.height / 2f
                                            val cursorOffset = layout.getOffsetForPosition(Offset(localX, localY))
                                            textFieldValue = textFieldValue.copy(
                                                selection = TextRange(cursorOffset)
                                            )
                                        } else {
                                            // Tap was before text area - position at start
                                            textFieldValue = textFieldValue.copy(
                                                selection = TextRange(0)
                                            )
                                        }
                                    }
                                    break
                                }
                                lastPos = change.position
                            }
                        }
                    }
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

        // Confirm button
        IconButton(
            onClick = { onConfirm(textFieldValue.text) },
            modifier = Modifier.size(ButtonSize)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Confirm",
                tint = ConfirmColor,
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
                tint = CancelColor,
                modifier = Modifier.size(IconSize)
            )
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
            color = IndicatorColor,
            start = Offset(horizontalPosition, 0f),
            end = Offset(horizontalPosition, verticalMidpoint),
            strokeWidth = strokeWidth
        )

        // Horizontal line to the right
        drawLine(
            color = IndicatorColor,
            start = Offset(horizontalPosition, verticalMidpoint),
            end = Offset(size.width, verticalMidpoint),
            strokeWidth = strokeWidth
        )
    }
}
