package org.alkaline.taskbrain.ui.currentnote

import android.util.Log
import org.alkaline.taskbrain.ui.currentnote.util.AlarmSymbolUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.alkaline.taskbrain.data.RecentTab
import org.alkaline.taskbrain.data.RecentTabsRepository

/**
 * ViewModel for managing the recent tabs bar.
 * Coordinates between tab UI and Firebase persistence.
 */
class RecentTabsViewModel : ViewModel() {

    private val repository = RecentTabsRepository()

    private val _tabs = MutableLiveData<List<RecentTab>>(emptyList())
    val tabs: LiveData<List<RecentTab>> = _tabs

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<TabsError?>(null)
    val error: LiveData<TabsError?> = _error

    fun clearError() {
        _error.value = null
    }

    /**
     * Loads open tabs from Firebase.
     */
    fun loadTabs() {
        _isLoading.value = true
        viewModelScope.launch {
            val result = repository.getOpenTabs()
            result.fold(
                onSuccess = { tabList ->
                    _tabs.value = tabList
                    Log.d(TAG, "Loaded ${tabList.size} tabs")
                },
                onFailure = { e ->
                    Log.e(TAG, "Error loading tabs", e)
                    _tabs.value = emptyList()
                    _error.value = TabsError("Failed to load tabs", e)
                }
            )
            _isLoading.value = false
        }
    }

    /**
     * Called when a note is opened. Adds or updates the tab.
     * @param noteId The ID of the note being opened
     * @param content The note content (first line will be used as display text)
     */
    fun onNoteOpened(noteId: String, content: String) {
        val displayText = extractDisplayText(content)
        viewModelScope.launch {
            val result = repository.addOrUpdateTab(noteId, displayText)
            result.fold(
                onSuccess = {
                    // Reload tabs to get updated order
                    loadTabs()
                },
                onFailure = { e ->
                    Log.e(TAG, "Error adding tab for note: $noteId", e)
                    _error.value = TabsError("Failed to add tab", e)
                }
            )
        }
    }

    /**
     * Called when user closes a tab with the X button.
     */
    fun closeTab(noteId: String) {
        viewModelScope.launch {
            val result = repository.removeTab(noteId)
            result.fold(
                onSuccess = {
                    // Update local state immediately for responsive UI
                    _tabs.value = _tabs.value?.filter { it.noteId != noteId }
                },
                onFailure = { e ->
                    Log.e(TAG, "Error closing tab: $noteId", e)
                    _error.value = TabsError("Failed to close tab", e)
                }
            )
        }
    }

    /**
     * Called when a note is deleted. Removes its tab immediately.
     */
    fun onNoteDeleted(noteId: String) {
        viewModelScope.launch {
            val result = repository.removeTab(noteId)
            result.onFailure { e ->
                Log.e(TAG, "Error removing tab for deleted note: $noteId", e)
                // Don't show error for this - it's a background cleanup
            }
            // Update local state immediately
            _tabs.value = _tabs.value?.filter { it.noteId != noteId }
        }
    }

    /**
     * Updates the display text for a tab (e.g., after note content changes).
     */
    fun updateTabDisplayText(noteId: String, content: String) {
        val displayText = extractDisplayText(content)
        viewModelScope.launch {
            val result = repository.updateTabDisplayText(noteId, displayText)
            result.fold(
                onSuccess = {
                    // Update local state immediately
                    _tabs.value = _tabs.value?.map { tab ->
                        if (tab.noteId == noteId) tab.copy(displayText = displayText) else tab
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Error updating tab display text: $noteId", e)
                    // Don't show error for display text updates - non-critical
                }
            )
        }
    }

    /**
     * Extracts display text from note content.
     * Uses the first line, truncated if needed.
     */
    private fun extractDisplayText(content: String): String {
        val firstLine = content.lines().firstOrNull() ?: ""
        // Remove any special characters/markers that shouldn't be in tab display
        val cleanedLine = firstLine
            .replace(AlarmSymbolUtils.ALARM_SYMBOL, "")
            .trim()
        return if (cleanedLine.length > MAX_DISPLAY_LENGTH) {
            cleanedLine.take(MAX_DISPLAY_LENGTH - 1) + "…"
        } else {
            cleanedLine.ifEmpty { "New Note" }
        }
    }

    companion object {
        private const val TAG = "RecentTabsViewModel"
        private const val MAX_DISPLAY_LENGTH = 12
    }
}

/**
 * Represents an error that occurred during tab operations.
 */
data class TabsError(
    val message: String,
    val cause: Throwable
)
