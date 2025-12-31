package org.alkaline.taskbrain.ui.notelist

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.alkaline.taskbrain.data.Note

class NoteListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _notes = MutableLiveData<List<Note>>()
    val notes: LiveData<List<Note>> = _notes

    private val _loadStatus = MutableLiveData<LoadStatus>()
    val loadStatus: LiveData<LoadStatus> = _loadStatus

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
                        notesList.add(note)
                    } catch (e: Exception) {
                        Log.e("NoteListViewModel", "Error parsing note", e)
                    }
                }
                _notes.value = notesList
                _loadStatus.value = LoadStatus.Success
            }
            .addOnFailureListener { exception ->
                Log.d("NoteListViewModel", "Error getting documents: ", exception)
                _loadStatus.value = LoadStatus.Error(exception.message ?: "Unknown error")
            }
    }
}

sealed class LoadStatus {
    object Loading : LoadStatus()
    object Success : LoadStatus()
    data class Error(val message: String) : LoadStatus()
}
