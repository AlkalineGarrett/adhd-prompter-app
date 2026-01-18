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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.ui.Dimens
import org.alkaline.taskbrain.ui.components.ActionButtonBar
import org.alkaline.taskbrain.ui.components.ErrorDialog

@Composable
fun CurrentNoteScreen(
    noteId: String? = null,
    currentNoteViewModel: CurrentNoteViewModel = viewModel()
) {
    val saveStatus by currentNoteViewModel.saveStatus.observeAsState()
    val loadStatus by currentNoteViewModel.loadStatus.observeAsState()
    val contentModified by currentNoteViewModel.contentModified.observeAsState(false)
    val isAgentProcessing by currentNoteViewModel.isAgentProcessing.observeAsState(false)

    var userContent by remember { mutableStateOf("") }
    var isSaved by remember { mutableStateOf(true) }
    var agentCommand by remember { mutableStateOf("") }
    var isAgentSectionExpanded by remember { mutableStateOf(false) }

    val mainContentFocusRequester = remember { FocusRequester() }

    // Handle initial data loading
    LaunchedEffect(noteId) {
        currentNoteViewModel.loadContent(noteId)
    }

    // Update content when loaded from VM
    LaunchedEffect(loadStatus) {
        if (loadStatus is LoadStatus.Success) {
            userContent = (loadStatus as LoadStatus.Success).content
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
            content = userContent,
            onContentChange = {
                userContent = it
                if (isSaved) isSaved = false
            },
            focusRequester = mainContentFocusRequester,
            modifier = Modifier.weight(1f)
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

@Composable
private fun MainContentTextField(
    content: String,
    onContentChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var textFieldValue by remember { mutableStateOf(TextFieldValue(content)) }

    // Sync external content changes (e.g., from ViewModel)
    LaunchedEffect(content) {
        if (content != textFieldValue.text) {
            textFieldValue = TextFieldValue(content, TextRange(content.length))
        }
    }

    TextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            // First check for checkbox tap (cursor moved to checkbox without text change)
            val tapped = handleCheckboxTap(textFieldValue, newValue)
            var transformed = if (tapped != null) {
                tapped
            } else {
                transformBulletText(textFieldValue, newValue)
            }

            // Check if this looks like a paste and transform HTML lists to bullets
            transformed = handlePasteTransformation(context, textFieldValue, transformed)

            textFieldValue = transformed
            if (transformed.text != content) {
                onContentChange(transformed.text)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        )
    )
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

@Preview(showBackground = true)
@Composable
fun CurrentNoteScreenPreview() {
    CurrentNoteScreen()
}
