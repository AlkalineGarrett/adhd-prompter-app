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
import org.alkaline.taskbrain.service.AlarmScheduler
import org.alkaline.taskbrain.data.NoteLine
import org.alkaline.taskbrain.data.NoteLineTracker
import org.alkaline.taskbrain.data.NoteRepository
import org.alkaline.taskbrain.data.PrompterAgent
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

    // Current Note ID being edited
    private var currentNoteId = "root_note"
    private val LAST_VIEWED_NOTE_KEY = "last_viewed_note_id"

    // Track lines with their corresponding note IDs
    private var lineTracker = NoteLineTracker(currentNoteId)

    fun loadContent(noteId: String? = null) {
        // If noteId is provided, use it. Otherwise, load from preferences. If neither, default to "root_note"
        currentNoteId = noteId ?: sharedPreferences.getString(LAST_VIEWED_NOTE_KEY, "root_note") ?: "root_note"
        
        // Save the current note as the last viewed note
        sharedPreferences.edit().putString(LAST_VIEWED_NOTE_KEY, currentNoteId).apply()
        
        // Recreate line tracker with new parent note ID
        lineTracker = NoteLineTracker(currentNoteId)
        
        _loadStatus.value = LoadStatus.Loading

        viewModelScope.launch {
            val result = repository.loadNoteWithChildren(currentNoteId)
            result.fold(
                onSuccess = { loadedLines ->
                    lineTracker.setTrackedLines(loadedLines)
                    val fullContent = loadedLines.joinToString("\n") { it.content }
                    _loadStatus.value = LoadStatus.Success(fullContent)
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
                    Log.d("CurrentNoteViewModel", "Alarm created with ID: $alarmId")

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
                    alarmScheduler.scheduleAlarm(createdAlarm)
                    Log.d("CurrentNoteViewModel", "Alarm scheduled: $alarmId")

                    // Signal to insert alarm symbol
                    _alarmCreated.value = AlarmCreatedEvent(alarmId, lineContent)
                },
                onFailure = { e ->
                    Log.e("CurrentNoteViewModel", "Error creating alarm", e)
                    _alarmError.value = e
                }
            )
        }
    }

    /**
     * Clears the alarm created event after it has been handled.
     */
    fun clearAlarmCreatedEvent() {
        _alarmCreated.value = null
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
    val lineContent: String
)
