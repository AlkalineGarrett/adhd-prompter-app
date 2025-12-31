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
import kotlinx.coroutines.launch
import org.alkaline.taskbrain.data.PrompterAgent

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

        db.collection("notes").document(currentNoteId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    Log.d("CurrentNoteViewModel", "DocumentSnapshot data: ${document.data}")
                    val content = document.getString("content")
                    if (content != null) {
                        _loadStatus.value = LoadStatus.Success(content)
                    } else {
                        // Document exists but content is null or missing
                        _loadStatus.value = LoadStatus.Success("") 
                    }
                } else {
                    Log.d("CurrentNoteViewModel", "No such document")
                    // Document doesn't exist yet (new user/note), treat as empty
                     _loadStatus.value = LoadStatus.Success("")
                }
            }
            .addOnFailureListener { exception ->
                Log.d("CurrentNoteViewModel", "get failed with ", exception)
                _loadStatus.value = LoadStatus.Error(exception.message ?: "Unknown error")
            }
    }

    fun saveContent(content: String) {
        val user = auth.currentUser
        if (user == null) {
            _saveStatus.value = SaveStatus.Error("User not signed in")
            return
        }

        _saveStatus.value = SaveStatus.Saving

        val noteData = hashMapOf(
            "content" to content,
            "updatedAt" to FieldValue.serverTimestamp(),
            "userId" to user.uid
        )

        db.collection("notes").document(currentNoteId)
            .set(noteData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("CurrentNoteViewModel", "DocumentSnapshot successfully written!")
                _saveStatus.value = SaveStatus.Success
            }
            .addOnFailureListener { e ->
                Log.w("CurrentNoteViewModel", "Error writing document", e)
                _saveStatus.value = SaveStatus.Error(e.message ?: "Unknown error")
            }
    }

    fun processAgentCommand(currentContent: String, command: String) {
        _isAgentProcessing.value = true
        viewModelScope.launch {
            try {
                val updatedContent = agent.processCommand(currentContent, command)
                // Update the UI with the new content
                _loadStatus.value = LoadStatus.Success(updatedContent)
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
