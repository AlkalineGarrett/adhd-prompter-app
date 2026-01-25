package org.alkaline.taskbrain.ui.currentnote

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import org.alkaline.taskbrain.dsl.DirectiveFinder
import org.alkaline.taskbrain.dsl.DirectiveInstance
import org.alkaline.taskbrain.dsl.DirectiveResult
import org.alkaline.taskbrain.dsl.DirectiveResultRepository
import org.alkaline.taskbrain.dsl.matchDirectiveInstances
import org.alkaline.taskbrain.dsl.parseAllDirectiveLocations
import org.alkaline.taskbrain.ui.currentnote.undo.AlarmSnapshot
import org.alkaline.taskbrain.util.PermissionHelper

class CurrentNoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository()
    private val alarmRepository = AlarmRepository()
    private val alarmScheduler = AlarmScheduler(application)
    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("taskbrain_prefs", Context.MODE_PRIVATE)
    private val agent = PrompterAgent()
    private val directiveResultRepository = DirectiveResultRepository()
    
    private val _saveStatus = MutableLiveData<SaveStatus>()
    val saveStatus: LiveData<SaveStatus> = _saveStatus

    // Event emitted when a save completes successfully - allows other screens to refresh
    private val _saveCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveCompleted: SharedFlow<Unit> = _saveCompleted.asSharedFlow()

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

    // Directive execution results - maps directive UUID to result
    private val _directiveResults = MutableLiveData<Map<String, DirectiveResult>>(emptyMap())
    val directiveResults: LiveData<Map<String, DirectiveResult>> = _directiveResults

    // Directive instances with stable UUIDs - survives text edits
    private var directiveInstances: List<DirectiveInstance> = emptyList()

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
            // Still update lastAccessedAt and load directive results in background
            viewModelScope.launch {
                repository.updateLastAccessed(currentNoteId)
                loadDirectiveResults(fullContent)
            }
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

                    // Load cached directive results and execute any missing directives
                    loadDirectiveResults(fullContent)
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

                    // Execute directives and store results
                    executeAndStoreDirectives(content)

                    Log.d("CurrentNoteViewModel", "Note saved successfully with structure.")
                    _saveStatus.value = SaveStatus.Success
                    _saveCompleted.tryEmit(Unit)
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
                    _saveCompleted.tryEmit(Unit)
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

    // region Directive Execution

    /**
     * Execute directives in the content and update local state immediately.
     * Called when content changes (e.g., when user types a closing bracket).
     * Results are NOT persisted to Firestore until save.
     *
     * Uses UUID-based keys for stable identity across text edits.
     * UUIDs are preserved when directives move due to text insertions/deletions.
     */
    fun executeDirectivesLive(content: String) {
        if (!DirectiveFinder.containsDirectives(content)) {
            return
        }

        viewModelScope.launch {
            // Parse all directive locations from content
            val newLocations = parseAllDirectiveLocations(content)

            // Match to existing instances to preserve UUIDs
            val updatedInstances = matchDirectiveInstances(directiveInstances, newLocations)
            directiveInstances = updatedInstances

            // Find directives that need execution (no existing result)
            val currentResults = _directiveResults.value ?: emptyMap()
            val missingInstances = updatedInstances.filter { currentResults[it.uuid] == null }

            if (missingInstances.isEmpty()) {
                return@launch
            }

            // Execute missing directives
            val executedResults = missingInstances.associate { instance ->
                instance.uuid to DirectiveFinder.executeDirective(instance.sourceText)
            }

            // Merge new results into CURRENT state (not the captured snapshot)
            // This avoids overwriting changes made by toggleDirectiveCollapsed while
            // this coroutine was running (race condition fix)
            val latestResults = _directiveResults.value?.toMutableMap() ?: mutableMapOf()
            for ((uuid, result) in executedResults) {
                // Only add if still missing (another coroutine might have added it)
                if (latestResults[uuid] == null) {
                    latestResults[uuid] = result
                }
            }

            _directiveResults.value = latestResults
            Log.d(TAG, "Live executed ${executedResults.size} directives")
        }
    }

    /**
     * Execute all directives in the content and store results.
     * Called after note save succeeds.
     * Preserves collapsed state from local results.
     *
     * Uses UUID-based keys for in-memory state, but stores with text hash in Firestore
     * for cross-session caching.
     */
    private fun executeAndStoreDirectives(content: String) {
        if (!DirectiveFinder.containsDirectives(content)) {
            return
        }

        viewModelScope.launch {
            // Parse all directive locations and match to existing instances
            val newLocations = parseAllDirectiveLocations(content)
            val updatedInstances = matchDirectiveInstances(directiveInstances, newLocations)
            directiveInstances = updatedInstances

            // Execute all directives with UUID-based keys
            val freshResults = mutableMapOf<String, DirectiveResult>()
            for (instance in updatedInstances) {
                val result = DirectiveFinder.executeDirective(instance.sourceText)
                freshResults[instance.uuid] = result
            }

            // Merge fresh results with CURRENT collapsed state (read at merge time to avoid race)
            // This ensures we don't overwrite collapsed state changes made while executing
            val mergedResults = freshResults.mapValues { (uuid, result) ->
                val latestCollapsed = _directiveResults.value?.get(uuid)?.collapsed ?: true
                result.copy(collapsed = latestCollapsed)
            }

            // Update local state
            _directiveResults.value = mergedResults

            // Store results in Firestore using text hash as key for cross-session caching
            for (instance in updatedInstances) {
                val result = mergedResults[instance.uuid] ?: continue
                val textHash = DirectiveResult.hashDirective(instance.sourceText)
                directiveResultRepository.saveResult(currentNoteId, textHash, result)
                    .onFailure { e ->
                        Log.e(TAG, "Failed to save directive result: $textHash", e)
                    }
            }

            Log.d(TAG, "Executed ${mergedResults.size} directives")
        }
    }

    /**
     * Load cached directive results for the current note, and execute any missing directives.
     * Called when note is loaded.
     *
     * Cached results in Firestore are keyed by text hash. We create fresh UUID-based instances
     * and map cached results to them.
     */
    private suspend fun loadDirectiveResults(content: String) {
        // First load cached results (keyed by text hash in Firestore)
        val cachedByHash = directiveResultRepository.getResults(currentNoteId)
            .getOrElse { e ->
                Log.e(TAG, "Failed to load directive results", e)
                emptyMap()
            }

        if (!DirectiveFinder.containsDirectives(content)) {
            directiveInstances = emptyList()
            _directiveResults.postValue(emptyMap())
            return
        }

        // Parse all directive locations and create fresh instances with UUIDs
        val newLocations = parseAllDirectiveLocations(content)
        val newInstances = newLocations.map { loc ->
            DirectiveInstance.create(loc.lineIndex, loc.startOffset, loc.sourceText)
        }
        directiveInstances = newInstances

        // Build UUID-based results map from cached results
        val uuidResults = mutableMapOf<String, DirectiveResult>()
        val missingInstances = mutableListOf<DirectiveInstance>()

        for (instance in newInstances) {
            val textHash = DirectiveResult.hashDirective(instance.sourceText)
            val cached = cachedByHash[textHash]
            if (cached != null) {
                uuidResults[instance.uuid] = cached
            } else {
                missingInstances.add(instance)
            }
        }

        if (missingInstances.isEmpty()) {
            // All directives have cached results
            _directiveResults.postValue(uuidResults)
            Log.d(TAG, "Loaded ${uuidResults.size} cached directive results")
            return
        }

        // Execute missing directives
        for (instance in missingInstances) {
            val result = DirectiveFinder.executeDirective(instance.sourceText)
            uuidResults[instance.uuid] = result
            // Store in Firestore using text hash
            val textHash = DirectiveResult.hashDirective(instance.sourceText)
            directiveResultRepository.saveResult(currentNoteId, textHash, result)
                .onFailure { e ->
                    Log.e(TAG, "Failed to save directive result: $textHash", e)
                }
        }

        _directiveResults.postValue(uuidResults)
        Log.d(TAG, "Loaded ${uuidResults.size - missingInstances.size} cached + executed ${missingInstances.size} new directives")
    }

    /**
     * Toggle the collapsed state of a directive result.
     * If no result exists for this UUID, executes the directive first.
     *
     * @param directiveUuid The UUID of the directive instance
     * @param sourceText The source text of the directive (e.g., "[42]"), needed to execute if no result exists
     */
    fun toggleDirectiveCollapsed(directiveUuid: String, sourceText: String? = null) {
        val current = _directiveResults.value ?: mutableMapOf()
        val existingResult = current[directiveUuid]

        if (existingResult == null) {
            // No result exists - execute the directive and create result with collapsed = false
            if (sourceText == null) {
                Log.w(TAG, "Cannot toggle directive without sourceText when no result exists")
                return
            }

            val newResult = DirectiveFinder.executeDirective(sourceText).copy(collapsed = false)
            val updated = current.toMutableMap()
            updated[directiveUuid] = newResult
            _directiveResults.value = updated
        } else {
            // Result exists - toggle collapsed state
            val newCollapsed = !existingResult.collapsed
            val updated = current.toMutableMap()
            updated[directiveUuid] = existingResult.copy(collapsed = newCollapsed)
            _directiveResults.value = updated
        }
        // Firestore sync happens on save via executeAndStoreDirectives
    }

    /**
     * Re-executes a directive and collapses it.
     * Called when user confirms a directive edit (including when no changes were made).
     * This ensures dynamic directives like [now] get fresh values on confirm.
     *
     * @param directiveUuid The UUID of the directive instance
     * @param sourceText The source text of the directive (e.g., "[now]")
     */
    fun confirmDirective(directiveUuid: String, sourceText: String) {
        val current = _directiveResults.value?.toMutableMap() ?: mutableMapOf()

        // Always re-execute to get fresh value (important for dynamic directives like [now])
        val freshResult = DirectiveFinder.executeDirective(sourceText).copy(collapsed = true)
        current[directiveUuid] = freshResult
        _directiveResults.value = current

        Log.d(TAG, "Confirmed directive $directiveUuid with fresh result")
    }

    /**
     * Gets the UUID for a directive at the given position.
     * Returns null if no directive instance exists at that position.
     */
    fun getDirectiveUuid(lineIndex: Int, startOffset: Int): String? {
        return directiveInstances.find { it.lineIndex == lineIndex && it.startOffset == startOffset }?.uuid
    }

    /**
     * Gets directive results keyed by position (lineIndex:startOffset) for UI display.
     * This converts from the internal UUID-keyed results to position-keyed results.
     */
    fun getResultsByPosition(): Map<String, DirectiveResult> {
        val uuidResults = _directiveResults.value ?: return emptyMap()
        val positionResults = mutableMapOf<String, DirectiveResult>()
        for (instance in directiveInstances) {
            val result = uuidResults[instance.uuid]
            if (result != null) {
                val positionKey = DirectiveFinder.directiveKey(instance.lineIndex, instance.startOffset)
                positionResults[positionKey] = result
            }
        }
        return positionResults
    }

    /**
     * A position identifier for a directive: line index and start offset within line content.
     * Used for undo/redo to restore expanded state by matching positions.
     */
    data class DirectivePosition(val lineIndex: Int, val startOffset: Int)

    /**
     * Gets the positions of all currently expanded directive edit rows.
     * Used before undo/redo to preserve expanded state by position.
     */
    fun getExpandedDirectivePositions(content: String): Set<DirectivePosition> {
        val current = _directiveResults.value ?: return emptySet()
        val expandedUuids = current.filter { !it.value.collapsed }.keys
        if (expandedUuids.isEmpty()) return emptySet()

        // Convert expanded UUIDs to DirectivePosition using current instances
        return directiveInstances
            .filter { it.uuid in expandedUuids }
            .map { DirectivePosition(it.lineIndex, it.startOffset) }
            .toSet()
    }

    /**
     * Restores expanded state for directives at the given positions.
     * Called after undo/redo to preserve edit row state for directives that still exist.
     * Re-runs directive matching to assign UUIDs to the new content.
     */
    fun restoreExpandedDirectivesByPosition(content: String, positions: Set<DirectivePosition>) {
        if (positions.isEmpty()) return

        // Re-parse directives and match to existing instances
        val newLocations = parseAllDirectiveLocations(content)
        val updatedInstances = matchDirectiveInstances(directiveInstances, newLocations)
        directiveInstances = updatedInstances

        val current = _directiveResults.value?.toMutableMap() ?: mutableMapOf()

        // Find directives at the target positions and expand them
        for (instance in updatedInstances) {
            val pos = DirectivePosition(instance.lineIndex, instance.startOffset)
            if (pos in positions) {
                val existing = current[instance.uuid]
                if (existing != null) {
                    current[instance.uuid] = existing.copy(collapsed = false)
                } else {
                    // Execute the directive and set it to expanded
                    val result = DirectiveFinder.executeDirective(instance.sourceText)
                    current[instance.uuid] = result.copy(collapsed = false)
                }
            }
        }

        _directiveResults.value = current
    }

    // endregion

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
