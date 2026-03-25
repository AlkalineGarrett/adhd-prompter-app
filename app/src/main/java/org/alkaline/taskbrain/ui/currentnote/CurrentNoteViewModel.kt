package org.alkaline.taskbrain.ui.currentnote

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmRepository
import org.alkaline.taskbrain.data.AlarmStage
import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.data.RecurringAlarm
import org.alkaline.taskbrain.data.RecurringAlarmRepository
import org.alkaline.taskbrain.data.toTimeOfDay

import org.alkaline.taskbrain.service.AlarmScheduleResult
import org.alkaline.taskbrain.service.AlarmScheduler
import org.alkaline.taskbrain.service.AlarmStateManager
import org.alkaline.taskbrain.service.RecurrenceConfigMapper
import org.alkaline.taskbrain.service.RecurrenceTemplateManager
import org.alkaline.taskbrain.service.RecurrenceScheduler
import org.alkaline.taskbrain.ui.currentnote.components.RecurrenceConfig
import org.alkaline.taskbrain.data.NoteLine
import org.alkaline.taskbrain.data.NoteLineTracker
import org.alkaline.taskbrain.data.NoteRepository
import org.alkaline.taskbrain.data.PrompterAgent
import org.alkaline.taskbrain.dsl.cache.CachedDirectiveExecutor
import org.alkaline.taskbrain.dsl.cache.CachedDirectiveExecutorFactory
import org.alkaline.taskbrain.dsl.cache.DirectiveCacheManager
import org.alkaline.taskbrain.dsl.cache.EditSessionManager
import org.alkaline.taskbrain.dsl.cache.MetadataHasher
import org.alkaline.taskbrain.dsl.cache.RefreshScheduler
import org.alkaline.taskbrain.dsl.cache.RefreshTriggerAnalyzer
import org.alkaline.taskbrain.dsl.directives.DirectiveFinder
import org.alkaline.taskbrain.dsl.directives.DirectiveResult
import org.alkaline.taskbrain.dsl.directives.DirectiveSegment
import org.alkaline.taskbrain.dsl.directives.DirectiveResultRepository
import org.alkaline.taskbrain.dsl.directives.ScheduleManager
import org.alkaline.taskbrain.dsl.runtime.values.AlarmVal
import org.alkaline.taskbrain.dsl.runtime.values.DateTimeVal
import org.alkaline.taskbrain.dsl.runtime.values.DateVal
import org.alkaline.taskbrain.dsl.runtime.values.TimeVal
import org.alkaline.taskbrain.dsl.language.Lexer
import org.alkaline.taskbrain.dsl.language.Parser
import org.alkaline.taskbrain.dsl.language.RefreshExpr
import org.alkaline.taskbrain.dsl.runtime.Environment
import org.alkaline.taskbrain.dsl.runtime.Executor
import org.alkaline.taskbrain.dsl.runtime.MutationType
import org.alkaline.taskbrain.dsl.runtime.NoteContext
import org.alkaline.taskbrain.dsl.runtime.NoteMutation
import org.alkaline.taskbrain.dsl.runtime.NoteRepositoryOperations
import org.alkaline.taskbrain.dsl.runtime.values.ButtonVal
import org.alkaline.taskbrain.dsl.runtime.ViewVal
import org.alkaline.taskbrain.dsl.ui.ButtonExecutionState
import org.alkaline.taskbrain.ui.currentnote.undo.AlarmSnapshot
import org.alkaline.taskbrain.ui.currentnote.util.SymbolOverlay
import org.alkaline.taskbrain.util.PermissionHelper

class CurrentNoteViewModel @JvmOverloads constructor(
    application: Application,
    // External dependencies - injectable for testing, default to real implementations
    private val repository: NoteRepository = NoteRepository(),
    private val alarmRepository: AlarmRepository = AlarmRepository(),
    private val alarmScheduler: AlarmScheduler = AlarmScheduler(application),
    private val alarmStateManager: AlarmStateManager = AlarmStateManager(application),
    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("taskbrain_prefs", Context.MODE_PRIVATE),
    private val agent: PrompterAgent = PrompterAgent(),
    private val directiveResultRepository: DirectiveResultRepository = DirectiveResultRepository(),
    // For testing: provide a way to override noteOperations without Firebase
    private val noteOperationsProvider: (() -> NoteRepositoryOperations?)? = null
) : AndroidViewModel(application) {

    private val recurringAlarmRepository = RecurringAlarmRepository()
    private val templateManager = RecurrenceTemplateManager(recurringAlarmRepository, alarmRepository, alarmStateManager)

    // Directive caching infrastructure (Phase 10 integration)
    private val directiveCacheManager = DirectiveCacheManager()
    private val editSessionManager = EditSessionManager(directiveCacheManager)
    private val cachedDirectiveExecutor = CachedDirectiveExecutor(
        cacheManager = directiveCacheManager,
        editSessionManager = editSessionManager
    )
    private val refreshScheduler = RefreshScheduler(
        onTrigger = { cacheKey, noteId ->
            // When a refresh trigger fires, clear the cache entry
            if (noteId != null) {
                directiveCacheManager.clearNote(noteId)
            } else {
                directiveCacheManager.clearAll()
            }
        }
    )

    // Note operations for DSL mutations (Milestone 7)
    // Use injected provider for testing, or create from Firebase for production
    private val noteOperations: NoteRepositoryOperations?
        get() {
            // If a provider was injected (for testing), use it
            noteOperationsProvider?.let { return it() }
            // Otherwise, use Firebase (production)
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
            return NoteRepositoryOperations(FirebaseFirestore.getInstance(), userId)
        }

    private val _saveStatus = MutableLiveData<SaveStatus>()
    val saveStatus: LiveData<SaveStatus> = _saveStatus

    // Event emitted when a save completes successfully - allows other screens to refresh
    private val _saveCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveCompleted: SharedFlow<Unit> = _saveCompleted.asSharedFlow()

    /** Emitted after save when new noteIds are assigned to lines that had none. Map: lineIndex → newNoteId */
    private val _newlyAssignedNoteIds = MutableSharedFlow<Map<Int, String>>(extraBufferCapacity = 1)
    val newlyAssignedNoteIds: SharedFlow<Map<Int, String>> = _newlyAssignedNoteIds.asSharedFlow()

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

    // Per-note toggle: whether completed (checked) lines are visible
    private val _showCompleted = MutableLiveData<Boolean>(true)
    val showCompleted: LiveData<Boolean> = _showCompleted

    // Bumped after directive cache or expanded state changes, triggering recomposition
    // so the synchronous computeDirectiveResults() picks up new results/state.
    private val _directiveCacheGeneration = MutableLiveData(0)
    val directiveCacheGeneration: LiveData<Int> = _directiveCacheGeneration

    // Tracks which directives are expanded (by hash key). Default state is collapsed.
    private val expandedDirectiveHashes = mutableSetOf<String>()

    // Button execution states - maps directive key to execution state
    private val _buttonExecutionStates = MutableLiveData<Map<String, ButtonExecutionState>>(emptyMap())
    val buttonExecutionStates: LiveData<Map<String, ButtonExecutionState>> = _buttonExecutionStates

    // Button error messages - maps directive key to error message
    private val _buttonErrors = MutableLiveData<Map<String, String>>(emptyMap())
    val buttonErrors: LiveData<Map<String, String>> = _buttonErrors

    /** Clear the button error for a specific directive */
    fun clearButtonError(directiveKey: String) {
        val current = _buttonErrors.value?.toMutableMap() ?: mutableMapOf()
        current.remove(directiveKey)
        _buttonErrors.value = current
    }

    // Cached notes for find() operations in directives
    private var cachedNotes: List<Note>? = null

    // Cached current note for [.] reference in directives (Milestone 6)
    private var cachedCurrentNote: Note? = null

    /**
     * Invalidate the notes cache so view directives get fresh content.
     * Call this when switching tabs after saving to ensure views show updated data.
     */
    fun invalidateNotesCache() {
        cachedNotes = null
        cachedCurrentNote = null
        // Phase 3: Invalidate metadata hash cache when notes change
        MetadataHasher.invalidateCache()
        // Also clear the directive cache to force re-execution with fresh data
        directiveCacheManager.clearAll()
    }

    // Emits when notes cache is refreshed (e.g., after save) so UI can refresh view directives
    private val _notesCacheRefreshed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val notesCacheRefreshed: SharedFlow<Unit> = _notesCacheRefreshed.asSharedFlow()

    // Start refresh scheduler on ViewModel creation and set up edit session listener
    init {
        refreshScheduler.start()
        // When an edit session ends, refresh the current view
        editSessionManager.addSessionEndListener {
            // Re-execute directives to pick up any changes that were suppressed
            bumpDirectiveCacheGeneration()
        }
    }

    // Firestore snapshot listener for real-time external change detection
    private var snapshotListener: ListenerRegistration? = null
    private var suppressSnapshotUpdate = false

    /**
     * Saves the current note to Firestore. ALL saves of the current note's content
     * MUST go through this method — it sets [suppressSnapshotUpdate] to prevent
     * the snapshot listener from triggering a spurious reload that overwrites the editor.
     *
     * On success, handles common post-save bookkeeping: updating line tracker IDs,
     * notifying the UI, and syncing alarm line content.
     */
    private suspend fun persistCurrentNote(trackedLines: List<NoteLine>): Result<Map<Int, String>> {
        suppressSnapshotUpdate = true
        val result = repository.saveNoteWithChildren(currentNoteId, trackedLines)
        result.fold(
            onSuccess = { newIdsMap ->
                for ((index, newId) in newIdsMap) {
                    lineTracker.updateLineNoteId(index, newId)
                }
                if (newIdsMap.isNotEmpty()) _newlyAssignedNoteIds.tryEmit(newIdsMap)
                syncAlarmLineContent(trackedLines)
                _saveStatus.value = SaveStatus.Success
                _saveCompleted.tryEmit(Unit)
                markAsSaved()
            },
            onFailure = { e ->
                Log.e(TAG, "Error saving note", e)
                _saveStatus.value = SaveStatus.Error(e)
            }
        )
        return result
    }

    // Current Note ID being edited — initialized from SharedPreferences so that LiveData
    // exposes the correct value immediately, preventing a race where the sync LaunchedEffect
    // in CurrentNoteScreen sets displayedNoteId to a stale default before loadContent runs.
    // For new users (no stored pref), starts as null/empty — loadContent will create a note.
    private val LAST_VIEWED_NOTE_KEY = "last_viewed_note_id"
    private var currentNoteId = sharedPreferences.getString(LAST_VIEWED_NOTE_KEY, null) ?: ""

    // Expose current note ID for UI (e.g., undo state persistence).
    // Nullable: null means no note loaded yet (new user before first note is created).
    private val _currentNoteIdLiveData = MutableLiveData<String?>(
        sharedPreferences.getString(LAST_VIEWED_NOTE_KEY, null)
    )
    val currentNoteIdLiveData: LiveData<String?> = _currentNoteIdLiveData

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

    /** Extracts noteIds from NoteLines for passing to EditorState via LoadStatus. */
    private fun noteLinesToNoteIds(lines: List<NoteLine>): List<List<String>> =
        lines.map { listOfNotNull(it.noteId) }

    /** Returns per-line noteIds from the line tracker for directive key generation. */
    fun getLineNoteIds(): List<String?> =
        lineTracker.getTrackedLines().map { it.noteId }

    /**
     * Starts a Firestore snapshot listener on the current note's parent document.
     * When an external change is detected (not from our own save), reloads the full note.
     */
    private fun startSnapshotListener(noteId: String, recentTabsViewModel: RecentTabsViewModel?) {
        snapshotListener?.remove()
        // Suppress the initial snapshot that fires immediately on registration
        suppressSnapshotUpdate = true
        val db = FirebaseFirestore.getInstance()
        snapshotListener = db.collection("notes").document(noteId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Snapshot listener error for $noteId", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                if (noteId != currentNoteId) return@addSnapshotListener

                // Skip local pending writes (optimistic updates from our own writes)
                // Check before suppress so the flag isn't consumed by the pending-write event
                if (snapshot.metadata.hasPendingWrites()) return@addSnapshotListener

                // Skip the initial snapshot and our own saves
                if (suppressSnapshotUpdate) {
                    suppressSnapshotUpdate = false
                    return@addSnapshotListener
                }

                Log.d(TAG, "Snapshot listener detected external change for $noteId")
                // Reload the full note (parent + children) from Firestore
                viewModelScope.launch {
                    val result = repository.loadNoteWithChildren(noteId)
                    result.fold(
                        onSuccess = { freshLines ->
                            if (noteId != currentNoteId) return@fold
                            lineTracker.setTrackedLines(freshLines)
                            val freshContent = freshLines.joinToString("\n") { it.content }
                            _loadStatus.value = LoadStatus.Success(freshContent, noteLinesToNoteIds(freshLines))
                            recentTabsViewModel?.cacheNoteContent(
                                noteId, freshLines, _isNoteDeleted.value ?: false
                            )
                            loadDirectiveResults(freshContent)
                            loadAlarmStates()
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Snapshot-triggered reload failed for $noteId", e)
                        }
                    )
                }
            }
    }

    fun loadContent(noteId: String? = null, recentTabsViewModel: RecentTabsViewModel? = null) {
        val resolvedId = noteId?.takeIf { it.isNotEmpty() }
            ?: sharedPreferences.getString(LAST_VIEWED_NOTE_KEY, null)

        if (resolvedId == null) {
            // New user with no notes — create their first note
            _loadStatus.value = LoadStatus.Loading
            viewModelScope.launch {
                repository.createNote().fold(
                    onSuccess = { newId ->
                        Log.d(TAG, "loadContent: created first note for new user: $newId")
                        loadContent(newId, recentTabsViewModel)
                    },
                    onFailure = { e ->
                        Log.e(TAG, "loadContent: failed to create first note", e)
                        _loadStatus.value = LoadStatus.Error(e)
                    }
                )
            }
            return
        }

        currentNoteId = resolvedId
        _currentNoteIdLiveData.value = currentNoteId
        // (generation bumped after async load completes)
        Log.d(TAG, "loadContent: switching to noteId=$currentNoteId, cachedNotes=${cachedNotes?.size}, cachedNotesNull=${cachedNotes == null}")

        // Save the current note as the last viewed note
        sharedPreferences.edit().putString(LAST_VIEWED_NOTE_KEY, currentNoteId).apply()

        // Start real-time listener for external changes on this note
        startSnapshotListener(currentNoteId, recentTabsViewModel)

        // Note: Don't clear cachedNotes here - it persists until a save happens
        // This allows view directives to use cached content when switching tabs
        // The cache is refreshed in refreshNotesCache() after saves

        // Recreate line tracker with new parent note ID
        lineTracker = NoteLineTracker(currentNoteId)

        // Clear expanded state from previous note
        expandedDirectiveHashes.clear()

        // Check cache first for instant tab switching
        val cached = recentTabsViewModel?.getCachedContent(currentNoteId)
        if (cached != null) {
            val fullContent = cached.noteLines.joinToString("\n") { it.content }
            Log.d(TAG, "loadContent: using RecentTabsViewModel cache for $currentNoteId")
            // Use cached content - instant!
            _isNoteDeleted.value = cached.isDeleted
            lineTracker.setTrackedLines(cached.noteLines)
            _loadStatus.value = LoadStatus.Success(fullContent, noteLinesToNoteIds(cached.noteLines))
            // Still update lastAccessedAt, load showCompleted, and directive results in background
            viewModelScope.launch {
                repository.updateLastAccessed(currentNoteId)
                repository.loadNoteById(currentNoteId).onSuccess { note ->
                    _showCompleted.value = note?.showCompleted ?: true
                }
                loadDirectiveResults(fullContent)
            }
            loadAlarmStates()

            // Background refresh: fetch from Firebase to pick up external changes (e.g. web edits)
            val cachedNoteId = currentNoteId
            viewModelScope.launch {
                val result = repository.loadNoteWithChildren(cachedNoteId)
                result.fold(
                    onSuccess = { freshLines ->
                        val freshContent = freshLines.joinToString("\n") { it.content }
                        if (freshContent != fullContent && cachedNoteId == currentNoteId) {
                            Log.d(TAG, "loadContent: background refresh found changes for $cachedNoteId")
                            lineTracker.setTrackedLines(freshLines)
                            _loadStatus.value = LoadStatus.Success(freshContent, noteLinesToNoteIds(freshLines))
                            recentTabsViewModel?.cacheNoteContent(
                                cachedNoteId,
                                freshLines,
                                _isNoteDeleted.value ?: false
                            )
                            loadDirectiveResults(freshContent)
                            loadAlarmStates()
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Background refresh failed for $cachedNoteId", e)
                    }
                )
            }
            return
        }
        Log.d(TAG, "loadContent: cache miss for $currentNoteId, fetching from Firebase")

        // Cache miss - fetch from Firebase
        _loadStatus.value = LoadStatus.Loading

        viewModelScope.launch {
            // Load note metadata (deleted state + showCompleted)
            repository.loadNoteById(currentNoteId).fold(
                onSuccess = { note ->
                    _isNoteDeleted.value = note?.state == "deleted"
                    _showCompleted.value = note?.showCompleted ?: true
                },
                onFailure = {
                    _isNoteDeleted.value = false
                    _showCompleted.value = true
                }
            )

            // Update lastAccessedAt (fire-and-forget, doesn't block loading)
            launch { repository.updateLastAccessed(currentNoteId) }

            val result = repository.loadNoteWithChildren(currentNoteId)
            result.fold(
                onSuccess = { loadedLines ->
                    lineTracker.setTrackedLines(loadedLines)
                    val fullContent = loadedLines.joinToString("\n") { it.content }
                    _loadStatus.value = LoadStatus.Success(fullContent, noteLinesToNoteIds(loadedLines))

                    // Cache the loaded content for future tab switches
                    recentTabsViewModel?.cacheNoteContent(
                        currentNoteId,
                        loadedLines,
                        _isNoteDeleted.value ?: false
                    )

                    // Load cached directive results and execute any missing directives
                    loadDirectiveResults(fullContent)

                    loadAlarmStates()
                },
                onFailure = { e ->
                    if (isPermissionDenied(e)) {
                        // Note belongs to a different user (stale SharedPreferences
                        // from a previous sign-in). Clear the stale pref and create
                        // a fresh note for the current user.
                        Log.w(TAG, "Permission denied loading note $currentNoteId — creating new note for current user")
                        sharedPreferences.edit().remove(LAST_VIEWED_NOTE_KEY).apply()
                        loadContent(null, recentTabsViewModel)
                    } else {
                        Log.e(TAG, "Error loading note", e)
                        _loadStatus.value = LoadStatus.Error(e)
                    }
                }
            )
        }
    }

    private fun isPermissionDenied(e: Throwable): Boolean {
        val firestoreException = e as? FirebaseFirestoreException
            ?: e.cause as? FirebaseFirestoreException
        return firestoreException?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    /**
     * Updates the tracked lines based on the new content provided by the user.
     * It uses a heuristic to match lines and preserve Note IDs across edits, insertions, and deletions.
     */
    fun updateTrackedLines(newContent: String) {
        lineTracker.updateTrackedLines(newContent)
    }

    fun toggleShowCompleted() {
        val newValue = !(_showCompleted.value ?: true)
        _showCompleted.value = newValue
        suppressSnapshotUpdate = true
        viewModelScope.launch {
            repository.updateShowCompleted(currentNoteId, newValue).onFailure { e ->
                Log.e(TAG, "Failed to persist showCompleted", e)
            }
        }
    }

    fun saveContent(content: String, lineNoteIds: List<List<String>> = emptyList()) {
        // Update tracked lines - use editor noteIds if available, else fall back to content matching
        if (lineNoteIds.isNotEmpty()) {
            val contentLines = content.lines()
            val noteLines = resolveNoteIds(contentLines, lineNoteIds)
            lineTracker.setTrackedLines(noteLines)
        } else {
            updateTrackedLines(content)
        }

        _saveStatus.value = SaveStatus.Saving

        viewModelScope.launch {
            val trackedLines = lineTracker.getTrackedLines()
            val result = persistCurrentNote(trackedLines)

            result.onSuccess {
                syncAlarmNoteIds(trackedLines)

                // Invalidate notes cache so other notes' view directives get fresh data
                // This must happen BEFORE executeAndStoreDirectives to ensure
                // ensureNotesLoaded() fetches fresh notes when switching tabs
                cachedNotes = null
                // Phase 3: Invalidate metadata hash cache when notes change
                MetadataHasher.invalidateCache()

                // Execute directives and store results
                executeAndStoreDirectives(content)
            }
        }
    }

    fun processAgentCommand(currentContent: String, command: String) {
        _isAgentProcessing.value = true
        viewModelScope.launch {
            try {
                val updatedContent = agent.processCommand(currentContent, command)
                // Also update tracked lines since content changed externally
                updateTrackedLines(updatedContent)

                // Update the UI with the new content
                _loadStatus.value = LoadStatus.Success(
                    updatedContent,
                    noteLinesToNoteIds(lineTracker.getTrackedLines())
                )
                
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
     * Syncs alarm noteIds with line noteIds.
     * For each line containing alarm directives, updates the alarm's noteId
     * if it doesn't match the line's current noteId.
     */
    private fun syncAlarmNoteIds(trackedLines: List<NoteLine>) {
        viewModelScope.launch {
            val updates = findAlarmNoteIdUpdates(trackedLines) { alarmId ->
                alarmRepository.getAlarm(alarmId).getOrNull()?.noteId
            }
            for (update in updates) {
                alarmRepository.updateAlarmNoteId(update.alarmId, update.lineNoteId)
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

    // Recurrence config and template for the currently selected alarm (if recurring)
    private val _recurrenceConfig = MutableLiveData<RecurrenceConfig?>()
    val recurrenceConfig: LiveData<RecurrenceConfig?> = _recurrenceConfig

    private val _recurringAlarm = MutableLiveData<RecurringAlarm?>()
    val recurringAlarm: LiveData<RecurringAlarm?> = _recurringAlarm

    /**
     * Fetches a specific alarm by its document ID.
     */
    fun fetchAlarmById(alarmId: String, onComplete: (Alarm?) -> Unit) {
        viewModelScope.launch {
            val alarm = alarmRepository.getAlarm(alarmId).getOrNull()
            onComplete(alarm)
        }
    }

    /**
     * Fetches the recurrence config for an alarm, if it's recurring.
     * Call this when opening the alarm dialog for an existing alarm.
     */
    fun fetchRecurrenceConfig(alarm: Alarm?) {
        if (alarm?.recurringAlarmId == null) {
            _recurrenceConfig.value = null
            _recurringAlarm.value = null
            return
        }
        viewModelScope.launch {
            val recurringRepo = recurringAlarmRepository
            val recurring = recurringRepo.get(alarm.recurringAlarmId).getOrNull()
            _recurringAlarm.value = recurring
            _recurrenceConfig.value = recurring?.let { RecurrenceConfigMapper.toRecurrenceConfig(it) }
        }
    }

    suspend fun getInstancesForRecurring(recurringAlarmId: String): List<Alarm> {
        return alarmRepository.getInstancesForRecurring(recurringAlarmId)
            .onFailure { _alarmError.value = it }
            .getOrDefault(emptyList())
    }

    // Alarm data keyed by alarm document ID, for rendering symbol overlays
    private val _alarmCache = MutableLiveData<Map<String, Alarm>>(emptyMap())
    val alarmCache: LiveData<Map<String, Alarm>> = _alarmCache

    // Recurring alarm cache: maps recurrence ID → current instance alarm
    private val _recurringAlarmCache = MutableLiveData<Map<String, Alarm>>(emptyMap())
    val recurringAlarmCache: LiveData<Map<String, Alarm>> = _recurringAlarmCache

    /**
     * Loads all alarms referenced by alarm directives in the current note's lines.
     * Handles both [alarm("id")] and [recurringAlarm("id")] directives.
     * For recurring directives, fetches the RecurringAlarm → currentAlarmId → alarm instance.
     */
    fun loadAlarmStates() {
        val extracted = extractAlarmIds(lineTracker.getTrackedLines())
        if (extracted.alarmIds.isEmpty() && extracted.recurringAlarmIds.isEmpty()) {
            _alarmCache.value = emptyMap()
            _recurringAlarmCache.value = emptyMap()
            return
        }
        viewModelScope.launch {
            coroutineScope {
                // Load direct alarm references and recurring alarm references in parallel
                val directDeferred = async {
                    if (extracted.alarmIds.isNotEmpty()) {
                        alarmRepository.getAlarmsByIds(extracted.alarmIds).fold(
                            onSuccess = { it },
                            onFailure = { e ->
                                Log.e(TAG, "Error loading alarm states", e)
                                emptyMap()
                            }
                        )
                    } else {
                        emptyMap()
                    }
                }

                val recurringDeferred = async {
                    if (extracted.recurringAlarmIds.isNotEmpty()) {
                        // Resolve each recurring alarm in parallel
                        val entries = extracted.recurringAlarmIds.map { recId ->
                            async { recId to alarmRepository.resolveCurrentInstance(recId) }
                        }.awaitAll()
                        entries.filter { it.second != null }.associate { it.first to it.second!! }
                    } else {
                        emptyMap()
                    }
                }

                _alarmCache.value = directDeferred.await()
                _recurringAlarmCache.value = recurringDeferred.await()
            }
        }
    }

    /**
     * Fetches the current alarm instance for a recurring alarm ID.
     * Used when tapping a [recurringAlarm("id")] directive.
     */
    fun fetchRecurringAlarmInstance(recurringAlarmId: String, onComplete: (Alarm?) -> Unit) {
        viewModelScope.launch {
            onComplete(alarmRepository.resolveCurrentInstance(recurringAlarmId))
        }
    }

    /**
     * Returns [SymbolOverlay] list for each alarm directive on the given line.
     * Delegates to pure [computeSymbolOverlays] in ViewModelPureLogic.
     */
    fun getSymbolOverlays(
        lineIndex: Int,
        lineContent: String,
        alarmCache: Map<String, Alarm>,
        recurringAlarmCache: Map<String, Alarm>
    ): List<SymbolOverlay> =
        computeSymbolOverlays(lineContent, alarmCache, recurringAlarmCache, Timestamp.now())

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
        dueTime: Timestamp?,
        stages: List<AlarmStage> = Alarm.DEFAULT_STAGES
    ) {
        viewModelScope.launch {
            updateTrackedLines(content)
            val trackedLines = lineTracker.getTrackedLines()
            val saveResult = persistCurrentNote(trackedLines)

            saveResult.onSuccess {
                createAlarmInternal(lineContent, lineIndex, dueTime, stages)
            }
        }
    }

    /**
     * Saves content, then creates a recurring alarm template and its first instance.
     */
    fun saveAndCreateRecurringAlarm(
        content: String,
        lineContent: String,
        lineIndex: Int? = null,
        dueTime: Timestamp?,
        stages: List<AlarmStage> = Alarm.DEFAULT_STAGES,
        recurrenceConfig: RecurrenceConfig
    ) {
        viewModelScope.launch {
            updateTrackedLines(content)
            val trackedLines = lineTracker.getTrackedLines()
            val saveResult = persistCurrentNote(trackedLines)

            saveResult.onSuccess {
                createRecurringAlarmInternal(
                    lineContent, lineIndex, dueTime, stages, recurrenceConfig
                )
            }
        }
    }

    private suspend fun createRecurringAlarmInternal(
        lineContent: String,
        lineIndex: Int?,
        dueTime: Timestamp?,
        stages: List<AlarmStage>,
        recurrenceConfig: RecurrenceConfig
    ) {
        val noteId = if (lineIndex != null) getNoteIdForLine(lineIndex) else currentNoteId
        val context = getApplication<Application>()

        // Create the recurring alarm template
        val recurringAlarm = RecurrenceConfigMapper.toRecurringAlarm(
            noteId = noteId,
            lineContent = lineContent,
            dueTime = dueTime,
            stages = stages,
            config = recurrenceConfig
        )

        val recurringRepo = recurringAlarmRepository
        val createResult = recurringRepo.create(recurringAlarm)
        val recurringId = createResult.getOrNull()
        if (recurringId == null) {
            val cause = createResult.exceptionOrNull()
            Log.e(TAG, "Failed to create recurring alarm", cause)
            _alarmError.value = cause ?: Exception("Failed to create recurring alarm")
            return
        }

        // Create the first alarm instance directly with the dialog's due time and stages
        val firstAlarm = Alarm(
            noteId = noteId,
            lineContent = lineContent,
            dueTime = dueTime,
            stages = stages,
            recurringAlarmId = recurringId
        )

        // Check permissions
        if (!alarmScheduler.canScheduleExactAlarms()) {
            _alarmPermissionWarning.value = true
        }
        if (!PermissionHelper.hasNotificationPermission(context)) {
            _notificationPermissionWarning.value = true
        }

        alarmStateManager.create(firstAlarm).fold(
            onSuccess = { (alarmId, scheduleResult) ->
                // Update recurring alarm with current instance ID
                recurringRepo.updateCurrentAlarmId(recurringId, alarmId, null)

                if (!scheduleResult.success) {
                    _schedulingWarning.value = scheduleResult.message
                }

                val alarmSnapshot = AlarmSnapshot(
                    id = alarmId,
                    noteId = firstAlarm.noteId,
                    lineContent = firstAlarm.lineContent,
                    dueTime = firstAlarm.dueTime,
                    stages = firstAlarm.stages
                )

                _alarmCreated.value = AlarmCreatedEvent(alarmId, lineContent, alarmSnapshot, recurringAlarmId = recurringId)
                loadAlarmStates()
            },
            onFailure = { e ->
                // Clean up the recurring alarm if first instance creation failed
                recurringRepo.delete(recurringId)
                _alarmError.value = e
            }
        )
    }

    /**
     * Creates a new alarm for the current line.
     * Uses the line tracker to get the note ID for the current line.
     */
    fun createAlarm(
        lineContent: String,
        lineIndex: Int? = null,
        dueTime: Timestamp?,
        stages: List<AlarmStage> = Alarm.DEFAULT_STAGES
    ) {
        viewModelScope.launch {
            createAlarmInternal(lineContent, lineIndex, dueTime, stages)
        }
    }

    private suspend fun createAlarmInternal(
        lineContent: String,
        lineIndex: Int?,
        dueTime: Timestamp?,
        stages: List<AlarmStage>
    ) {
        // Use the note ID from line tracker if available
        val noteId = if (lineIndex != null) getNoteIdForLine(lineIndex) else currentNoteId

        val alarm = Alarm(
            noteId = noteId,
            lineContent = lineContent,
            dueTime = dueTime,
            stages = stages
        )

        // Check permissions and show warnings
        val context = getApplication<Application>()
        if (!alarmScheduler.canScheduleExactAlarms()) {
            _alarmPermissionWarning.value = true
        }
        if (!PermissionHelper.hasNotificationPermission(context)) {
            _notificationPermissionWarning.value = true
        }

        alarmStateManager.create(alarm).fold(
            onSuccess = { (alarmId, scheduleResult) ->
                if (!scheduleResult.success) {
                    _schedulingWarning.value = scheduleResult.message
                }

                val alarmSnapshot = AlarmSnapshot(
                    id = alarmId,
                    noteId = alarm.noteId,
                    lineContent = alarm.lineContent,
                    dueTime = alarm.dueTime,
                    stages = alarm.stages
                )

                // Signal to insert alarm symbol (even if scheduling partially failed,
                // the alarm exists in the DB)
                _alarmCreated.value = AlarmCreatedEvent(alarmId, lineContent, alarmSnapshot)
                loadAlarmStates()
            },
            onFailure = { e ->
                _alarmError.value = e
            }
        )
    }

    // region Directive Execution

    /**
     * Loads notes for use in find() operations within directives.
     * Notes are cached and reused until explicitly refreshed.
     * Also caches the current note for [.] reference (Milestone 6).
     * Call this before executing directives that may use find().
     */
    private suspend fun ensureNotesLoaded(): List<Note> {
        Log.d(TAG, "ensureNotesLoaded: cachedNotes=${cachedNotes?.size}, cachedCurrentNote=${cachedCurrentNote?.id}, currentNoteId=$currentNoteId")

        // Only skip loading if both notes AND current note are cached
        if (cachedNotes != null && cachedCurrentNote != null) {
            Log.d(TAG, "ensureNotesLoaded: returning cached (notes=${cachedNotes?.size}, currentNote=${cachedCurrentNote?.id})")
            return cachedNotes!!
        }

        // Load notes if not cached
        if (cachedNotes == null) {
            Log.d(TAG, "ensureNotesLoaded: FETCHING FRESH from Firestore...")
            // Use loadNotesWithFullContent for directives that need complete note text (e.g., view())
            val result = repository.loadNotesWithFullContent()
            val notes = result.getOrNull() ?: emptyList()
            cachedNotes = notes
            cachedCurrentNote = notes.find { it.id == currentNoteId }
            Log.d(TAG, "ensureNotesLoaded: FETCHED ${notes.size} notes from Firestore, found currentNote in list: ${cachedCurrentNote != null}")
            // Log first few note contents for debugging
            notes.take(3).forEach { note ->
                Log.d(TAG, "ensureNotesLoaded: note ${note.id} content preview: '${note.content.take(40)}...'")
            }
        }

        // If current note not found in top-level notes (e.g., it's a child note), load it separately
        if (cachedCurrentNote == null) {
            Log.d(TAG, "ensureNotesLoaded: currentNote not in list, loading by ID: $currentNoteId")
            val loadResult = repository.loadNoteById(currentNoteId)
            cachedCurrentNote = loadResult.getOrNull()
            Log.d(TAG, "ensureNotesLoaded: loadNoteById result: ${cachedCurrentNote?.id}, error: ${loadResult.exceptionOrNull()?.message}")
        }

        Log.d(TAG, "ensureNotesLoaded: final cachedCurrentNote=${cachedCurrentNote?.id}")
        return cachedNotes ?: emptyList()
    }

    /**
     * Refreshes the cached notes for find() operations.
     * Also updates the cached current note for [.] reference (Milestone 6).
     * Call this when notes may have changed (e.g., after save).
     */
    private suspend fun refreshNotesCache(): List<Note> {
        // Use loadNotesWithFullContent for directives that need complete note text (e.g., view())
        val result = repository.loadNotesWithFullContent()
        val notes = result.getOrNull() ?: emptyList()
        cachedNotes = notes
        cachedCurrentNote = notes.find { it.id == currentNoteId }

        // If current note not found in top-level notes (e.g., it's a child note), load it separately
        if (cachedCurrentNote == null) {
            cachedCurrentNote = repository.loadNoteById(currentNoteId).getOrNull()
        }

        return notes
    }

    /**
     * Callback for when editor content should be updated due to directive mutations.
     * Set by CurrentNoteScreen to bridge ViewModel mutations to EditorController.
     *
     * Parameters:
     * - noteId: ID of the mutated note
     * - newContent: The new content from the note (first line only for notes with children)
     * - mutationType: The type of mutation that occurred
     * - alreadyPersisted: If true, the mutation was part of a save operation and is already
     *   persisted to Firestore, so isSaved should not be set to false
     * - appendedText: For CONTENT_APPENDED, the text that was appended (without leading newline)
     */
    var onEditorContentMutated: ((noteId: String, newContent: String, mutationType: MutationType, alreadyPersisted: Boolean, appendedText: String?) -> Unit)? = null

    /**
     * Process mutations that occurred during directive execution.
     * Updates the cache and notifies the UI if necessary.
     *
     * @param mutations List of mutations that occurred
     * @param alreadyPersisted If true, mutations are already persisted (e.g., during save)
     * @param skipEditorCallback If true, skip notifying the editor (e.g., during live execution
     *        where updating the editor could cause index issues with in-progress event handlers)
     * @return true if the current note was mutated (requiring undo history clear)
     */
    private fun processMutations(
        mutations: List<NoteMutation>,
        alreadyPersisted: Boolean = false,
        skipEditorCallback: Boolean = false
    ): Boolean {
        if (mutations.isEmpty()) return false

        var currentNoteMutated = false

        for (mutation in mutations) {
            Log.d(TAG, "Processing mutation: ${mutation.mutationType} on note ${mutation.noteId}, alreadyPersisted=$alreadyPersisted")

            // Update cache
            cachedNotes = cachedNotes?.map { note ->
                if (note.id == mutation.noteId) mutation.updatedNote else note
            }

            // Update current note cache if this note was mutated
            if (cachedCurrentNote?.id == mutation.noteId) {
                cachedCurrentNote = mutation.updatedNote
            }

            // If this mutation affects the note currently being edited, notify the UI
            if (mutation.noteId == currentNoteId) {
                currentNoteMutated = true
                // Only notify the editor if not skipping (e.g., during live execution)
                if (!skipEditorCallback) {
                    when (mutation.mutationType) {
                        MutationType.CONTENT_CHANGED, MutationType.CONTENT_APPENDED -> {
                            // Notify editor to update its content
                            onEditorContentMutated?.invoke(
                                mutation.noteId,
                                mutation.updatedNote.content,
                                mutation.mutationType,
                                alreadyPersisted,
                                mutation.appendedText
                            )
                        }
                        MutationType.PATH_CHANGED -> {
                            // Path changes don't affect editor content
                        }
                    }
                }
            }
        }

        return currentNoteMutated
    }

    /**
     * Execute directives in the content and update local state immediately.
     * Called when content changes (e.g., when user types a closing bracket).
     * Results are NOT persisted to Firestore until save.
     *
     * Uses UUID-based keys for stable identity across text edits.
     * UUIDs are preserved when directives move due to text insertions/deletions.
     */
    /**
     * Bumps the directive cache generation counter, triggering recomposition
     * so computeDirectiveResults() re-runs with current cache state.
     */
    fun bumpDirectiveCacheGeneration() {
        _directiveCacheGeneration.value = (_directiveCacheGeneration.value ?: 0) + 1
    }

    /**
     * Force re-execute all directives with fresh data from Firestore.
     * Used after inline editing a viewed note to refresh the view.
     *
     * Refreshes notes cache, clears directive executor cache, and bumps
     * cache generation so computeDirectiveResults() re-runs with fresh data.
     */
    fun forceRefreshAllDirectives(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            refreshNotesCache()
            directiveCacheManager.clearAll()
            bumpDirectiveCacheGeneration()
            onComplete?.invoke()
        }
    }

    /**
     * Execute all directives in the content and store results in Firestore.
     * Called after note save succeeds.
     *
     * Refreshes notes cache, executes directives via the cached executor,
     * stores results in Firestore keyed by text hash, and bumps cache generation.
     */
    private fun executeAndStoreDirectives(content: String) {
        if (!DirectiveFinder.containsDirectives(content)) {
            return
        }

        viewModelScope.launch {
            // Refresh notes cache (notes may have changed after save)
            val notes = refreshNotesCache()

            // Notify observers that notes cache was refreshed (for view directive updates)
            _notesCacheRefreshed.tryEmit(Unit)

            // Execute all directives and collect mutations
            val allMutations = mutableListOf<NoteMutation>()
            for (line in content.lines()) {
                for (directive in DirectiveFinder.findDirectives(line)) {
                    val cachedResult = cachedDirectiveExecutor.execute(
                        directive.sourceText, notes, cachedCurrentNote, noteOperations
                    )
                    allMutations.addAll(cachedResult.mutations)
                    registerRefreshTriggersIfNeeded(directive.sourceText, cachedCurrentNote?.id)

                    // Store in Firestore using text hash (skip view/alarm results)
                    val resultValue = cachedResult.result.toValue()
                    val isViewResult = resultValue is ViewVal
                    val isAlarmResult = resultValue is AlarmVal
                    if (!isViewResult && !isAlarmResult) {
                        val textHash = DirectiveResult.hashDirective(directive.sourceText)
                        directiveResultRepository.saveResult(currentNoteId, textHash, cachedResult.result)
                            .onFailure { e ->
                                Log.e(TAG, "Failed to save directive result: $textHash", e)
                            }
                    }
                }
            }

            // Process any mutations that occurred (alreadyPersisted=true since this runs during save)
            processMutations(allMutations, alreadyPersisted = true)

            // Register any schedule directives found in this note
            cachedCurrentNote?.let { note ->
                ScheduleManager.registerSchedulesFromNote(note)
            }

            // Invalidate notes cache — Firestore eventual consistency means
            // refreshNotesCache() above may have fetched stale data.
            cachedNotes = null
            MetadataHasher.invalidateCache()

            bumpDirectiveCacheGeneration()
        }
    }

    /**
     * Loads cached directive results from Firestore into the L1 cache,
     * then executes any directives that aren't cached. Called when note is loaded.
     *
     * Alarm directives are trivial pure functions — computeDirectiveResults handles
     * them synchronously, so no pre-population is needed.
     */
    private suspend fun loadDirectiveResults(content: String) {
        if (!DirectiveFinder.containsDirectives(content)) {
            _directiveCacheGeneration.postValue((_directiveCacheGeneration.value ?: 0) + 1)
            return
        }

        // Load cached results from Firestore (keyed by text hash)
        val cachedByHash = directiveResultRepository.getResults(currentNoteId)
            .getOrElse { e ->
                Log.e(TAG, "Failed to load directive results", e)
                emptyMap()
            }

        // Prime the L1 cache with Firestore results (skip stale/unreliable types)
        val directiveTexts = mutableSetOf<String>()
        for (line in content.lines()) {
            for (directive in DirectiveFinder.findDirectives(line)) {
                directiveTexts.add(directive.sourceText)
            }
        }

        val missingTexts = mutableListOf<String>()
        for (sourceText in directiveTexts) {
            val textHash = DirectiveResult.hashDirective(sourceText)
            val cached = cachedByHash[textHash]
            val cachedValue = cached?.toValue()
            val cachedResultType = cached?.result?.get("type") as? String

            // Skip view/temporal/alarm results — re-execute these fresh
            val isViewResult = cachedValue is ViewVal || cachedResultType == "view"
            val isBareTemporalResult = cachedValue is DateVal ||
                    cachedValue is TimeVal ||
                    cachedValue is DateTimeVal ||
                    cachedResultType in listOf("date", "time", "datetime")
            val isAlarmResult = cachedValue is AlarmVal || cachedResultType == "alarm"

            if (cached != null && !isViewResult && !isBareTemporalResult && !isAlarmResult) {
                // Prime L1 cache with this Firestore result
                cachedDirectiveExecutor.primeCache(sourceText, currentNoteId, cached)
            } else {
                missingTexts.add(sourceText)
            }
        }

        if (missingTexts.isEmpty()) {
            _directiveCacheGeneration.postValue((_directiveCacheGeneration.value ?: 0) + 1)
            return
        }

        // Load notes and execute missing directives
        val notes = ensureNotesLoaded()
        val allMutations = mutableListOf<NoteMutation>()
        for (sourceText in missingTexts) {
            val cachedResult = cachedDirectiveExecutor.execute(sourceText, notes, cachedCurrentNote, noteOperations)
            allMutations.addAll(cachedResult.mutations)
            registerRefreshTriggersIfNeeded(sourceText, cachedCurrentNote?.id)

            // Store in Firestore (skip view/alarm results)
            val resultValue = cachedResult.result.toValue()
            val isViewResult = resultValue is ViewVal
            val isAlarmResult = resultValue is AlarmVal
            if (!isViewResult && !isAlarmResult) {
                val textHash = DirectiveResult.hashDirective(sourceText)
                directiveResultRepository.saveResult(currentNoteId, textHash, cachedResult.result)
                    .onFailure { e ->
                        Log.e(TAG, "Failed to save directive result: $textHash", e)
                    }
            }
        }
        processMutations(allMutations, skipEditorCallback = true)

        _directiveCacheGeneration.postValue((_directiveCacheGeneration.value ?: 0) + 1)
    }

    /**
     * Toggle the collapsed/expanded state of a directive.
     *
     * @param sourceText The source text of the directive (e.g., "[42]")
     */
    fun toggleDirectiveCollapsed(sourceText: String) {
        val hash = DirectiveResult.hashDirective(sourceText)
        if (hash in expandedDirectiveHashes) {
            expandedDirectiveHashes.remove(hash)
        } else {
            expandedDirectiveHashes.add(hash)
        }
        bumpDirectiveCacheGeneration()
    }

    /**
     * Re-executes a directive and collapses it.
     * Called when user confirms a directive edit (including when no changes were made).
     * This ensures dynamic directives like [now] get fresh values on confirm.
     *
     * @param sourceText The source text of the directive (e.g., "[now]")
     */
    fun confirmDirective(sourceText: String) {
        val hash = DirectiveResult.hashDirective(sourceText)
        expandedDirectiveHashes.remove(hash)
        // Clear L1 cache so computeDirectiveResults re-executes with fresh value
        cachedDirectiveExecutor.clearCacheEntry(sourceText, currentNoteId)
        bumpDirectiveCacheGeneration()
    }

    /**
     * Re-executes a directive and keeps it expanded.
     * Called when user refreshes a directive edit (recompute without closing).
     *
     * @param sourceText The source text of the directive (e.g., "[now]")
     */
    fun refreshDirective(sourceText: String) {
        // Keep expanded — hash stays in expandedDirectiveHashes
        // Clear L1 cache so computeDirectiveResults re-executes with fresh value
        cachedDirectiveExecutor.clearCacheEntry(sourceText, currentNoteId)
        bumpDirectiveCacheGeneration()
    }

    /**
     * Executes a button directive's action.
     * Called when user clicks a button rendered from a ButtonVal.
     *
     * Note: The buttonVal passed here has a placeholder lambda (lambdas can't be serialized).
     * We need to re-parse the source text to get the real lambda for execution.
     *
     * @param directiveKey Directive key (e.g., "noteId:15")
     * @param buttonVal The ButtonVal (used for label display, action is placeholder)
     * @param sourceText The original directive source text to re-parse for execution
     */
    fun executeButton(directiveKey: String, buttonVal: ButtonVal, sourceText: String? = null) {
        viewModelScope.launch {
            ensureNotesLoaded()

            // Set loading state
            updateButtonState(directiveKey, ButtonExecutionState.LOADING)

            try {
                // Create fresh environment for button execution with current context
                // This ensures mutations are properly captured
                val env = Environment(
                    NoteContext(
                        notes = cachedNotes ?: emptyList(),
                        currentNote = cachedCurrentNote,
                        noteOperations = noteOperations
                    )
                )

                // Re-parse the directive source to get the fresh ButtonVal with real lambda
                // The deserialized buttonVal has a placeholder lambda that can't execute the real action
                if (sourceText != null) {
                    val tokens = Lexer(sourceText).tokenize()
                    val directive = Parser(tokens, sourceText).parseDirective()
                    val executor = Executor()
                    val freshValue = executor.execute(directive, env)

                    // The freshValue should be a ButtonVal with the real lambda
                    if (freshValue is ButtonVal) {
                        // Execute the button's lambda in the environment
                        executor.evaluate(freshValue.action.body, env)
                    } else {
                        Log.w(TAG, "Re-parsed directive did not produce ButtonVal: ${freshValue::class.simpleName}")
                    }
                } else {
                    // Fallback: try to use the placeholder (will likely fail or do nothing)
                    Log.w(TAG, "No sourceText provided, using placeholder lambda")
                    val executor = Executor()
                    executor.evaluate(buttonVal.action.body, env)
                }

                // Get and process mutations from the fresh environment
                // For button clicks, we DO want to update the editor since we're not
                // in the middle of an editing operation (unlike live directive execution)
                val mutations = env.getMutations()
                if (mutations.isNotEmpty()) {
                    processMutations(mutations, skipEditorCallback = false)
                }

                // Clear any previous error and flash success state
                clearButtonError(directiveKey)
                updateButtonState(directiveKey, ButtonExecutionState.SUCCESS)
                delay(500)
                updateButtonState(directiveKey, ButtonExecutionState.IDLE)
            } catch (e: Exception) {
                Log.e(TAG, "Button execution failed: ${e.message}", e)
                // Keep button in error state (don't reset to IDLE)
                updateButtonState(directiveKey, ButtonExecutionState.ERROR)

                // Store the error message for display
                val current = _buttonErrors.value?.toMutableMap() ?: mutableMapOf()
                current[directiveKey] = e.message ?: "Unknown error"
                _buttonErrors.postValue(current)
            }
        }
    }

    /**
     * Updates the execution state for a button directive.
     */
    private fun updateButtonState(directiveKey: String, state: ButtonExecutionState) {
        val current = _buttonExecutionStates.value?.toMutableMap() ?: mutableMapOf()
        if (state == ButtonExecutionState.IDLE) {
            current.remove(directiveKey)
        } else {
            current[directiveKey] = state
        }
        _buttonExecutionStates.value = current
    }

    /**
     * Compute directive results synchronously using the CachedDirectiveExecutor.
     * Results are keyed by directiveHash(sourceText) — same key the rendering layer uses.
     * Cache hits return instantly; misses execute and cache for next call.
     *
     * Applies collapsed state from [expandedDirectiveHashes].
     * When notes haven't loaded yet, still returns alarm results (trivial pure functions).
     */
    fun computeDirectiveResults(content: String): Map<String, DirectiveResult> {
        val notes = cachedNotes
        val currentNote = cachedCurrentNote

        val hashResults = mutableMapOf<String, DirectiveResult>()
        for (line in content.lines()) {
            for (directive in DirectiveFinder.findDirectives(line)) {
                val hash = DirectiveResult.hashDirective(directive.sourceText)
                if (hashResults.containsKey(hash)) continue

                val result = if (notes != null) {
                    cachedDirectiveExecutor.execute(
                        directive.sourceText, notes, currentNote, noteOperations
                    ).result
                } else {
                    // Notes not loaded yet — only alarm directives can be resolved
                    val alarmId = DirectiveSegment.Directive.alarmIdFromSource(directive.sourceText)
                        ?: DirectiveSegment.Directive.recurringAlarmIdFromSource(directive.sourceText)
                    if (alarmId != null) DirectiveResult.success(AlarmVal(alarmId)) else continue
                }

                val collapsed = hash !in expandedDirectiveHashes
                hashResults[hash] = result.copy(collapsed = collapsed)
            }
        }
        return hashResults
    }

    /**
     * Gets a snapshot of currently expanded directive hashes.
     * Used before undo/redo to preserve expanded state.
     */
    fun getExpandedDirectiveHashes(): Set<String> = expandedDirectiveHashes.toSet()

    /**
     * Restores expanded state from a snapshot of directive hashes.
     * Called after undo/redo to preserve edit row state.
     */
    fun restoreExpandedDirectiveHashes(hashes: Set<String>) {
        expandedDirectiveHashes.clear()
        expandedDirectiveHashes.addAll(hashes)
        bumpDirectiveCacheGeneration()
    }

    /**
     * Execute directives for arbitrary content (e.g., a viewed note being edited inline).
     * Returns directive results keyed by "lineId:startOffset" for use in rendering.
     *
     * This is used when inline editing a viewed note to render its directives properly.
     * The results are separate from the main note's directive results.
     *
     * @param content The content to parse and execute directives for
     * @param onResults Callback with the results when execution completes
     */
    fun executeDirectivesForContent(content: String, onResults: (Map<String, DirectiveResult>) -> Unit) {
        if (!DirectiveFinder.containsDirectives(content)) {
            onResults(emptyMap())
            return
        }

        viewModelScope.launch {
            val notes = ensureNotesLoaded()

            val results = mutableMapOf<String, DirectiveResult>()
            for (line in content.lines()) {
                for (directive in DirectiveFinder.findDirectives(line)) {
                    val hash = DirectiveResult.hashDirective(directive.sourceText)
                    if (results.containsKey(hash)) continue
                    try {
                        val cachedResult = cachedDirectiveExecutor.execute(
                            directive.sourceText, notes, cachedCurrentNote, null
                        )
                        results[hash] = cachedResult.result
                    } catch (e: Exception) {
                        Log.e(TAG, "executeDirectivesForContent: error executing '${directive.sourceText}'", e)
                        results[hash] = DirectiveResult.failure(e.message ?: "Execution error")
                    }
                }
            }

            onResults(results)
        }
    }

    /**
     * Execute a single directive and return its result.
     * Used for refresh/confirm actions in inline directive editing.
     *
     * @param sourceText The directive source text (e.g., "[add(1,2)]")
     * @param onResult Callback with the result when execution completes
     */
    fun executeSingleDirective(sourceText: String, onResult: (DirectiveResult) -> Unit) {
        viewModelScope.launch {
            val notes = ensureNotesLoaded()
            val cachedResult = cachedDirectiveExecutor.execute(
                sourceText, notes, cachedCurrentNote, null // null noteOperations = read-only
            )
            Log.d(TAG, "executeSingleDirective: executed '$sourceText'")
            onResult(cachedResult.result)
        }
    }

    /**
     * Start an edit session for inline editing within a view.
     * Call this when the user starts editing content that belongs to another note
     * (e.g., editing a note displayed in a view directive).
     *
     * This suppresses cache invalidation for the current note during editing
     * to prevent UI flicker.
     *
     * @param editedNoteId The ID of the note being edited
     */
    fun startInlineEditSession(editedNoteId: String) {
        editSessionManager.startEditSession(
            editedNoteId = editedNoteId,
            originatingNoteId = currentNoteId
        )
        Log.d(TAG, "Started inline edit session: editing $editedNoteId from $currentNoteId")
    }

    /**
     * End the current inline edit session.
     * Call this when the user finishes editing (blur, save, navigation).
     * This will apply any pending cache invalidations and refresh affected views.
     */
    fun endInlineEditSession() {
        if (editSessionManager.isEditSessionActive()) {
            editSessionManager.endEditSession()
            Log.d(TAG, "Ended inline edit session")
        }
    }

    /**
     * Check if an inline edit session is currently active.
     */
    fun isInlineEditSessionActive(): Boolean = editSessionManager.isEditSessionActive()

    /**
     * Saves the content of a note edited inline within a view directive.
     * This is a simple content update that doesn't affect child note structure.
     *
     * The method:
     * 1. Starts an inline edit session to suppress cache invalidation during edit
     * 2. Updates the note content in Firestore
     * 3. Invalidates the directive cache so views refresh
     * 4. Ends the edit session which triggers view refresh
     *
     * @param noteId The ID of the note to update
     * @param newContent The new content for the note
     * @param onSuccess Optional callback when save succeeds
     * @param onFailure Optional callback when save fails
     */
    fun saveInlineNoteContent(
        noteId: String,
        newContent: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        val lines = newContent.lines()
        Log.d("InlineEditCache", "=== saveInlineNoteContent START ===")
        Log.d("InlineEditCache", "noteId=$noteId")
        Log.d("InlineEditCache", "content has ${lines.size} lines, first='${lines.firstOrNull()}'")
        Log.d("InlineEditCache", "full content: '${newContent.take(100).replace("\n", "\\n")}...'")
        Log.d("InlineEditCache", "cachedNotes was ${if (cachedNotes == null) "NULL" else "${cachedNotes?.size} notes"}")

        viewModelScope.launch {
            startInlineEditSession(noteId)

            Log.d("InlineEditCache", "saveInlineNoteContent: calling repository.saveNoteWithFullContent...")
            // Use saveNoteWithFullContent to properly handle multi-line notes
            // This preserves child note IDs and handles line additions/deletions
            //
            // Phase 2 (Caching Audit): AWAIT save completion before clearing caches.
            // This ensures that when we refresh, Firestore has the new data.
            val saveResult = repository.saveNoteWithFullContent(noteId, newContent)

            saveResult
                .onSuccess {
                    Log.d("InlineEditCache", "=== saveInlineNoteContent SUCCESS for $noteId ===")

                    // OPTIMISTIC UPDATE: Replace the saved note's content in the cache
                    // instead of clearing entirely. This prevents UI from showing stale
                    // content if it re-renders before forceRefreshAllDirectives completes.
                    Log.d("InlineEditCache", "saveInlineNoteContent: OPTIMISTIC UPDATE of cachedNotes...")
                    cachedNotes = cachedNotes?.map { note ->
                        if (note.id == noteId) {
                            Log.d("InlineEditCache", "  Updated note $noteId in cache with new content")
                            note.copy(content = newContent)
                        } else note
                    }
                    // Clear directive cache so they re-execute with the optimistic content
                    MetadataHasher.invalidateCache()
                    directiveCacheManager.clearAll()
                    Log.d("InlineEditCache", "saveInlineNoteContent: directive caches cleared, notes cache has optimistic content")

                    // DO NOT end session here - the caller must end it AFTER
                    // forceRefreshAllDirectives completes to avoid stale content.
                    // See the onComplete callback in forceRefreshAllDirectives.
                    Log.d("InlineEditCache", "saveInlineNoteContent: NOT ending session here (caller will end after refresh)")

                    onSuccess?.invoke()
                    Log.d("InlineEditCache", "saveInlineNoteContent: onSuccess callback DONE")
                }
                .onFailure { e ->
                    Log.e("InlineEditCache", "=== saveInlineNoteContent FAILED for $noteId ===", e)

                    // Abort edit session on failure (don't apply pending invalidations)
                    editSessionManager.abortEditSession()

                    onFailure?.invoke(e)
                }
        }
    }

    /**
     * Register refresh triggers for a directive if it contains refresh[...].
     * This enables automatic cache invalidation at computed trigger times.
     */
    private fun registerRefreshTriggersIfNeeded(sourceText: String, noteId: String?) {
        if (!sourceText.contains("refresh[")) return

        try {
            val tokens = Lexer(sourceText).tokenize()
            val directive = Parser(tokens, sourceText).parseDirective()

            // Find RefreshExpr in the AST
            val refreshExpr = findRefreshExpr(directive.expression)
            if (refreshExpr != null) {
                val analysis = RefreshTriggerAnalyzer.analyze(refreshExpr)
                if (analysis.success && analysis.triggers.isNotEmpty()) {
                    val cacheKey = DirectiveResult.hashDirective(sourceText)
                    refreshScheduler.register(cacheKey, noteId, analysis.triggers)
                    Log.d(TAG, "Registered ${analysis.triggers.size} refresh triggers for directive")
                }
            }
        } catch (e: Exception) {
            // Ignore analysis errors - directive will still execute
            Log.w(TAG, "Failed to analyze refresh triggers: ${e.message}")
        }
    }

    /**
     * Find a RefreshExpr in an expression tree.
     */
    private fun findRefreshExpr(expr: org.alkaline.taskbrain.dsl.language.Expression): RefreshExpr? {
        return when (expr) {
            is RefreshExpr -> expr
            is org.alkaline.taskbrain.dsl.language.StatementList -> {
                expr.statements.asSequence()
                    .mapNotNull { findRefreshExpr(it) }
                    .firstOrNull()
            }
            is org.alkaline.taskbrain.dsl.language.Assignment -> findRefreshExpr(expr.value)
            else -> null
        }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        snapshotListener?.remove()
        refreshScheduler.stop()
    }

    companion object {
        private const val TAG = "CurrentNoteViewModel"
    }

    /**
     * Updates an existing alarm's due time and stages, then reschedules it.
     */
    fun updateAlarm(
        alarm: Alarm,
        dueTime: Timestamp?,
        stages: List<AlarmStage> = Alarm.DEFAULT_STAGES
    ) {
        viewModelScope.launch {
            alarmStateManager.update(alarm, dueTime, stages).fold(
                onSuccess = { scheduleResult ->
                    if (!scheduleResult.success) {
                        _schedulingWarning.value = scheduleResult.message
                    }
                    loadAlarmStates()
                },
                onFailure = { e ->
                    _alarmError.value = e
                }
            )
        }
    }

    /**
     * Updates an existing recurring alarm's thresholds and recurrence config.
     * Updates both the template and the current instance.
     */
    fun updateRecurringAlarm(
        alarm: Alarm,
        dueTime: Timestamp?,
        stages: List<AlarmStage> = Alarm.DEFAULT_STAGES,
        recurrenceConfig: RecurrenceConfig
    ) {
        viewModelScope.launch {
            val recurringAlarmId = alarm.recurringAlarmId
            if (recurringAlarmId == null) {
                // Not actually recurring — fall back to normal update
                updateAlarm(alarm, dueTime, stages)
                return@launch
            }

            val recurringRepo = recurringAlarmRepository
            val existing = recurringRepo.get(recurringAlarmId).getOrNull()
            if (existing == null) {
                Log.e(TAG, "Recurring alarm template not found: $recurringAlarmId")
                _alarmError.value = Exception("Recurring alarm template not found")
                return@launch
            }

            // Update the template with new recurrence config
            val updatedTemplate = RecurrenceConfigMapper.toRecurringAlarm(
                noteId = alarm.noteId,
                lineContent = alarm.lineContent,
                dueTime = dueTime,
                stages = stages,
                config = recurrenceConfig
            ).copy(
                id = existing.id,
                userId = existing.userId,
                completionCount = existing.completionCount,
                lastCompletionDate = existing.lastCompletionDate,
                currentAlarmId = existing.currentAlarmId,
                status = if (recurrenceConfig.enabled) existing.status
                         else org.alkaline.taskbrain.data.RecurringAlarmStatus.ENDED,
                createdAt = existing.createdAt
            )

            recurringRepo.update(updatedTemplate).fold(
                onSuccess = {
                    // Also update the current alarm instance
                    alarmStateManager.update(alarm, dueTime, stages).fold(
                        onSuccess = { scheduleResult ->
                            if (!scheduleResult.success) {
                                _schedulingWarning.value = scheduleResult.message
                            }
                            loadAlarmStates()
                        },
                        onFailure = { e -> _alarmError.value = e }
                    )
                },
                onFailure = { e -> _alarmError.value = e }
            )
        }
    }

    /**
     * Updates an instance's times, optionally propagating the change to the recurrence template.
     */
    fun updateInstanceTimes(
        alarm: Alarm,
        dueTime: Timestamp?,
        stages: List<AlarmStage>,
        alsoUpdateRecurrence: Boolean
    ) {
        viewModelScope.launch {
            templateManager.updateInstanceTimes(alarm, dueTime, stages, alsoUpdateRecurrence).fold(
                onSuccess = { scheduleResult ->
                    if (!scheduleResult.success) {
                        _schedulingWarning.value = scheduleResult.message
                    }
                    loadAlarmStates()
                },
                onFailure = { e -> _alarmError.value = e }
            )
        }
    }

    /**
     * Updates the recurrence template's times and pattern, optionally propagating
     * time changes to all pending instances that still match the old template times.
     */
    fun updateRecurrenceTemplate(
        recurringAlarmId: String,
        dueTime: Timestamp?,
        stages: List<AlarmStage>,
        recurrenceConfig: RecurrenceConfig,
        alsoUpdateMatchingInstances: Boolean
    ) {
        viewModelScope.launch {
            templateManager.updateRecurrenceTemplate(
                recurringAlarmId, dueTime, stages, recurrenceConfig, alsoUpdateMatchingInstances
            ).fold(
                onSuccess = { loadAlarmStates() },
                onFailure = { e -> _alarmError.value = e }
            )
        }
    }

    /**
     * Marks an existing alarm as done.
     */
    fun markAlarmDone(alarmId: String) {
        viewModelScope.launch {
            alarmStateManager.markDone(alarmId).fold(
                onSuccess = { loadAlarmStates() },
                onFailure = { e -> _alarmError.value = e }
            )
        }
    }

    /**
     * Cancels an existing alarm (sets status to CANCELLED).
     */
    fun cancelAlarm(alarmId: String) {
        viewModelScope.launch {
            alarmStateManager.markCancelled(alarmId).fold(
                onSuccess = { loadAlarmStates() },
                onFailure = { e -> _alarmError.value = e }
            )
        }
    }

    fun reactivateAlarm(alarmId: String) {
        viewModelScope.launch {
            alarmStateManager.reactivate(alarmId).fold(
                onSuccess = { loadAlarmStates() },
                onFailure = { e -> _alarmError.value = e }
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
     * Deletes an alarm permanently (for undo operation).
     * This completely removes the alarm from Firestore and cancels any scheduled notifications.
     * Sets isAlarmOperationPending during the operation to prevent race conditions.
     */
    fun deleteAlarmPermanently(alarmId: String, onComplete: (() -> Unit)? = null) {
        _isAlarmOperationPending.value = true
        viewModelScope.launch {
            alarmStateManager.delete(alarmId).fold(
                onSuccess = {
                    onComplete?.invoke()
                    loadAlarmStates()
                },
                onFailure = { e -> _alarmError.value = e }
            )
            _isAlarmOperationPending.value = false
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
                dueTime = alarmSnapshot.dueTime,
                stages = alarmSnapshot.stages
            )

            alarmStateManager.create(alarm).fold(
                onSuccess = { (newAlarmId, _) ->
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
    data class Success(
        val content: String,
        val lineNoteIds: List<List<String>> = emptyList()
    ) : LoadStatus()
    data class Error(val throwable: Throwable) : LoadStatus()
}

/**
 * Event emitted when an alarm is successfully created.
 */
data class AlarmCreatedEvent(
    val alarmId: String,
    val lineContent: String,
    val alarmSnapshot: AlarmSnapshot? = null,
    val recurringAlarmId: String? = null
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
