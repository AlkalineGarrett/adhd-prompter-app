package org.alkaline.taskbrain.ui.currentnote

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.geometry.Rect
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.ui.Dimens
import org.alkaline.taskbrain.ui.components.ActionButtonBar
import org.alkaline.taskbrain.ui.components.ErrorDialog

@Composable
fun CurrentNoteScreen(
    noteId: String? = null,
    isFingerDownFlow: StateFlow<Boolean>? = null,
    currentNoteViewModel: CurrentNoteViewModel = viewModel()
) {
    val saveStatus by currentNoteViewModel.saveStatus.observeAsState()
    val loadStatus by currentNoteViewModel.loadStatus.observeAsState()
    val contentModified by currentNoteViewModel.contentModified.observeAsState(false)
    val isAgentProcessing by currentNoteViewModel.isAgentProcessing.observeAsState(false)

    var userContent by remember { mutableStateOf("") }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isSaved by remember { mutableStateOf(true) }
    var agentCommand by remember { mutableStateOf("") }
    var isAgentSectionExpanded by remember { mutableStateOf(false) }

    val mainContentFocusRequester = remember { FocusRequester() }
    var isMainContentFocused by remember { mutableStateOf(false) }

    // Handle initial data loading
    LaunchedEffect(noteId) {
        currentNoteViewModel.loadContent(noteId)
    }

    // Update content when loaded from VM
    LaunchedEffect(loadStatus) {
        if (loadStatus is LoadStatus.Success) {
            val loadedContent = (loadStatus as LoadStatus.Success).content
            userContent = loadedContent
            textFieldValue = TextFieldValue(loadedContent, TextRange(loadedContent.length))
        }
    }

    // React to content modification signal (e.g. from Agent)
    LaunchedEffect(contentModified) {
        if (contentModified) {
            isSaved = false
        }
    }

    // Handle save status changes
    LaunchedEffect(saveStatus) {
        if (saveStatus is SaveStatus.Success) {
            isSaved = true
            currentNoteViewModel.markAsSaved()
        }
    }

    // Show error dialogs
    if (saveStatus is SaveStatus.Error) {
        ErrorDialog(
            title = "Save Error",
            throwable = (saveStatus as SaveStatus.Error).throwable,
            onDismiss = { currentNoteViewModel.clearSaveError() }
        )
    }

    if (loadStatus is LoadStatus.Error) {
        ErrorDialog(
            title = "Load Error",
            throwable = (loadStatus as LoadStatus.Error).throwable,
            onDismiss = { currentNoteViewModel.clearLoadError() }
        )
    }

    // Monitor clipboard and add HTML formatting for bullets/checkboxes
    ClipboardHtmlConverter()

    Column(modifier = Modifier.fillMaxSize()) {
        StatusBar(
            isSaved = isSaved,
            onSaveClick = { currentNoteViewModel.saveContent(userContent) }
        )

        MainContentTextField(
            textFieldValue = textFieldValue,
            onTextFieldValueChange = { newValue ->
                textFieldValue = newValue
                if (newValue.text != userContent) {
                    userContent = newValue.text
                    if (isSaved) isSaved = false
                }
            },
            focusRequester = mainContentFocusRequester,
            onFocusChanged = { isFocused -> isMainContentFocused = isFocused },
            isFingerDownFlow = isFingerDownFlow,
            modifier = Modifier.weight(1f)
        )

        CommandBar(
            onToggleBullet = {
                val newValue = toggleBulletOnCurrentLine(textFieldValue)
                textFieldValue = newValue
                if (newValue.text != userContent) {
                    userContent = newValue.text
                    if (isSaved) isSaved = false
                }
            },
            onToggleCheckbox = {
                val newValue = toggleCheckboxOnCurrentLine(textFieldValue)
                textFieldValue = newValue
                if (newValue.text != userContent) {
                    userContent = newValue.text
                    if (isSaved) isSaved = false
                }
            },
            onIndent = {
                val newValue = handleSelectionIndent(textFieldValue)
                textFieldValue = newValue
                if (newValue.text != userContent) {
                    userContent = newValue.text
                    if (isSaved) isSaved = false
                }
            },
            onUnindent = {
                val newValue = handleSelectionUnindent(textFieldValue)
                if (newValue != null) {
                    textFieldValue = newValue
                    if (newValue.text != userContent) {
                        userContent = newValue.text
                        if (isSaved) isSaved = false
                    }
                }
            },
            onPaste = { clipText ->
                val cursorPos = textFieldValue.selection.start
                val newText = textFieldValue.text.substring(0, cursorPos) +
                        clipText +
                        textFieldValue.text.substring(cursorPos)
                val newCursor = cursorPos + clipText.length
                textFieldValue = TextFieldValue(newText, TextRange(newCursor))
                if (newText != userContent) {
                    userContent = newText
                    if (isSaved) isSaved = false
                }
            },
            isPasteEnabled = isMainContentFocused && textFieldValue.selection.collapsed
        )

        AgentCommandSection(
            isExpanded = isAgentSectionExpanded,
            onExpandedChange = { isAgentSectionExpanded = it },
            agentCommand = agentCommand,
            onAgentCommandChange = { agentCommand = it },
            isProcessing = isAgentProcessing,
            onSendCommand = {
                currentNoteViewModel.processAgentCommand(userContent, agentCommand)
                agentCommand = ""
            },
            mainContentFocusRequester = mainContentFocusRequester
        )
    }
}

private val GutterWidth = 21.dp
private val GutterBackgroundColor = Color(0xFFE0E0E0) // Light gray
private val GutterLineColor = Color(0xFF9E9E9E) // Dark gray

@Composable
private fun MainContentTextField(
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
    val density = LocalDensity.current

    // Track drag selection state
    var dragStartLine by remember { mutableIntStateOf(-1) }
    var dragEndLine by remember { mutableIntStateOf(-1) }

    // Track last indent time for double-space to unindent
    var lastIndentTime by remember { mutableStateOf(0L) }

    // Context menu state
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(Offset.Zero) }

    // Track previous selection to detect tap-in-selection and new selections
    var previousSelection by remember { mutableStateOf(TextRange.Zero) }

    // Flag to skip restore logic when unselect is intentional
    var skipNextRestore by remember { mutableStateOf(false) }

    // Detect selection changes and handle:
    // 1. New selection created (from no selection) -> show menu (after gesture ends)
    // 2. Selection changed (from one selection to another) -> show menu (after gesture ends)
    // 3. Tap in existing selection (selection collapses to point within it) -> restore and show menu
    LaunchedEffect(textFieldValue.selection, textLayoutResult) {
        val currentSelection = textFieldValue.selection
        val prevSel = previousSelection

        // Case 1 & 2: Selection is non-collapsed and either:
        // - was collapsed before (new selection from cursor)
        // - was different selection before (new selection replacing old)
        val isNewOrChangedSelection = !currentSelection.collapsed &&
            (prevSel.collapsed || currentSelection != prevSel)

        if (isNewOrChangedSelection && isFingerDownFlow != null) {
            // If finger is already up, wait for next down->up cycle
            // This handles selection handles which may bypass Activity's dispatchTouchEvent
            if (!isFingerDownFlow.value) {
                isFingerDownFlow.first { it }  // Wait for finger down
            }

            // Wait for finger to lift
            isFingerDownFlow.first { !it }

            // Finger is up - show menu if we still have a selection
            if (!textFieldValue.selection.collapsed) {
                textLayoutResult?.let { layout ->
                    val selStart = textFieldValue.selection.min
                    val startLine = layout.getLineForOffset(selStart)
                    val lineRight = layout.getLineRight(startLine)
                    val lineTop = layout.getLineTop(startLine)
                    contextMenuOffset = Offset(lineRight + 16f, lineTop)
                }
                showContextMenu = true
            }
        } else if (currentSelection.collapsed && !prevSel.collapsed) {
            // Case 3: Selection just collapsed - check if cursor is within the previous selection
            // Skip if this was an intentional unselect action
            if (skipNextRestore) {
                skipNextRestore = false
            } else {
                val cursorPos = currentSelection.start
                if (cursorPos >= prevSel.min && cursorPos <= prevSel.max) {
                    // Tap was inside the selection - restore selection and show menu
                    textLayoutResult?.let { layout ->
                        val startLine = layout.getLineForOffset(prevSel.min)
                        val lineRight = layout.getLineRight(startLine)
                        val lineTop = layout.getLineTop(startLine)
                        contextMenuOffset = Offset(lineRight + 16f, lineTop)
                    }
                    // Restore the previous selection
                    onTextFieldValueChange(textFieldValue.copy(selection = prevSel))
                    showContextMenu = true
                    // Don't update previousSelection here - we're restoring it
                    return@LaunchedEffect
                }
            }
        }

        previousSelection = currentSelection
    }

    Row(modifier = modifier.fillMaxWidth()) {
        // Gutter
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

        // Text field with selection tap handling
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            // Disable the system text toolbar by providing a no-op implementation
            val emptyTextToolbar = remember {
                object : TextToolbar {
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
            }

            androidx.compose.runtime.CompositionLocalProvider(
                LocalTextToolbar provides emptyTextToolbar
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        // Check if this is space with selection -> indent/unindent
                        if (isSpaceReplacingSelection(textFieldValue, newValue)) {
                            val currentTime = System.currentTimeMillis()
                            val isDoubleSpace = currentTime - lastIndentTime < 250
                            lastIndentTime = currentTime

                            val result = if (isDoubleSpace) {
                                // Double-space: unindent from original state
                                // First unindent undoes the indent we just did, second actually unindents
                                val firstUnindent = handleSelectionUnindent(textFieldValue)
                                if (firstUnindent != null) {
                                    handleSelectionUnindent(firstUnindent) ?: firstUnindent
                                } else {
                                    // Can't unindent - keep current state
                                    textFieldValue
                                }
                            } else {
                                // Single space - indent
                                handleSelectionIndent(textFieldValue)
                            }

                            onTextFieldValueChange(result)
                            // Always return early to prevent space from replacing selection
                            return@BasicTextField
                        }

                        // Check if this is a deletion of fully selected line(s)
                        val lineDeleteResult = handleFullLineDelete(textFieldValue, newValue)
                        if (lineDeleteResult != null) {
                            onTextFieldValueChange(lineDeleteResult)
                            return@BasicTextField
                        }

                        val tapped = handleCheckboxTap(textFieldValue, newValue)
                        var transformed = if (tapped != null) {
                            tapped
                        } else {
                            transformBulletText(textFieldValue, newValue)
                        }

                        transformed = handlePasteTransformation(context, textFieldValue, transformed)

                        onTextFieldValueChange(transformed)
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

                // Context menu for selection
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    offset = with(density) {
                        androidx.compose.ui.unit.DpOffset(
                            contextMenuOffset.x.toDp(),
                            contextMenuOffset.y.toDp() - 48.dp
                        )
                    }
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_copy),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            val selectedText = textFieldValue.text.substring(
                                textFieldValue.selection.min,
                                textFieldValue.selection.max
                            )
                            clipboardManager.setText(AnnotatedString(selectedText))
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Cut") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_cut),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            skipNextRestore = true
                            val selectedText = textFieldValue.text.substring(
                                textFieldValue.selection.min,
                                textFieldValue.selection.max
                            )
                            clipboardManager.setText(AnnotatedString(selectedText))
                            val newText = textFieldValue.text.removeRange(
                                textFieldValue.selection.min,
                                textFieldValue.selection.max
                            )
                            onTextFieldValueChange(TextFieldValue(newText, TextRange(textFieldValue.selection.min)))
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Select All") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_select_all),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            onTextFieldValueChange(textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length)))
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Unselect") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_deselect),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            // Collapse selection to cursor at end of selection
                            skipNextRestore = true
                            onTextFieldValueChange(textFieldValue.copy(selection = TextRange(textFieldValue.selection.max)))
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            skipNextRestore = true
                            val newText = textFieldValue.text.removeRange(
                                textFieldValue.selection.min,
                                textFieldValue.selection.max
                            )
                            onTextFieldValueChange(TextFieldValue(newText, TextRange(textFieldValue.selection.min)))
                            showContextMenu = false
                        }
                    )
                }

            }
            }
        }
    }
}

@Composable
private fun LineGutter(
    textLayoutResult: TextLayoutResult?,
    scrollState: androidx.compose.foundation.ScrollState,
    onLineSelected: (Int) -> Unit,
    onDragStart: (Int) -> Unit,
    onDragUpdate: (Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val lineCount = textLayoutResult?.lineCount ?: 1
    val lineHeight = textLayoutResult?.let {
        if (it.lineCount > 0) it.getLineBottom(0) - it.getLineTop(0) else 0f
    } ?: with(density) { 20.dp.toPx() }

    // Calculate total height based on text layout
    val totalHeight = textLayoutResult?.let {
        if (it.lineCount > 0) it.getLineBottom(it.lineCount - 1) else lineHeight
    } ?: lineHeight

    val gutterWidthPx = with(density) { GutterWidth.toPx() }

    Box(
        modifier = modifier
            .width(GutterWidth)
            .verticalScroll(scrollState)
            .drawBehind {
                // Draw background
                drawRect(
                    color = GutterBackgroundColor,
                    size = Size(gutterWidthPx, totalHeight + with(density) { 16.dp.toPx() })
                )

                // Draw horizontal lines between each row
                for (i in 0..lineCount) {
                    val y = if (textLayoutResult != null && i < textLayoutResult.lineCount) {
                        textLayoutResult.getLineTop(i)
                    } else if (textLayoutResult != null && i == textLayoutResult.lineCount) {
                        textLayoutResult.getLineBottom(i - 1)
                    } else {
                        i * lineHeight
                    }
                    // Add padding offset to match text field
                    val yWithPadding = y + with(density) { 8.dp.toPx() }
                    drawLine(
                        color = GutterLineColor,
                        start = Offset(0f, yWithPadding),
                        end = Offset(gutterWidthPx, yWithPadding),
                        strokeWidth = 1f
                    )
                }
            }
            .pointerInput(textLayoutResult) {
                val paddingPx = with(density) { 8.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startOffset = down.position
                    val startLineIndex = getLineIndexFromOffset(textLayoutResult, startOffset.y - paddingPx, lineHeight)

                    if (startLineIndex !in 0 until lineCount) {
                        return@awaitEachGesture
                    }

                    var isDragging = false
                    var currentLineIndex = startLineIndex

                    // Select the initial line immediately
                    onDragStart(startLineIndex)

                    // Track drag
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (change.pressed) {
                            val newLineIndex = getLineIndexFromOffset(
                                textLayoutResult,
                                change.position.y - paddingPx,
                                lineHeight
                            ).coerceIn(0, lineCount - 1)

                            if (newLineIndex != currentLineIndex) {
                                isDragging = true
                                currentLineIndex = newLineIndex
                                onDragUpdate(currentLineIndex)
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })

                    // Gesture ended
                    if (!isDragging) {
                        // It was a tap
                        onLineSelected(startLineIndex)
                    }
                    onDragEnd()
                }
            }
    ) {
        // Empty box with calculated height to enable scrolling
        Box(
            modifier = Modifier
                .width(GutterWidth)
                .height(with(density) { (totalHeight + 16.dp.toPx()).toDp() })
        )
    }
}

private fun getLineIndexFromOffset(
    textLayoutResult: TextLayoutResult?,
    yOffset: Float,
    defaultLineHeight: Float
): Int {
    if (textLayoutResult == null || textLayoutResult.lineCount == 0) {
        return (yOffset / defaultLineHeight).toInt()
    }

    // Binary search or linear search for the line
    for (i in 0 until textLayoutResult.lineCount) {
        val top = textLayoutResult.getLineTop(i)
        val bottom = textLayoutResult.getLineBottom(i)
        if (yOffset >= top && yOffset < bottom) {
            return i
        }
    }

    // If below all lines, return last line
    if (yOffset >= textLayoutResult.getLineBottom(textLayoutResult.lineCount - 1)) {
        return textLayoutResult.lineCount - 1
    }

    return 0
}


/**
 * Detects paste operations and transforms HTML list content to bulleted text.
 */
private fun handlePasteTransformation(
    context: Context,
    oldValue: TextFieldValue,
    newValue: TextFieldValue
): TextFieldValue {
    // Detect if this is a paste: text grew significantly and it's an insertion
    val insertedLength = newValue.text.length - oldValue.text.length
    if (insertedLength < 2) return newValue // Not a paste, too small

    // Get the inserted text
    val insertionStart = oldValue.selection.min
    val insertionEnd = insertionStart + insertedLength
    if (insertionEnd > newValue.text.length) return newValue

    val insertedText = newValue.text.substring(insertionStart, insertionEnd)

    // Check if clipboard has HTML lists that should be converted
    val bulletedText = getClipboardAsBulletedText(context) ?: return newValue

    // Verify the inserted text matches clipboard content (it's actually a paste)
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: return newValue

    if (insertedText.trim() != clipText.trim()) return newValue // Not a clipboard paste

    // Replace the pasted text with the bulleted version
    val newText = newValue.text.substring(0, insertionStart) +
            bulletedText +
            newValue.text.substring(insertionEnd)

    val newCursorPos = insertionStart + bulletedText.length

    return TextFieldValue(newText, TextRange(newCursorPos))
}

@Composable
private fun AgentCommandSection(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    agentCommand: String,
    onAgentCommandChange: (String) -> Unit,
    isProcessing: Boolean,
    onSendCommand: () -> Unit,
    mainContentFocusRequester: FocusRequester
) {
    var hasBeenFocused by remember { mutableStateOf(false) }

    // Reset focus tracking when section collapses
    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            hasBeenFocused = false
        }
    }

    AgentSectionHeader(
        isExpanded = isExpanded,
        onExpand = { onExpandedChange(true) },
        onCollapse = {
            mainContentFocusRequester.requestFocus()
            onExpandedChange(false)
        }
    )

    if (isProcessing) {
        ProcessingIndicatorBar()
    }

    if (isExpanded) {
        AgentCommandTextField(
            command = agentCommand,
            onCommandChange = onAgentCommandChange,
            isProcessing = isProcessing,
            onSendCommand = onSendCommand,
            onFocusGained = { hasBeenFocused = true },
            onFocusLost = {
                if (hasBeenFocused && agentCommand.isBlank()) {
                    onExpandedChange(false)
                }
            }
        )
    }
}

@Composable
private fun AgentSectionHeader(
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.brand_color))
            .clickable { onExpand() }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.agent_chat_label),
            color = colorResource(R.color.brand_text_color),
            fontSize = 18.sp
        )
        if (isExpanded) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = colorResource(R.color.brand_text_color),
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCollapse() }
            )
        }
    }
}

@Composable
private fun AgentCommandTextField(
    command: String,
    onCommandChange: (String) -> Unit,
    isProcessing: Boolean,
    onSendCommand: () -> Unit,
    onFocusGained: () -> Unit,
    onFocusLost: () -> Unit
) {
    TextField(
        value = command,
        onValueChange = onCommandChange,
        enabled = !isProcessing,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocusGained()
                } else {
                    onFocusLost()
                }
            },
        trailingIcon = {
            IconButton(
                enabled = !isProcessing && command.isNotBlank(),
                onClick = {
                    if (command.isNotBlank()) {
                        onSendCommand()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (isProcessing || command.isBlank()) Color.Gray else colorResource(R.color.brand_color)
                )
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = if (isProcessing) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent,
            unfocusedContainerColor = if (isProcessing) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent,
            disabledContainerColor = Color.LightGray.copy(alpha = 0.3f)
        ),
        maxLines = 5,
        minLines = 5
    )
}

@Composable
fun ProcessingIndicatorBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "processing_animation")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_offset"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            colorResource(R.color.brand_color),
            Color.White,
            colorResource(R.color.brand_color)
        ),
        start = Offset(x = offset * 1000f, y = 0f),
        end = Offset(x = offset * 1000f + 500f, y = 0f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(brush)
    )
}

@Composable
fun StatusBar(
    isSaved: Boolean,
    onSaveClick: () -> Unit
) {
    ActionButtonBar {
        androidx.compose.material3.Button(
            onClick = onSaveClick,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.action_button_background),
                contentColor = Color.White
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.StatusBarButtonCornerRadius),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.StatusBarButtonHorizontalPadding, vertical = 0.dp),
            modifier = Modifier.height(Dimens.StatusBarButtonHeight)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_save),
                contentDescription = stringResource(id = R.string.action_save),
                modifier = Modifier.size(Dimens.StatusBarButtonIconSize),
                tint = Color.White
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(Dimens.StatusBarButtonIconTextSpacing))
            Text(
                text = stringResource(id = R.string.action_save),
                fontSize = Dimens.StatusBarButtonTextSize
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(Dimens.StatusBarItemSpacing))

        Text(
            text = if (isSaved) stringResource(id = R.string.status_saved) else stringResource(id = R.string.status_unsaved),
            color = Color.Black,
            fontSize = Dimens.StatusTextSize
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(Dimens.StatusTextIconSpacing))

        Icon(
            painter = if (isSaved) painterResource(id = R.drawable.ic_check_circle) else painterResource(id = R.drawable.ic_warning),
            contentDescription = null,
            tint = if (isSaved) Color(0xFF4CAF50) else Color(0xFFFFC107),
            modifier = Modifier.size(Dimens.StatusIconSize)
        )
    }
}

@Composable
private fun CommandBar(
    onToggleBullet: () -> Unit,
    onToggleCheckbox: () -> Unit,
    onIndent: () -> Unit,
    onUnindent: () -> Unit,
    onPaste: (String) -> Unit,
    isPasteEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Bullet toggle button
        IconButton(
            onClick = onToggleBullet,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_format_list_bulleted),
                contentDescription = "Toggle bullet",
                tint = Color(0xFF616161),
                modifier = Modifier.size(24.dp)
            )
        }

        // Checkbox toggle button
        IconButton(
            onClick = onToggleCheckbox,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check_box_outline),
                contentDescription = "Toggle checkbox",
                tint = Color(0xFF616161),
                modifier = Modifier.size(24.dp)
            )
        }

        // Unindent button
        IconButton(
            onClick = onUnindent,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_format_indent_decrease),
                contentDescription = "Unindent",
                tint = Color(0xFF616161),
                modifier = Modifier.size(24.dp)
            )
        }

        // Indent button
        IconButton(
            onClick = onIndent,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_format_indent_increase),
                contentDescription = "Indent",
                tint = Color(0xFF616161),
                modifier = Modifier.size(24.dp)
            )
        }

        // Paste button
        IconButton(
            onClick = {
                val clipText = clipboardManager.getText()?.text ?: ""
                if (clipText.isNotEmpty()) {
                    onPaste(clipText)
                }
            },
            enabled = isPasteEnabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_paste),
                contentDescription = "Paste",
                tint = if (isPasteEnabled) Color(0xFF616161) else Color(0xFFBDBDBD),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CurrentNoteScreenPreview() {
    CurrentNoteScreen()
}
