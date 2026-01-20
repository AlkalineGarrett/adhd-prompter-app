package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val alarmCreated by currentNoteViewModel.alarmCreated.observeAsState()
    val alarmError by currentNoteViewModel.alarmError.observeAsState()
    val alarmPermissionWarning by currentNoteViewModel.alarmPermissionWarning.observeAsState(false)
    val notificationPermissionWarning by currentNoteViewModel.notificationPermissionWarning.observeAsState(false)
    val schedulingWarning by currentNoteViewModel.schedulingWarning.observeAsState()

    var userContent by remember { mutableStateOf("") }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isSaved by remember { mutableStateOf(true) }
    var agentCommand by remember { mutableStateOf("") }
    var isAgentSectionExpanded by remember { mutableStateOf(false) }

    val mainContentFocusRequester = remember { FocusRequester() }
    var isMainContentFocused by remember { mutableStateOf(false) }
    val editorState = rememberHangingIndentEditorState()

    // Alarm dialog state
    var showAlarmDialog by remember { mutableStateOf(false) }
    var alarmDialogLineContent by remember { mutableStateOf("") }
    var alarmDialogLineIndex by remember { mutableStateOf<Int?>(null) }

    // Auto-save when navigating away or app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUserContent by rememberUpdatedState(userContent)
    val currentIsSaved by rememberUpdatedState(isSaved)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !currentIsSaved && currentUserContent.isNotEmpty()) {
                currentNoteViewModel.saveContent(currentUserContent)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    // Handle alarm creation - insert symbol at end of line and save
    LaunchedEffect(alarmCreated) {
        alarmCreated?.let { event ->
            editorState.insertAtEndOfCurrentLine(AlarmSymbolUtils.ALARM_SYMBOL)
            userContent = editorState.text
            // Save the note with the alarm symbol included
            currentNoteViewModel.saveContent(editorState.text)
            currentNoteViewModel.clearAlarmCreatedEvent()
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

    alarmError?.let { throwable ->
        ErrorDialog(
            title = "Alarm Error",
            throwable = throwable,
            onDismiss = { currentNoteViewModel.clearAlarmError() }
        )
    }

    // Alarm permission warning dialog
    if (alarmPermissionWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { currentNoteViewModel.clearAlarmPermissionWarning() },
            title = { androidx.compose.material3.Text("Exact Alarms Disabled") },
            text = {
                androidx.compose.material3.Text(
                    "Exact alarm permission is not granted. Your alarm may not trigger at the exact time.\n\n" +
                    "To enable: Settings → Apps → TaskBrain → Alarms & reminders → Allow"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { currentNoteViewModel.clearAlarmPermissionWarning() }
                ) {
                    androidx.compose.material3.Text("OK")
                }
            }
        )
    }

    // Notification permission warning dialog
    if (notificationPermissionWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { currentNoteViewModel.clearNotificationPermissionWarning() },
            title = { androidx.compose.material3.Text("Notifications Disabled") },
            text = {
                androidx.compose.material3.Text(
                    "Notification permission is not granted. Your alarms will not show notifications.\n\n" +
                    "To enable: Settings → Apps → TaskBrain → Notifications → Allow"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { currentNoteViewModel.clearNotificationPermissionWarning() }
                ) {
                    androidx.compose.material3.Text("OK")
                }
            }
        )
    }

    // Scheduling warning dialog - shown when alarm couldn't be scheduled
    schedulingWarning?.let { warning ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { currentNoteViewModel.clearSchedulingWarning() },
            title = { androidx.compose.material3.Text("Alarm Scheduling Issue") },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    androidx.compose.material3.Text(
                        "$warning\n\nThe alarm was saved but may not trigger at the expected time."
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { currentNoteViewModel.clearSchedulingWarning() }
                ) {
                    androidx.compose.material3.Text("OK")
                }
            }
        )
    }

    // Alarm configuration dialog
    if (showAlarmDialog) {
        AlarmConfigDialog(
            lineContent = alarmDialogLineContent,
            existingAlarm = null,
            onSave = { upcomingTime, notifyTime, urgentTime, alarmTime ->
                // Auto-save before creating alarm to ensure correct note IDs
                currentNoteViewModel.saveAndCreateAlarm(
                    content = userContent,
                    lineContent = alarmDialogLineContent,
                    lineIndex = alarmDialogLineIndex,
                    upcomingTime = upcomingTime,
                    notifyTime = notifyTime,
                    urgentTime = urgentTime,
                    alarmTime = alarmTime
                )
            },
            onDismiss = {
                showAlarmDialog = false
                alarmDialogLineIndex = null
            }
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
            editorState = editorState,
            isFingerDownFlow = isFingerDownFlow,
            onAlarmSymbolTap = { symbolInfo ->
                // Get the line content for this line and show the alarm dialog
                val lineStart = TextLineUtils.getLineStartOffset(textFieldValue.text, symbolInfo.lineIndex)
                val lineContent = TextLineUtils.getLineContent(textFieldValue.text, lineStart)
                alarmDialogLineContent = TextLineUtils.trimLineForAlarm(lineContent)
                alarmDialogLineIndex = symbolInfo.lineIndex
                showAlarmDialog = true
            },
            modifier = Modifier.weight(1f)
        )

        CommandBar(
            onToggleBullet = {
                editorState.toggleBullet()
            },
            onToggleCheckbox = {
                editorState.toggleCheckbox()
            },
            onIndent = {
                editorState.indent()
            },
            onUnindent = {
                editorState.unindent()
            },
            onPaste = { clipText ->
                editorState.replaceSelection(clipText)
            },
            isPasteEnabled = isMainContentFocused && !editorState.hasSelection,
            onAddAlarm = {
                val cursorPos = textFieldValue.selection.start
                val lineContent = TextLineUtils.getLineContent(textFieldValue.text, cursorPos)
                alarmDialogLineContent = TextLineUtils.trimLineForAlarm(lineContent)
                alarmDialogLineIndex = TextLineUtils.getLineIndex(textFieldValue.text, cursorPos)
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
