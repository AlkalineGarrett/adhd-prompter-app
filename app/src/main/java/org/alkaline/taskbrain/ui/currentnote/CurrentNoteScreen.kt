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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
    val isAlarmOperationPending by currentNoteViewModel.isAlarmOperationPending.observeAsState(false)
    val redoRollbackWarning by currentNoteViewModel.redoRollbackWarning.observeAsState()

    var userContent by remember { mutableStateOf("") }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isSaved by remember { mutableStateOf(true) }
    var agentCommand by remember { mutableStateOf("") }
    var isAgentSectionExpanded by remember { mutableStateOf(false) }

    val mainContentFocusRequester = remember { FocusRequester() }
    var isMainContentFocused by remember { mutableStateOf(false) }
    val editorState = rememberHangingIndentEditorState()
    val controller = rememberEditorController(editorState)

    // Context and coroutine scope for undo state persistence
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Track current note ID for undo state persistence (observed from ViewModel)
    val currentNoteId by currentNoteViewModel.currentNoteIdLiveData.observeAsState()

    // Track undo/redo button states - need to observe state versions to trigger recomposition
    @Suppress("UNUSED_VARIABLE")
    val editorStateVersion = editorState.stateVersion
    @Suppress("UNUSED_VARIABLE")
    val undoStateVersion = controller.undoManager.stateVersion
    val canUndo = controller.canUndo
    val canRedo = controller.canRedo

    // Alarm dialog state
    var showAlarmDialog by remember { mutableStateOf(false) }
    var alarmDialogLineContent by remember { mutableStateOf("") }
    var alarmDialogLineIndex by remember { mutableStateOf<Int?>(null) }

    // Auto-save when navigating away or app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUserContent by rememberUpdatedState(userContent)
    val currentIsSaved by rememberUpdatedState(isSaved)

    // Track current note ID for persistence (using rememberUpdatedState for lifecycle access)
    val currentNoteIdForPersistence by rememberUpdatedState(currentNoteId)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Commit undo state before navigating away
                controller.commitUndoState()
                // Persist undo state for this note (blocking to ensure it completes)
                currentNoteIdForPersistence?.let { noteId ->
                    UndoStatePersistence.saveStateBlocking(context, noteId, controller.undoManager)
                }
                if (!currentIsSaved && currentUserContent.isNotEmpty()) {
                    currentNoteViewModel.saveContent(currentUserContent)
                }
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
            // CRITICAL: Update editorState BEFORE setBaseline() below!
            // Without this line, editorState is empty when baseline is captured,
            // which means undo can restore to empty state and LOSE USER DATA.
            // See UndoManagerTest: "CRITICAL - baseline must contain actual content"
            editorState.updateFromText(loadedContent)
            // Try to restore persisted undo state, or reset if none exists
            val noteIdForRestore = currentNoteViewModel.getCurrentNoteId()
            val restored = UndoStatePersistence.restoreState(context, noteIdForRestore, controller.undoManager)
            if (!restored) {
                controller.resetUndoHistory()
            }
            // Always ensure baseline is set - this is the "floor" for undo.
            // On fresh load (no restored state), this captures the loaded content.
            // On restore, this ensures baseline exists even if restored from old format.
            if (!controller.undoManager.hasBaseline) {
                controller.undoManager.setBaseline(editorState)
            }
            controller.undoManager.beginEditingLine(editorState, editorState.focusedLineIndex)
            // Trigger focus update now that content is loaded.
            // HangingIndentEditor waits for stateVersion > 0 before requesting focus
            // to avoid cursor jumping from position 0 to end of line on initial load.
            editorState.requestFocusUpdate()
        }
    }

    // React to content modification signal (e.g. from Agent)
    // Clear undo history since externally modified content would have stale snapshots
    LaunchedEffect(contentModified) {
        if (contentModified) {
            isSaved = false
            controller.resetUndoHistory()
        }
    }

    // Handle save status changes
    LaunchedEffect(saveStatus) {
        if (saveStatus is SaveStatus.Success) {
            isSaved = true
            currentNoteViewModel.markAsSaved()
        }
    }

    // Handle alarm creation - insert symbol at end of line, record for undo, and save
    LaunchedEffect(alarmCreated) {
        alarmCreated?.let { event ->
            controller.insertAtEndOfCurrentLine(AlarmSymbolUtils.ALARM_SYMBOL)
            userContent = editorState.text
            // Record alarm creation for undo/redo (snapshot is passed from ViewModel)
            event.alarmSnapshot?.let { snapshot ->
                controller.recordAlarmCreation(snapshot)
            }
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

    // Redo rollback warning dialog - shown when alarm recreation fails during redo
    redoRollbackWarning?.let { warning ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { currentNoteViewModel.clearRedoRollbackWarning() },
            title = {
                androidx.compose.material3.Text(
                    if (warning.rollbackSucceeded) "Redo Failed" else "Redo Error"
                )
            },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    androidx.compose.material3.Text(
                        if (warning.rollbackSucceeded) {
                            "Could not recreate the alarm: ${warning.errorMessage}\n\n" +
                            "The document has been automatically rolled back to its previous state."
                        } else {
                            "Could not recreate the alarm: ${warning.errorMessage}\n\n" +
                            "Warning: The document may be in an inconsistent state. " +
                            "The alarm symbol may be visible but no alarm exists. " +
                            "Consider saving and reloading the note."
                        }
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { currentNoteViewModel.clearRedoRollbackWarning() }
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
            onSaveClick = {
                controller.commitUndoState(continueEditing = true)
                currentNoteViewModel.saveContent(userContent)
            },
            // Disable undo/redo during alarm operations to prevent race conditions
            canUndo = canUndo && !isAlarmOperationPending,
            canRedo = canRedo && !isAlarmOperationPending,
            onUndoClick = {
                controller.commitUndoState()
                val snapshot = controller.undo()
                if (snapshot != null) {
                    userContent = editorState.text
                    isSaved = false
                    // If this snapshot created an alarm, delete it permanently
                    snapshot.createdAlarm?.let { alarm ->
                        currentNoteViewModel.deleteAlarmPermanently(alarm.id)
                    }
                }
            },
            onRedoClick = {
                controller.commitUndoState()
                val snapshot = controller.redo()
                if (snapshot != null) {
                    userContent = editorState.text
                    isSaved = false
                    // If the undone snapshot had an alarm, recreate it
                    snapshot.createdAlarm?.let { alarm ->
                        currentNoteViewModel.recreateAlarm(
                            alarmSnapshot = alarm,
                            onAlarmCreated = { newId ->
                                controller.updateLastUndoAlarmId(newId)
                            },
                            onFailure = { errorMessage ->
                                // If alarm recreation fails, undo the redo to remove orphaned alarm symbol
                                val rollbackSnapshot = controller.undo()
                                val rollbackSucceeded = rollbackSnapshot != null
                                if (rollbackSucceeded) {
                                    userContent = editorState.text
                                }
                                // Show warning dialog explaining what happened
                                currentNoteViewModel.showRedoRollbackWarning(rollbackSucceeded, errorMessage)
                            }
                        )
                    }
                }
            }
        )

        NoteTextField(
            textFieldValue = textFieldValue,
            onTextFieldValueChange = updateTextFieldValue,
            focusRequester = mainContentFocusRequester,
            onFocusChanged = { isFocused -> isMainContentFocused = isFocused },
            editorState = editorState,
            controller = controller,
            isFingerDownFlow = isFingerDownFlow,
            onAlarmSymbolTap = { symbolInfo ->
                // Get the line content for this line and show the alarm dialog
                val lineContent = editorState.lines.getOrNull(symbolInfo.lineIndex)?.text ?: ""
                alarmDialogLineContent = TextLineUtils.trimLineForAlarm(lineContent)
                alarmDialogLineIndex = symbolInfo.lineIndex
                showAlarmDialog = true
            },
            modifier = Modifier.weight(1f)
        )

        CommandBar(
            onToggleBullet = { controller.toggleBullet() },
            onToggleCheckbox = { controller.toggleCheckbox() },
            onIndent = { controller.indent() },
            onUnindent = { controller.unindent() },
            onPaste = { clipText -> controller.paste(clipText) },
            isPasteEnabled = isMainContentFocused && !editorState.hasSelection,
            onAddAlarm = {
                controller.commitUndoState(continueEditing = true)
                val lineContent = editorState.currentLine?.text ?: ""
                alarmDialogLineContent = TextLineUtils.trimLineForAlarm(lineContent)
                alarmDialogLineIndex = editorState.focusedLineIndex
                showAlarmDialog = true
            },
            isAlarmEnabled = isMainContentFocused && !editorState.hasSelection
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
