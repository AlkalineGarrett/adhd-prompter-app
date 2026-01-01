package org.alkaline.taskbrain.ui.notelist

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.alkaline.taskbrain.data.Note

class NoteListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _notes = MutableLiveData<List<Note>>()
    val notes: LiveData<List<Note>> = _notes

    private val _loadStatus = MutableLiveData<LoadStatus>()
    val loadStatus: LiveData<LoadStatus> = _loadStatus

    private val _createNoteStatus = MutableLiveData<CreateNoteStatus>()
    val createNoteStatus: LiveData<CreateNoteStatus> = _createNoteStatus

    fun loadNotes() {
        val user = auth.currentUser
        if (user == null) {
            _loadStatus.value = LoadStatus.Error("User not signed in")
            return
        }

        _loadStatus.value = LoadStatus.Loading

        db.collection("notes")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { result ->
                val notesList = mutableListOf<Note>()
                for (document in result) {
                    try {
                        val note = document.toObject(Note::class.java).copy(id = document.id)
                        // Filter out child notes (parentNoteId != null) and deleted notes
                        if (note.parentNoteId == null && note.state != "deleted") {
                            notesList.add(note)
                        }
                    } catch (e: Exception) {
                        Log.e("NoteListViewModel", "Error parsing note", e)
                    }
                }
                // Sort by updatedAt descending
                notesList.sortByDescending { it.updatedAt }
                
                _notes.value = notesList
                _loadStatus.value = LoadStatus.Success
            }
            .addOnFailureListener { exception ->
                Log.d("NoteListViewModel", "Error getting documents: ", exception)
                _loadStatus.value = LoadStatus.Error(exception.message ?: "Unknown error")
            }
    }

    fun createNote(onSuccess: (String) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            _createNoteStatus.value = CreateNoteStatus.Error("User not signed in")
            return
        }

        _createNoteStatus.value = CreateNoteStatus.Loading

        val newNote = hashMapOf(
            "userId" to user.uid,
            "content" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "parentNoteId" to null
        )

        db.collection("notes")
            .add(newNote)
            .addOnSuccessListener { documentReference ->
                Log.d("NoteListViewModel", "Note created with ID: ${documentReference.id}")
                _createNoteStatus.value = CreateNoteStatus.Success(documentReference.id)
                onSuccess(documentReference.id)
            }
            .addOnFailureListener { e ->
                Log.w("NoteListViewModel", "Error adding document", e)
                _createNoteStatus.value = CreateNoteStatus.Error(e.message ?: "Unknown error")
            }
    }

    fun saveNewNote(content: String, onSuccess: (String) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            _createNoteStatus.value = CreateNoteStatus.Error("User not signed in")
            return
        }

        _createNoteStatus.value = CreateNoteStatus.Loading

        val lines = content.lines()
        val firstLine = lines.firstOrNull() ?: ""
        val childLines = if (lines.size > 1) lines.subList(1, lines.size) else emptyList()

        val batch = db.batch()
        val parentNoteRef = db.collection("notes").document()
        val parentNoteId = parentNoteRef.id

        val childNoteIds = mutableListOf<String>()
        if (childLines.isNotEmpty()) {
            for (childLine in childLines) {
                if (childLine.isNotBlank()) {
                    val childNoteRef = db.collection("notes").document()
                    childNoteIds.add(childNoteRef.id)
                    val newChildNote = hashMapOf(
                        "userId" to user.uid,
                        "content" to childLine,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "parentNoteId" to parentNoteId
                    )
                    batch.set(childNoteRef, newChildNote)
                } else {
                    childNoteIds.add("")
                }
            }
        }

        val newParentNote = hashMapOf(
            "userId" to user.uid,
            "content" to firstLine,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "containedNotes" to childNoteIds,
            "parentNoteId" to null
        )
        batch.set(parentNoteRef, newParentNote)

        batch.commit()
            .addOnSuccessListener {
                Log.d("NoteListViewModel", "Multi-line note created with ID: $parentNoteId")
                _createNoteStatus.value = CreateNoteStatus.Success(parentNoteId)
                onSuccess(parentNoteId)
            }
            .addOnFailureListener { e ->
                Log.w("NoteListViewModel", "Error creating multi-line note", e)
                _createNoteStatus.value = CreateNoteStatus.Error(e.message ?: "Unknown error")
            }
    }
}

sealed class LoadStatus {
    object Loading : LoadStatus()
    object Success : LoadStatus()
    data class Error(val message: String) : LoadStatus()
}

sealed class CreateNoteStatus {
    object Loading : CreateNoteStatus()
    data class Success(val noteId: String) : CreateNoteStatus()
    data class Error(val message: String) : CreateNoteStatus()
}
