package org.alkaline.taskbrain.ui.currentnote

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.data.PrompterAgent

data class NoteLine(
    val content: String,
    val noteId: String? = null // Null if it's a new line or hasn't been persisted yet
)

class CurrentNoteViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
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
    private var trackedLines = listOf<NoteLine>()

    fun loadContent(noteId: String? = null) {
        // If noteId is provided, use it. Otherwise, load from preferences. If neither, default to "root_note"
        currentNoteId = noteId ?: sharedPreferences.getString(LAST_VIEWED_NOTE_KEY, "root_note") ?: "root_note"
        
        // Save the current note as the last viewed note
        sharedPreferences.edit().putString(LAST_VIEWED_NOTE_KEY, currentNoteId).apply()
        
        val user = auth.currentUser
        if (user == null) {
            _loadStatus.value = LoadStatus.Error("User not signed in")
            return
        }

        _loadStatus.value = LoadStatus.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val document = db.collection("notes").document(currentNoteId).get().await()
                if (document != null && document.exists()) {
                    val note = document.toObject(Note::class.java)
                    if (note != null) {
                        val loadedLines = mutableListOf<NoteLine>()
                        // First line is the parent note itself
                        loadedLines.add(NoteLine(note.content, currentNoteId))

                        if (note.containedNotes.isNotEmpty()) {
                            val childDeferreds = note.containedNotes.map { childId ->
                                async {
                                    if (childId.isEmpty()) {
                                        NoteLine("", null) // Empty line, no ID yet
                                    } else {
                                        try {
                                            val childDoc = db.collection("notes").document(childId).get().await()
                                            if (childDoc.exists()) {
                                                 val content = childDoc.toObject(Note::class.java)?.content ?: ""
                                                 NoteLine(content, childId)
                                            } else {
                                                NoteLine("", null) // Child doc missing, treat as empty new line
                                            }
                                        } catch (e: Exception) {
                                            Log.e("CurrentNoteViewModel", "Error fetching child note $childId", e)
                                            NoteLine("", null)
                                        }
                                    }
                                }
                            }
                            
                            val childLines = childDeferreds.awaitAll()
                            loadedLines.addAll(childLines)
                        }
                        
                        trackedLines = loadedLines
                        val fullContent = loadedLines.joinToString("\n") { it.content }
                        
                        _loadStatus.postValue(LoadStatus.Success(fullContent))
                    } else {
                        trackedLines = listOf(NoteLine("", currentNoteId))
                        _loadStatus.postValue(LoadStatus.Success(""))
                    }
                } else {
                    // Document doesn't exist yet (new user/note), treat as empty
                    trackedLines = listOf(NoteLine("", currentNoteId))
                    _loadStatus.postValue(LoadStatus.Success(""))
                }
            } catch (e: Exception) {
                Log.e("CurrentNoteViewModel", "Error loading note", e)
                _loadStatus.postValue(LoadStatus.Error(e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Updates the tracked lines based on the new content provided by the user.
     * It attempts to match new lines with existing note IDs using a simple diff heuristic.
     */
    fun updateTrackedLines(newContent: String) {
        val newLinesContent = newContent.lines()
        val oldLines = trackedLines
        val newTrackedLines = mutableListOf<NoteLine>()
        
        // This is a simplified diffing strategy. 
        // A more robust one would use Myer's diff algorithm or similar.
        // Here we try to map lines greedily.
        
        var oldIndex = 0
        for (newLineContent in newLinesContent) {
            // Try to find a match in the remaining old lines
            var foundMatchIndex = -1
            
            // 1. Exact match search forward
            for (i in oldIndex until oldLines.size) {
                if (oldLines[i].content == newLineContent) {
                    foundMatchIndex = i
                    break
                }
            }
            
            if (foundMatchIndex != -1) {
                // Found an exact match, consume it and all preceding skipped lines are effectively deleted/replaced
                newTrackedLines.add(oldLines[foundMatchIndex])
                oldIndex = foundMatchIndex + 1
            } else {
                // No exact match. 
                // Check if the current old line is "similar" enough to consider it an edit?
                // For simplicity now, if we are at the same index, we assume it's an edit of that line
                // unless we ran out of old lines.
                if (oldIndex < oldLines.size) {
                    // Assume the current line was edited
                    // Special case: The first line always corresponds to the parent note ID (currentNoteId)
                    val noteId = if (newTrackedLines.isEmpty()) currentNoteId else oldLines[oldIndex].noteId
                    newTrackedLines.add(NoteLine(newLineContent, noteId))
                    oldIndex++
                } else {
                    // New line added at the end
                    newTrackedLines.add(NoteLine(newLineContent, null))
                }
            }
        }
        
        // Ensure first line has the parent ID
        if (newTrackedLines.isNotEmpty() && newTrackedLines[0].noteId != currentNoteId) {
             newTrackedLines[0] = newTrackedLines[0].copy(noteId = currentNoteId)
        }
        
        trackedLines = newTrackedLines
    }

    fun saveContent(content: String) {
        // Update tracked lines before saving to ensure we have the latest state mapping
        updateTrackedLines(content)

        val user = auth.currentUser
        if (user == null) {
            _saveStatus.value = SaveStatus.Error("User not signed in")
            return
        }

        _saveStatus.value = SaveStatus.Saving

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parentRef = db.collection("notes").document(currentNoteId)
                
                // We use a transaction to ensure atomic updates of parent and child notes
                val newIdsMap = db.runTransaction { transaction ->
                    val parentSnapshot = transaction.get(parentRef)
                    val oldContainedNotes = if (parentSnapshot.exists()) {
                        parentSnapshot.get("containedNotes") as? List<String> ?: emptyList()
                    } else {
                        emptyList()
                    }
                    
                    // Identify IDs to delete: start with all old non-empty IDs
                    // We will remove IDs from this set as we encounter them in the new content
                    val idsToDelete = oldContainedNotes.filter { it.isNotEmpty() }.toMutableSet()
                    val newContainedNotes = mutableListOf<String>()
                    val createdIdsMap = mutableMapOf<Int, String>()
                    
                    // 1. Update Parent Note Content (First Line)
                    val parentContent = trackedLines.firstOrNull()?.content ?: ""
                    val parentUpdateData = hashMapOf<String, Any>(
                        "content" to parentContent,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "userId" to user.uid
                    )
                    
                    // 2. Process Child Lines (starting from index 1)
                    if (trackedLines.size > 1) {
                        for (i in 1 until trackedLines.size) {
                            val line = trackedLines[i]
                            
                            if (line.content.isNotEmpty()) {
                                if (line.noteId != null) {
                                    // Existing child note: Update content
                                    val childRef = db.collection("notes").document(line.noteId)
                                    transaction.set(childRef, mapOf(
                                        "content" to line.content,
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    ), SetOptions.merge())
                                    
                                    newContainedNotes.add(line.noteId)
                                    idsToDelete.remove(line.noteId)
                                } else {
                                    // New child note: Create
                                    val newChildRef = db.collection("notes").document()
                                    val newNoteData = hashMapOf(
                                        "userId" to user.uid,
                                        "content" to line.content,
                                        "createdAt" to FieldValue.serverTimestamp(),
                                        "updatedAt" to FieldValue.serverTimestamp(),
                                        "parentNoteId" to currentNoteId
                                    )
                                    transaction.set(newChildRef, newNoteData)
                                    
                                    newContainedNotes.add(newChildRef.id)
                                    createdIdsMap[i] = newChildRef.id
                                }
                            } else {
                                // Empty line: Represents a gap/spacer
                                newContainedNotes.add("")
                            }
                        }
                    }
                    
                    // 3. Update Parent with new structure
                    parentUpdateData["containedNotes"] = newContainedNotes
                    transaction.set(parentRef, parentUpdateData, SetOptions.merge())
                    
                    // 4. Soft delete removed notes
                    for (idToDelete in idsToDelete) {
                        val docRef = db.collection("notes").document(idToDelete)
                        transaction.update(docRef, mapOf(
                            "state" to "deleted",
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    
                    return@runTransaction createdIdsMap
                }.await()

                // Update trackedLines with the newly created IDs so subsequent saves are correct
                val currentList = trackedLines.toMutableList()
                for ((index, newId) in newIdsMap) {
                    if (index < currentList.size) {
                        currentList[index] = currentList[index].copy(noteId = newId)
                    }
                }
                trackedLines = currentList

                Log.d("CurrentNoteViewModel", "Note saved successfully with structure.")
                _saveStatus.postValue(SaveStatus.Success)
                markAsSaved()
                
            } catch (e: Exception) {
                Log.e("CurrentNoteViewModel", "Error saving note", e)
                _saveStatus.postValue(SaveStatus.Error(e.message ?: "Unknown error"))
            }
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
                _loadStatus.value = LoadStatus.Error("Agent failed: ${e.message}")
            } finally {
                _isAgentProcessing.value = false
            }
        }
    }
    
    // Call this when content is manually edited or when a save completes
    fun markAsSaved() {
        _contentModified.value = false
    }
}

sealed class SaveStatus {
    object Saving : SaveStatus()
    object Success : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

sealed class LoadStatus {
    object Loading : LoadStatus()
    data class Success(val content: String) : LoadStatus()
    data class Error(val message: String) : LoadStatus()
}
