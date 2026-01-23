package org.alkaline.taskbrain.ui.currentnote

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.service.AlarmScheduleResult
import org.alkaline.taskbrain.service.AlarmScheduler
import org.alkaline.taskbrain.data.NoteLine
import org.alkaline.taskbrain.data.NoteLineTracker
import org.alkaline.taskbrain.data.NoteRepository
import org.alkaline.taskbrain.data.PrompterAgent
import org.alkaline.taskbrain.ui.currentnote.undo.AlarmSnapshot
import org.alkaline.taskbrain.util.PermissionHelper

class CurrentNoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository()
    private val alarmRepository = AlarmRepository()
    private val alarmScheduler = AlarmScheduler(application)
    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("taskbrain_prefs", Context.MODE_PRIVATE)
    private val agent = PrompterAgent()
    
    private val _saveStatus = MutableLiveData<SaveStatus>()
    val saveStatus: LiveData<SaveStatus> = _saveStatus

    private val _loadStatus = MutableLiveData<LoadStatus>()
    val loadStatus: LiveData<LoadStatus> = _loadStatus
    
    // Add a LiveData specifically to signal that content has been modified externally (e.g., by AI)
    private val _contentModified = MutableLiveData<Boolean>()
    val contentModified: LiveData<Boolean> = _contentModified
    
    private val _isAgentProcessing = MutableLiveData<Boolean>(false)
    val isAgentProcessing: LiveData<Boolean> = _isAgentProcessing

    // Alarm creation status - signals when to insert alarm symbol
    private val _alarmCreated = MutableLiveData<AlarmCreatedEvent?>()
    val alarmCreated: LiveData<AlarmCreatedEvent?> = _alarmCreated

    // Alarm error state
    private val _alarmError = MutableLiveData<Throwable?>()
    val alarmError: LiveData<Throwable?> = _alarmError

    // Alarm permission warning
    private val _alarmPermissionWarning = MutableLiveData<Boolean>(false)
    val alarmPermissionWarning: LiveData<Boolean> = _alarmPermissionWarning

    // Notification permission warning
    private val _notificationPermissionWarning = MutableLiveData<Boolean>(false)
    val notificationPermissionWarning: LiveData<Boolean> = _notificationPermissionWarning

    // Alarm undo/redo operation in progress - used to disable buttons during async operations
    private val _isAlarmOperationPending = MutableLiveData<Boolean>(false)
    val isAlarmOperationPending: LiveData<Boolean> = _isAlarmOperationPending

    // Warning shown when alarm redo fails and document is rolled back
    private val _redoRollbackWarning = MutableLiveData<RedoRollbackWarning?>()
    val redoRollbackWarning: LiveData<RedoRollbackWarning?> = _redoRollbackWarning

    // Whether the current note is deleted
    private val _isNoteDeleted = MutableLiveData<Boolean>(false)
    val isNoteDeleted: LiveData<Boolean> = _isNoteDeleted

    // Current Note ID being edited
    private var currentNoteId = "root_note"
    private val LAST_VIEWED_NOTE_KEY = "last_viewed_note_id"

    // Expose current note ID for UI (e.g., undo state persistence)
    private val _currentNoteIdLiveData = MutableLiveData<String>(currentNoteId)
    val currentNoteIdLiveData: LiveData<String> = _currentNoteIdLiveData

    /**
     * Gets the current note ID synchronously.
     */
    fun getCurrentNoteId(): String = currentNoteId

    // Track lines with their corresponding note IDs
    private var lineTracker = NoteLineTracker(currentNoteId)

    /**
     * Gets the current tracked lines for cache updates.
     * Returns a copy to prevent external modification.
     */
    fun getTrackedLines(): List<NoteLine> = lineTracker.getTrackedLines()

    fun loadContent(noteId: String? = null, recentTabsViewModel: RecentTabsViewModel? = null) {
        // If noteId is provided, use it. Otherwise, load from preferences. If neither, default to "root_note"
        currentNoteId = noteId ?: sharedPreferences.getString(LAST_VIEWED_NOTE_KEY, "root_note") ?: "root_note"
        _currentNoteIdLiveData.value = currentNoteId

        // Save the current note as the last viewed note
        sharedPreferences.edit().putString(LAST_VIEWED_NOTE_KEY, currentNoteId).apply()

        // Recreate line tracker with new parent note ID
        lineTracker = NoteLineTracker(currentNoteId)

        // Check cache first for instant tab switching
        val cached = recentTabsViewModel?.getCachedContent(currentNoteId)
        if (cached != null) {
            // Use cached content - instant!
            _isNoteDeleted.value = cached.isDeleted
            lineTracker.setTrackedLines(cached.noteLines)
            val fullContent = cached.noteLines.joinToString("\n") { it.content }
            _loadStatus.value = LoadStatus.Success(fullContent)
            // Still update lastAccessedAt in background
            viewModelScope.launch { repository.updateLastAccessed(currentNoteId) }
            return
        }

        // Cache miss - fetch from Firebase
        _loadStatus.value = LoadStatus.Loading

        viewModelScope.launch {
            // Check if note is deleted
            repository.isNoteDeleted(currentNoteId).fold(
                onSuccess = { isDeleted -> _isNoteDeleted.value = isDeleted },
                onFailure = { _isNoteDeleted.value = false }
            )

            // Update lastAccessedAt (fire-and-forget, doesn't block loading)
            launch { repository.updateLastAccessed(currentNoteId) }

            val result = repository.loadNoteWithChildren(currentNoteId)
            result.fold(
                onSuccess = { loadedLines ->
                    lineTracker.setTrackedLines(loadedLines)
                    val fullContent = loadedLines.joinToString("\n") { it.content }
                    _loadStatus.value = LoadStatus.Success(fullContent)

                    // Cache the loaded content for future tab switches
                    recentTabsViewModel?.cacheNoteContent(
                        currentNoteId,
                        loadedLines,
                        _isNoteDeleted.value ?: false
                    )
                },
                onFailure = { e ->
                    Log.e("CurrentNoteViewModel", "Error loading note", e)
                    _loadStatus.value = LoadStatus.Error(e)
                }
            )
        }
    }

    /**
     * Updates the tracked lines based on the new content provided by the user.
     * It uses a heuristic to match lines and preserve Note IDs across edits, insertions, and deletions.
     */
    fun updateTrackedLines(newContent: String) {
        lineTracker.updateTrackedLines(newContent)
    }

    fun saveContent(content: String) {
        // Update tracked lines before saving to ensure we have the latest state mapping
        updateTrackedLines(content)

        _saveStatus.value = SaveStatus.Saving

        viewModelScope.launch {
            val trackedLines = lineTracker.getTrackedLines()
            val result = repository.saveNoteWithChildren(currentNoteId, trackedLines)
            
            result.fold(
                onSuccess = { newIdsMap ->
                    // Update trackedLines with the newly created IDs so subsequent saves are correct
                    for ((index, newId) in newIdsMap) {
                        lineTracker.updateLineNoteId(index, newId)
                    }

                    // Sync alarm line content with updated note content
                    syncAlarmLineContent(trackedLines)

                    Log.d("CurrentNoteViewModel", "Note saved successfully with structure.")
                    _saveStatus.value = SaveStatus.Success
                    markAsSaved()
                },
                onFailure = { e ->
                    Log.e("CurrentNoteViewModel", "Error saving note", e)
                    _saveStatus.value = SaveStatus.Error(e)
                }
            )
        }
    }

    fun processAgentCommand(currentContent: String, command: String) {
        _isAgentProcessing.value = true
        viewModelScope.launch {
            try {
                val updatedContent = agent.processCommand(currentContent, command)
                // Update the UI with the new content
                _loadStatus.value = LoadStatus.Success(updatedContent)
                
                // Also update tracked lines since content changed externally
                updateTrackedLines(updatedContent)
                
                // Signal that the content has been modified and is unsaved
                _contentModified.value = true
            } catch (e: Exception) {
                Log.e("CurrentNoteViewModel", "Agent processing failed", e)
                _loadStatus.value = LoadStatus.Error(e)
            } finally {
                _isAgentProcessing.value = false
            }
        }
    }

    /**
     * Syncs alarm line content with the current note content.
     * Called after saving to keep alarm display text up to date.
     */
    private fun syncAlarmLineContent(trackedLines: List<org.alkaline.taskbrain.data.NoteLine>) {
        viewModelScope.launch {
            for (line in trackedLines) {
                line.noteId?.let { noteId ->
                    alarmRepository.updateLineContentForNote(noteId, line.content)
                }
            }
        }
    }

    /**
     * Gets the note ID for a given line index.
     * Returns the parent note ID if the line doesn't have an associated note.
     */
    fun getNoteIdForLine(lineIndex: Int): String {
        val lines = lineTracker.getTrackedLines()
        return if (lineIndex < lines.size) {
            lines[lineIndex].noteId ?: currentNoteId
        } else {
            currentNoteId
        }
    }

    /**
     * Fetches alarms for a specific line by its note ID.
     */
    private val _lineAlarms = MutableLiveData<List<Alarm>>()
    val lineAlarms: LiveData<List<Alarm>> = _lineAlarms

    fun fetchAlarmsForLine(lineIndex: Int) {
        val noteId = getNoteIdForLine(lineIndex)
        viewModelScope.launch {
            val result = alarmRepository.getAlarmsForNote(noteId)
            result.fold(
                onSuccess = { alarms ->
                    _lineAlarms.value = alarms.filter { it.status == org.alkaline.taskbrain.data.AlarmStatus.PENDING }
                },
                onFailure = { e ->
                    Log.e("CurrentNoteViewModel", "Error fetching alarms for line", e)
                    _lineAlarms.value = emptyList()
                }
            )
        }
    }

    // Scheduling failure warning
    private val _schedulingWarning = MutableLiveData<String?>()
    val schedulingWarning: LiveData<String?> = _schedulingWarning

    fun clearSchedulingWarning() {
        _schedulingWarning.value = null
    }

    /**
     * Saves content if needed, then creates a new alarm for the current line.
     * This ensures the line tracker has correct note IDs before alarm creation.
     */
    fun saveAndCreateAlarm(
        content: String,
        lineContent: String,
        lineIndex: Int? = null,
        upcomingTime: Timestamp?,
        notifyTime: Timestamp?,
        urgentTime: Timestamp?,
        alarmTime: Timestamp?
    ) {
        viewModelScope.launch {
            // First, save the content to ensure line tracker has correct note IDs
            updateTrackedLines(content)
            val trackedLines = lineTracker.getTrackedLines()
            val saveResult = repository.saveNoteWithChildren(currentNoteId, trackedLines)

            saveResult.fold(
                onSuccess = { newIdsMap ->
                    // Update trackedLines with newly created IDs
                    for ((index, newId) in newIdsMap) {
                        lineTracker.updateLineNoteId(index, newId)
                    }
                    syncAlarmLineContent(trackedLines)
                    _saveStatus.value = SaveStatus.Success
                    markAsSaved()

                    // Now create the alarm with correct note ID
                    createAlarmInternal(lineContent, lineIndex, upcomingTime, notifyTime, urgentTime, alarmTime)
                },
                onFailure = { e ->
                    _saveStatus.value = SaveStatus.Error(e)
                }
            )
        }
    }

    /**
     * Creates a new alarm for the current line.
     * Uses the line tracker to get the note ID for the current line.
     */
    fun createAlarm(
        lineContent: String,
        lineIndex: Int? = null,
        upcomingTime: Timestamp?,
        notifyTime: Timestamp?,
        urgentTime: Timestamp?,
        alarmTime: Timestamp?
    ) {
        viewModelScope.launch {
            createAlarmInternal(lineContent, lineIndex, upcomingTime, notifyTime, urgentTime, alarmTime)
        }
    }

    private suspend fun createAlarmInternal(
        lineContent: String,
        lineIndex: Int?,
        upcomingTime: Timestamp?,
        notifyTime: Timestamp?,
        urgentTime: Timestamp?,
        alarmTime: Timestamp?
    ) {
        // Use the note ID from line tracker if available
        val noteId = if (lineIndex != null) getNoteIdForLine(lineIndex) else currentNoteId

        // Auto-populate upcomingTime with the earliest scheduled time if not set
        val effectiveUpcomingTime = upcomingTime ?: listOfNotNull(notifyTime, urgentTime, alarmTime)
            .minByOrNull { it.toDate().time }

        val alarm = Alarm(
            noteId = noteId,
            lineContent = lineContent,
            upcomingTime = effectiveUpcomingTime,
            notifyTime = notifyTime,
            urgentTime = urgentTime,
            alarmTime = alarmTime
        )

        val result = alarmRepository.createAlarm(alarm)
        result.fold(
            onSuccess = { alarmId ->
                // Check permissions and show warnings
                val context = getApplication<Application>()
                if (!alarmScheduler.canScheduleExactAlarms()) {
                    _alarmPermissionWarning.value = true
                }
                if (!PermissionHelper.hasNotificationPermission(context)) {
                    _notificationPermissionWarning.value = true
                }

                // Schedule the alarm
                val createdAlarm = alarm.copy(id = alarmId)
                val scheduleResult = alarmScheduler.scheduleAlarm(createdAlarm)

                // Handle schedule result
                if (!scheduleResult.success) {
                    _schedulingWarning.value = scheduleResult.message
                }

                // Create alarm snapshot for undo/redo
                val alarmSnapshot = AlarmSnapshot(
                    id = alarmId,
                    noteId = alarm.noteId,
                    lineContent = alarm.lineContent,
                    upcomingTime = alarm.upcomingTime,
                    notifyTime = alarm.notifyTime,
                    urgentTime = alarm.urgentTime,
                    alarmTime = alarm.alarmTime
                )

                // Signal to insert alarm symbol (even if scheduling partially failed,
                // the alarm exists in the DB)
                _alarmCreated.value = AlarmCreatedEvent(alarmId, lineContent, alarmSnapshot)
            },
            onFailure = { e ->
                _alarmError.value = e
            }
        )
    }

    companion object {
        private const val TAG = "CurrentNoteViewModel"
    }

    /**
     * Clears the alarm created event after it has been handled.
     */
    fun clearAlarmCreatedEvent() {
        _alarmCreated.value = null
    }

    /**
     * Deletes an alarm permanently (for undo operation).
     * This completely removes the alarm from Firestore and cancels any scheduled notifications.
     * Sets isAlarmOperationPending during the operation to prevent race conditions.
     */
    fun deleteAlarmPermanently(alarmId: String, onComplete: (() -> Unit)? = null) {
        _isAlarmOperationPending.value = true
        viewModelScope.launch {
            try {
                alarmScheduler.cancelAlarm(alarmId)
                alarmRepository.deleteAlarm(alarmId)
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e("CurrentNoteViewModel", "Error deleting alarm: $alarmId", e)
                _alarmError.value = e
            } finally {
                _isAlarmOperationPending.value = false
            }
        }
    }

    /**
     * Recreates an alarm with the same configuration (for redo operation).
     * The alarm will get a new ID. Calls onAlarmCreated with the new ID.
     * Sets isAlarmOperationPending during the operation to prevent race conditions.
     * Calls onFailure with the error message if alarm creation fails (e.g., to clean up the alarm symbol).
     */
    fun recreateAlarm(
        alarmSnapshot: AlarmSnapshot,
        onAlarmCreated: (String) -> Unit,
        onFailure: ((String) -> Unit)? = null
    ) {
        _isAlarmOperationPending.value = true
        viewModelScope.launch {
            val alarm = Alarm(
                noteId = alarmSnapshot.noteId,
                lineContent = alarmSnapshot.lineContent,
                upcomingTime = alarmSnapshot.upcomingTime,
                notifyTime = alarmSnapshot.notifyTime,
                urgentTime = alarmSnapshot.urgentTime,
                alarmTime = alarmSnapshot.alarmTime
            )

            val result = alarmRepository.createAlarm(alarm)
            result.fold(
                onSuccess = { newAlarmId ->
                    // Schedule the recreated alarm
                    val createdAlarm = alarm.copy(id = newAlarmId)
                    alarmScheduler.scheduleAlarm(createdAlarm)
                    // Notify caller of new ID for future undo/redo cycles
                    onAlarmCreated(newAlarmId)
                },
                onFailure = { e ->
                    // Pass error message to callback instead of showing generic error dialog
                    // The screen will show a specific redo rollback warning
                    onFailure?.invoke(e.message ?: "Unknown error")
                }
            )
            _isAlarmOperationPending.value = false
        }
    }

    /**
     * Clears the alarm error after it has been shown.
     */
    fun clearAlarmError() {
        _alarmError.value = null
    }

    /**
     * Clears the alarm permission warning after it has been shown.
     */
    fun clearAlarmPermissionWarning() {
        _alarmPermissionWarning.value = false
    }

    /**
     * Clears the notification permission warning after it has been shown.
     */
    fun clearNotificationPermissionWarning() {
        _notificationPermissionWarning.value = false
    }

    /**
     * Shows a warning that alarm recreation failed during redo and the document was rolled back.
     */
    fun showRedoRollbackWarning(rollbackSucceeded: Boolean, errorMessage: String) {
        _redoRollbackWarning.value = RedoRollbackWarning(rollbackSucceeded, errorMessage)
    }

    /**
     * Clears the redo rollback warning after it has been shown.
     */
    fun clearRedoRollbackWarning() {
        _redoRollbackWarning.value = null
    }

    // Call this when content is manually edited or when a save completes
    fun markAsSaved() {
        _contentModified.value = false
    }

    fun clearSaveError() {
        if (_saveStatus.value is SaveStatus.Error) {
            _saveStatus.value = null
        }
    }

    fun clearLoadError() {
        if (_loadStatus.value is LoadStatus.Error) {
            _loadStatus.value = null
        }
    }

    /**
     * Soft-deletes the current note.
     */
    fun deleteCurrentNote(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.softDeleteNote(currentNoteId)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Note deleted successfully: $currentNoteId")
                    _isNoteDeleted.value = true
                    onSuccess()
                },
                onFailure = { e ->
                    Log.e(TAG, "Error deleting note", e)
                    _saveStatus.value = SaveStatus.Error(e)
                }
            )
        }
    }

    /**
     * Restores the current note from deleted state.
     */
    fun undeleteCurrentNote(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.undeleteNote(currentNoteId)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Note restored successfully: $currentNoteId")
                    _isNoteDeleted.value = false
                    onSuccess()
                },
                onFailure = { e ->
                    Log.e(TAG, "Error restoring note", e)
                    _saveStatus.value = SaveStatus.Error(e)
                }
            )
        }
    }
}

sealed class SaveStatus {
    object Saving : SaveStatus()
    object Success : SaveStatus()
    data class Error(val throwable: Throwable) : SaveStatus()
}

sealed class LoadStatus {
    object Loading : LoadStatus()
    data class Success(val content: String) : LoadStatus()
    data class Error(val throwable: Throwable) : LoadStatus()
}

/**
 * Event emitted when an alarm is successfully created.
 */
data class AlarmCreatedEvent(
    val alarmId: String,
    val lineContent: String,
    val alarmSnapshot: AlarmSnapshot? = null
)

/**
 * Warning shown when alarm recreation fails during redo and the document is rolled back.
 * @param rollbackSucceeded true if the cleanup undo succeeded, false if document may be inconsistent
 * @param errorMessage the underlying error message from the alarm creation failure
 */
data class RedoRollbackWarning(
    val rollbackSucceeded: Boolean,
    val errorMessage: String
)
