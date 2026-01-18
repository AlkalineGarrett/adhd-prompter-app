package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

/**
 * Main text editing component for notes.
 * Includes line gutter, selection handling, and context menu.
 */
@Composable
fun NoteTextField(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    isFingerDownFlow: StateFlow<Boolean>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scrollState = rememberScrollState()

    // Track drag selection state for gutter
    var dragStartLine by remember { mutableIntStateOf(-1) }
    var dragEndLine by remember { mutableIntStateOf(-1) }

    // Track last indent time for double-space to unindent
    var lastIndentTime by remember { mutableStateOf(0L) }

    // Selection menu controller handles all selection tracking and menu state
    val menuController = rememberSelectionMenuController()

    // Sync cursor position when it moves (collapsed → collapsed)
    LaunchedEffect(textFieldValue.selection) {
        menuController.syncCursorPosition(textFieldValue.selection)
    }

    // Handle selection changes
    LaunchedEffect(textFieldValue.selection, textLayoutResult) {
        val currentSelection = textFieldValue.selection

        when (val result = menuController.analyzeSelectionChange(currentSelection)) {
            is SelectionMenuController.SelectionChangeResult.WaitForGesture -> {
                val shouldShowMenu = menuController.waitForGestureComplete(
                    isFingerDownFlow
                ) { textFieldValue.selection }

                if (shouldShowMenu) {
                    menuController.calculateMenuPosition(textLayoutResult, textFieldValue.selection.min)
                    menuController.showMenu()
                }
                menuController.updatePreviousSelection(currentSelection)
            }
            is SelectionMenuController.SelectionChangeResult.RestoreSelection -> {
                menuController.calculateMenuPosition(textLayoutResult, result.selectionToRestore.min)
                onTextFieldValueChange(textFieldValue.copy(selection = result.selectionToRestore))
                menuController.showMenu()
                // Don't update previousSelection - we're restoring it
            }
            is SelectionMenuController.SelectionChangeResult.Skip -> {
                // Already handled in analyzeSelectionChange
            }
            is SelectionMenuController.SelectionChangeResult.PassThrough -> {
                menuController.updatePreviousSelection(currentSelection)
            }
        }
    }

    Row(modifier = modifier.fillMaxWidth()) {
        LineGutter(
            textLayoutResult = textLayoutResult,
            scrollState = scrollState,
            onLineSelected = { lineIndex ->
                val selection = getLineSelection(textFieldValue.text, lineIndex)
                onTextFieldValueChange(textFieldValue.copy(selection = selection))
            },
            onDragStart = { lineIndex ->
                dragStartLine = lineIndex
                dragEndLine = lineIndex
                val selection = getLineSelection(textFieldValue.text, lineIndex)
                onTextFieldValueChange(textFieldValue.copy(selection = selection))
            },
            onDragUpdate = { lineIndex ->
                if (dragStartLine >= 0 && lineIndex != dragEndLine) {
                    dragEndLine = lineIndex
                    val startLine = minOf(dragStartLine, dragEndLine)
                    val endLine = maxOf(dragStartLine, dragEndLine)
                    val selection = getMultiLineSelection(textFieldValue.text, startLine, endLine)
                    onTextFieldValueChange(textFieldValue.copy(selection = selection))
                }
            },
            onDragEnd = {
                dragStartLine = -1
                dragEndLine = -1
            },
            modifier = Modifier.fillMaxHeight()
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            CompositionLocalProvider(
                LocalTextToolbar provides EmptyTextToolbar
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            val result = TextFieldInputHandler.processValueChange(
                                context = context,
                                oldValue = textFieldValue,
                                newValue = newValue,
                                lastIndentTime = lastIndentTime,
                                onIndentTimeUpdate = { lastIndentTime = it }
                            )
                            onTextFieldValueChange(result.value)
                        },
                        onTextLayout = { result ->
                            textLayoutResult = result
                        },
                        textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
                        cursorBrush = SolidColor(Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                onFocusChanged(focusState.isFocused)
                            }
                    )

                    SelectionContextMenu(
                        expanded = menuController.showContextMenu,
                        onDismissRequest = { menuController.hideMenu() },
                        menuOffset = menuController.contextMenuOffset,
                        actions = SelectionMenuActions(
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(SelectionActions.copy(textFieldValue)))
                                menuController.hideMenu()
                            },
                            onCut = {
                                menuController.markSkipNextRestore()
                                val result = SelectionActions.cut(textFieldValue)
                                result.copiedText?.let { clipboardManager.setText(AnnotatedString(it)) }
                                onTextFieldValueChange(result.newValue)
                                menuController.hideMenu()
                            },
                            onSelectAll = {
                                onTextFieldValueChange(SelectionActions.selectAll(textFieldValue))
                                menuController.hideMenu()
                            },
                            onUnselect = {
                                menuController.markSkipNextRestore()
                                onTextFieldValueChange(SelectionActions.unselect(textFieldValue))
                                menuController.hideMenu()
                            },
                            onDelete = {
                                menuController.markSkipNextRestore()
                                onTextFieldValueChange(SelectionActions.delete(textFieldValue))
                                menuController.hideMenu()
                            }
                        )
                    )
                }
            }
        }
    }
}

/**
 * Empty text toolbar that disables the system text selection toolbar.
 */
private object EmptyTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() {}
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {}
}
