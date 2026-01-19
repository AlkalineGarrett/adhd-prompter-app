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
import org.alkaline.taskbrain.data.NoteLine
import org.alkaline.taskbrain.data.NoteLineTracker
import org.alkaline.taskbrain.data.NoteRepository
import org.alkaline.taskbrain.data.PrompterAgent

class CurrentNoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository()
    private val alarmRepository = AlarmRepository()
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
     * Creates a new alarm for the current line.
     * Uses the line tracker to get the note ID for the current line.
     */
    fun createAlarm(
        lineContent: String,
        upcomingTime: Timestamp?,
        notifyTime: Timestamp?,
        urgentTime: Timestamp?,
        alarmTime: Timestamp?
    ) {
        viewModelScope.launch {
            // For now, we'll use a placeholder note ID
            // TODO: Get the actual note ID from line tracker based on line content position
            val alarm = Alarm(
                noteId = currentNoteId,
                lineContent = lineContent,
                upcomingTime = upcomingTime,
                notifyTime = notifyTime,
                urgentTime = urgentTime,
                alarmTime = alarmTime
            )

            val result = alarmRepository.createAlarm(alarm)
            result.fold(
                onSuccess = { alarmId ->
                    Log.d("CurrentNoteViewModel", "Alarm created with ID: $alarmId")
                    // TODO: Schedule the alarm with AlarmScheduler
                    // TODO: Insert alarm symbol into text
                },
                onFailure = { e ->
                    Log.e("CurrentNoteViewModel", "Error creating alarm", e)
                }
            )
        }
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
