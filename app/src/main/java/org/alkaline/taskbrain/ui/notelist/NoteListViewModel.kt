package org.alkaline.taskbrain.ui.notelist

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.data.NoteFilteringUtils
import org.alkaline.taskbrain.data.NoteRepository

class NoteListViewModel : ViewModel() {

    private val repository = NoteRepository()
    
    private val _notes = MutableLiveData<List<Note>>()
    val notes: LiveData<List<Note>> = _notes

    private val _loadStatus = MutableLiveData<LoadStatus>()
    val loadStatus: LiveData<LoadStatus> = _loadStatus

    private val _createNoteStatus = MutableLiveData<CreateNoteStatus>()
    val createNoteStatus: LiveData<CreateNoteStatus> = _createNoteStatus

    fun loadNotes() {
        _loadStatus.value = LoadStatus.Loading

        viewModelScope.launch {
            val result = repository.loadUserNotes()
            result.fold(
                onSuccess = { notesList ->
                    val filteredAndSorted = NoteFilteringUtils.filterAndSortNotes(notesList)
                    _notes.value = filteredAndSorted
                    _loadStatus.value = LoadStatus.Success
                },
                onFailure = { exception ->
                    Log.d("NoteListViewModel", "Error getting documents: ", exception)
                    _loadStatus.value = LoadStatus.Error(exception)
                }
            )
        }
    }

    fun createNote(onSuccess: (String) -> Unit) {
        _createNoteStatus.value = CreateNoteStatus.Loading

        viewModelScope.launch {
            val result = repository.createNote()
            result.fold(
                onSuccess = { noteId ->
                    Log.d("NoteListViewModel", "Note created with ID: $noteId")
                    _createNoteStatus.value = CreateNoteStatus.Success(noteId)
                    onSuccess(noteId)
                },
                onFailure = { e ->
                    Log.w("NoteListViewModel", "Error adding document", e)
                    _createNoteStatus.value = CreateNoteStatus.Error(e)
                }
            )
        }
    }

    fun saveNewNote(content: String, onSuccess: (String) -> Unit) {
        _createNoteStatus.value = CreateNoteStatus.Loading

        viewModelScope.launch {
            val result = repository.createMultiLineNote(content)
            result.fold(
                onSuccess = { noteId ->
                    Log.d("NoteListViewModel", "Multi-line note created with ID: $noteId")
                    _createNoteStatus.value = CreateNoteStatus.Success(noteId)
                    onSuccess(noteId)
                },
                onFailure = { e ->
                    Log.w("NoteListViewModel", "Error creating multi-line note", e)
                    _createNoteStatus.value = CreateNoteStatus.Error(e)
                }
            )
        }
    }

    fun clearLoadError() {
        if (_loadStatus.value is LoadStatus.Error) {
            _loadStatus.value = null
        }
    }

    fun clearCreateNoteError() {
        if (_createNoteStatus.value is CreateNoteStatus.Error) {
            _createNoteStatus.value = null
        }
    }
}

sealed class LoadStatus {
    object Loading : LoadStatus()
    object Success : LoadStatus()
    data class Error(val throwable: Throwable) : LoadStatus()
}

sealed class CreateNoteStatus {
    object Loading : CreateNoteStatus()
    data class Success(val noteId: String) : CreateNoteStatus()
    data class Error(val throwable: Throwable) : CreateNoteStatus()
}
