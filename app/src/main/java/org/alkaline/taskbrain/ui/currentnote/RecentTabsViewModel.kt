package org.alkaline.taskbrain.ui.currentnote

import android.util.Log
import org.alkaline.taskbrain.ui.currentnote.util.AlarmSymbolUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.alkaline.taskbrain.data.NoteLine
import org.alkaline.taskbrain.data.RecentTab
import org.alkaline.taskbrain.data.RecentTabsRepository

/**
 * Cached note content for instant tab switching.
 */
data class CachedNoteContent(
    val noteLines: List<NoteLine>,
    val isDeleted: Boolean = false
)

/**
 * ViewModel for managing the recent tabs bar.
 * Coordinates between tab UI and Firebase persistence.
 * Also maintains an in-memory cache of note content for instant tab switching.
 */
class RecentTabsViewModel : ViewModel() {

    private val repository = RecentTabsRepository()

    private val _tabs = MutableLiveData<List<RecentTab>>(emptyList())
    val tabs: LiveData<List<RecentTab>> = _tabs

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<TabsError?>(null)
    val error: LiveData<TabsError?> = _error

    // In-memory cache of note content for instant tab switching
    // Cache is bounded by max tabs (5) so memory usage is naturally limited
    private val noteCache = mutableMapOf<String, CachedNoteContent>()

    fun clearError() {
        _error.value = null
    }

    /**
     * Gets cached note content if available.
     */
    fun getCachedContent(noteId: String): CachedNoteContent? = noteCache[noteId]

    /**
     * Caches note content for instant tab switching.
     */
    fun cacheNoteContent(noteId: String, noteLines: List<NoteLine>, isDeleted: Boolean = false) {
        noteCache[noteId] = CachedNoteContent(noteLines, isDeleted)
    }

    /**
     * Invalidates cache for a specific note.
     * Call this when external changes occur (e.g., AI modification).
     */
    fun invalidateCache(noteId: String) {
        noteCache.remove(noteId)
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
     * Uses optimistic update for immediate UI animation.
     * @param noteId The ID of the note being opened
     * @param content The note content (first line will be used as display text)
     */
    fun onNoteOpened(noteId: String, content: String) {
        val displayText = extractDisplayText(content)

        // OPTIMISTIC UPDATE: Immediately move/add tab to front for instant animation
        _tabs.value = _tabs.value?.let { currentTabs ->
            val existingTab = currentTabs.find { it.noteId == noteId }
            if (existingTab != null) {
                // Move existing tab to front with updated display text
                listOf(existingTab.copy(displayText = displayText)) +
                    currentTabs.filter { it.noteId != noteId }
            } else {
                // Add new tab at front
                listOf(RecentTab(noteId = noteId, displayText = displayText)) + currentTabs
            }
        } ?: listOf(RecentTab(noteId = noteId, displayText = displayText))

        // Persist to Firestore in background (no loadTabs() call on success)
        viewModelScope.launch {
            val result = repository.addOrUpdateTab(noteId, displayText)
            result.onFailure { e ->
                Log.e(TAG, "Error adding tab for note: $noteId", e)
                // Sync with Firebase on error to recover correct state
                loadTabs()
                _error.value = TabsError("Failed to sync tab", e)
            }
        }
    }

    /**
     * Called when user closes a tab with the X button.
     */
    fun closeTab(noteId: String) {
        // Clear cache for this note
        noteCache.remove(noteId)

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
        // Clear cache for deleted note
        noteCache.remove(noteId)

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
