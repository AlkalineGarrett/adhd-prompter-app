package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.background
import org.alkaline.taskbrain.ui.currentnote.util.AlarmSymbolUtils
import org.alkaline.taskbrain.ui.currentnote.util.ClipboardHtmlConverter
import org.alkaline.taskbrain.ui.currentnote.undo.UndoStatePersistence
import org.alkaline.taskbrain.ui.currentnote.util.TextLineUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import org.alkaline.taskbrain.ui.currentnote.components.AgentCommandSection
import org.alkaline.taskbrain.ui.currentnote.components.AlarmConfigDialog
import org.alkaline.taskbrain.ui.currentnote.components.CommandBar
import org.alkaline.taskbrain.ui.currentnote.components.NoteTextField
import org.alkaline.taskbrain.ui.currentnote.components.StatusBar

/**
 * Main screen for viewing and editing a note.
 * Coordinates the note text field, command bar, and agent command section.
 */
@Composable
fun CurrentNoteScreen(
    noteId: String? = null,
    isFingerDownFlow: StateFlow<Boolean>? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToNote: (String) -> Unit = {},
    currentNoteViewModel: CurrentNoteViewModel = viewModel(),
    recentTabsViewModel: RecentTabsViewModel = viewModel()
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
    val isNoteDeletedFromVm by currentNoteViewModel.isNoteDeleted.observeAsState(false)
    val recentTabs by recentTabsViewModel.tabs.observeAsState(emptyList())
    val tabsError by recentTabsViewModel.error.observeAsState()

    // Internal note ID state - allows tab switching without navigation
    // Initialize from route parameter, but can be updated internally for tab switches
    var displayedNoteId by remember { mutableStateOf(noteId) }

    // Sync with route parameter when it changes (external navigation)
    LaunchedEffect(noteId) {
        if (noteId != displayedNoteId) {
            displayedNoteId = noteId
        }
    }

    // Get cached content for immediate initialization (prevents flashing on tab switch)
    val cachedContent = remember(displayedNoteId) {
        displayedNoteId?.let { recentTabsViewModel.getCachedContent(it) }
    }
    val initialContent = cachedContent?.noteLines?.joinToString("\n") { it.content } ?: ""
    val initialIsDeleted = cachedContent?.isDeleted ?: false

    // Track deleted state keyed on displayedNoteId, initialized from cache to prevent background flash
    // ViewModel will update this via effect when it loads
    var isNoteDeleted by remember(displayedNoteId) { mutableStateOf(initialIsDeleted) }

    // Sync with ViewModel when it updates (ViewModel is authoritative)
    LaunchedEffect(isNoteDeletedFromVm) {
        isNoteDeleted = isNoteDeletedFromVm
    }

    // Key states on displayedNoteId to reset when switching tabs, but initialize with cache
    var userContent by remember(displayedNoteId) { mutableStateOf(initialContent) }
    var textFieldValue by remember(displayedNoteId) {
        mutableStateOf(TextFieldValue(initialContent, TextRange(initialContent.length)))
    }
    var isSaved by remember(displayedNoteId) { mutableStateOf(true) }
    var agentCommand by remember { mutableStateOf("") }
    var isAgentSectionExpanded by remember { mutableStateOf(false) }

    val mainContentFocusRequester = remember { FocusRequester() }
    var isMainContentFocused by remember { mutableStateOf(false) }

    // Key editor state on displayedNoteId and initialize with cached content to prevent flashing
    val editorState = remember(displayedNoteId) {
        EditorState().apply {
            if (initialContent.isNotEmpty()) {
                updateFromText(initialContent)
            }
        }
    }
    val controller = rememberEditorController(editorState)

    // Context and coroutine scope for undo state persistence
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Track current note ID for undo state persistence (observed from ViewModel)
    val currentNoteId by currentNoteViewModel.currentNoteIdLiveData.observeAsState()

    // Sync displayedNoteId with ViewModel's currentNoteId on initial load
    // This handles the case where displayedNoteId is null and ViewModel loads a default note
    LaunchedEffect(currentNoteId) {
        if (displayedNoteId == null && currentNoteId != null) {
            displayedNoteId = currentNoteId
        }
    }

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

    // Handle data loading when displayed note changes (either from navigation or tab click)
    LaunchedEffect(displayedNoteId) {
        currentNoteViewModel.loadContent(displayedNoteId, recentTabsViewModel)
    }

    // Update content when loaded from VM
    // Skip redundant updates if we already initialized with identical cached content
    LaunchedEffect(loadStatus) {
        if (loadStatus is LoadStatus.Success) {
            val loadedContent = (loadStatus as LoadStatus.Success).content
            val needsContentUpdate = loadedContent != userContent

            if (needsContentUpdate) {
                userContent = loadedContent
                textFieldValue = TextFieldValue(loadedContent, TextRange(loadedContent.length))
                // CRITICAL: Update editorState BEFORE setBaseline() below!
                // Without this line, editorState is empty when baseline is captured,
                // which means undo can restore to empty state and LOSE USER DATA.
                // See UndoManagerTest: "CRITICAL - baseline must contain actual content"
                editorState.updateFromText(loadedContent)
            }

            // Always set up undo state (needed even for cached content on initial load)
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

    // Load tabs on initial composition
    LaunchedEffect(Unit) {
        recentTabsViewModel.loadTabs()
    }

    // Update tab when note is loaded
    LaunchedEffect(loadStatus, currentNoteId) {
        val noteId = currentNoteId
        if (loadStatus is LoadStatus.Success && noteId != null) {
            val content = (loadStatus as LoadStatus.Success).content
            recentTabsViewModel.onNoteOpened(noteId, content)
        }
    }

    // React to content modification signal (e.g. from Agent)
    // Clear undo history since externally modified content would have stale snapshots
    // Invalidate cache since content changed externally
    LaunchedEffect(contentModified) {
        if (contentModified) {
            isSaved = false
            controller.resetUndoHistory()
            // Invalidate cache - AI modified content is not yet saved
            currentNoteId?.let { noteId ->
                recentTabsViewModel.invalidateCache(noteId)
            }
        }
    }

    // Handle save status changes
    LaunchedEffect(saveStatus) {
        if (saveStatus is SaveStatus.Success) {
            isSaved = true
            currentNoteViewModel.markAsSaved()
            currentNoteId?.let { noteId ->
                // Update tab display text after save
                recentTabsViewModel.updateTabDisplayText(noteId, userContent)
                // Update cache with latest tracked lines (includes new note IDs)
                val trackedLines = currentNoteViewModel.getTrackedLines()
                recentTabsViewModel.cacheNoteContent(noteId, trackedLines, isNoteDeleted)
            }
        }
    }

    // Remove tab when note is deleted
    LaunchedEffect(isNoteDeleted, currentNoteId) {
        val noteId = currentNoteId
        if (isNoteDeleted && noteId != null) {
            recentTabsViewModel.onNoteDeleted(noteId)
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

    tabsError?.let { error ->
        ErrorDialog(
            title = "Tabs Error",
            throwable = error.cause,
            onDismiss = { recentTabsViewModel.clearError() }
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

    val deletedNoteBackground = Color(0xFFF0F0F0)
    val deletedNoteTextColor = Color(0xFF666666)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isNoteDeleted) deletedNoteBackground else Color.White)
    ) {
        RecentTabsBar(
            tabs = recentTabs,
            currentNoteId = displayedNoteId ?: "",
            onTabClick = { targetNoteId ->
                // Save current note before switching (if needed)
                if (!isSaved && userContent.isNotEmpty()) {
                    currentNoteViewModel.saveContent(userContent)
                }
                // Switch tabs internally - no navigation, no screen recreation
                displayedNoteId = targetNoteId
            },
            onTabClose = { targetNoteId ->
                val isClosingCurrentTab = targetNoteId == displayedNoteId
                recentTabsViewModel.closeTab(targetNoteId)
                if (isClosingCurrentTab) {
                    // Find the next tab to switch to
                    val currentIndex = recentTabs.indexOfFirst { it.noteId == targetNoteId }
                    val remainingTabs = recentTabs.filter { it.noteId != targetNoteId }
                    if (remainingTabs.isEmpty()) {
                        onNavigateBack()
                    } else {
                        // Switch to next tab, or previous if we closed the last one
                        val nextIndex = minOf(currentIndex, remainingTabs.size - 1)
                        displayedNoteId = remainingTabs[nextIndex].noteId
                    }
                }
            }
        )

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
            },
            isDeleted = isNoteDeleted,
            onDeleteClick = {
                currentNoteViewModel.deleteCurrentNote(onSuccess = onNavigateBack)
            },
            onUndeleteClick = {
                currentNoteViewModel.undeleteCurrentNote(onSuccess = {})
            }
        )

        // Key on displayedNoteId to force full recreation of editor tree when switching tabs.
        // Without this, Compose reuses composables by position, causing stale state in
        // remember blocks (like interactionSource) that breaks touch handling.
        key(displayedNoteId) {
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
                textColor = if (isNoteDeleted) deletedNoteTextColor else Color.Black,
                modifier = Modifier.weight(1f)
            )
        }

        CommandBar(
            onToggleBullet = { controller.toggleBullet() },
            onToggleCheckbox = { controller.toggleCheckbox() },
            onIndent = { controller.indent() },
            onUnindent = { controller.unindent() },
            onMoveUp = {
                if (controller.moveUp()) {
                    userContent = editorState.text
                    isSaved = false
                }
            },
            onMoveDown = {
                if (controller.moveDown()) {
                    userContent = editorState.text
                    isSaved = false
                }
            },
            moveUpState = controller.getMoveUpState(),
            moveDownState = controller.getMoveDownState(),
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
