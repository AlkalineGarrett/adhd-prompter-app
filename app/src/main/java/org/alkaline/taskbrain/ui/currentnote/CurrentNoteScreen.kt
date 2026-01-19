package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import org.alkaline.taskbrain.ui.components.ErrorDialog

/**
 * Main screen for viewing and editing a note.
 * Coordinates the note text field, command bar, and agent command section.
 */
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

    // Alarm dialog state
    var showAlarmDialog by remember { mutableStateOf(false) }
    var alarmDialogLineContent by remember { mutableStateOf("") }

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

    // Alarm configuration dialog
    if (showAlarmDialog) {
        AlarmConfigDialog(
            lineContent = alarmDialogLineContent,
            existingAlarm = null,
            onSave = { upcomingTime, notifyTime, urgentTime, alarmTime ->
                currentNoteViewModel.createAlarm(
                    lineContent = alarmDialogLineContent,
                    upcomingTime = upcomingTime,
                    notifyTime = notifyTime,
                    urgentTime = urgentTime,
                    alarmTime = alarmTime
                )
            },
            onDismiss = { showAlarmDialog = false }
        )
    }

    // Monitor clipboard and add HTML formatting for bullets/checkboxes
    ClipboardHtmlConverter()

    // Helper to update text field value and track changes
    val updateTextFieldValue: (TextFieldValue) -> Unit = { newValue ->
        textFieldValue = newValue
        if (newValue.text != userContent) {
            userContent = newValue.text
            if (isSaved) isSaved = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StatusBar(
            isSaved = isSaved,
            onSaveClick = { currentNoteViewModel.saveContent(userContent) }
        )

        NoteTextField(
            textFieldValue = textFieldValue,
            onTextFieldValueChange = updateTextFieldValue,
            focusRequester = mainContentFocusRequester,
            onFocusChanged = { isFocused -> isMainContentFocused = isFocused },
            isFingerDownFlow = isFingerDownFlow,
            modifier = Modifier.weight(1f)
        )

        CommandBar(
            onToggleBullet = {
                updateTextFieldValue(toggleBulletOnCurrentLine(textFieldValue))
            },
            onToggleCheckbox = {
                updateTextFieldValue(toggleCheckboxOnCurrentLine(textFieldValue))
            },
            onIndent = {
                updateTextFieldValue(handleSelectionIndent(textFieldValue))
            },
            onUnindent = {
                handleSelectionUnindent(textFieldValue)?.let { updateTextFieldValue(it) }
            },
            onPaste = { clipText ->
                updateTextFieldValue(SelectionActions.insertText(textFieldValue, clipText))
            },
            isPasteEnabled = isMainContentFocused && textFieldValue.selection.collapsed,
            onAddAlarm = {
                alarmDialogLineContent = TextLineUtils.getLineContent(
                    textFieldValue.text,
                    textFieldValue.selection.start
                )
                showAlarmDialog = true
            },
            isAlarmEnabled = isMainContentFocused && textFieldValue.selection.collapsed
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

@Preview(showBackground = true)
@Composable
fun CurrentNoteScreenPreview() {
    CurrentNoteScreen()
}
