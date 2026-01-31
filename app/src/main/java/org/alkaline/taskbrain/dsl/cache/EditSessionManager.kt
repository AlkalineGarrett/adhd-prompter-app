package org.alkaline.taskbrain.dsl.cache

import android.util.Log

/**
 * Tracks the context of an active inline edit session.
 *
 * Phase 8: Inline editing support.
 *
 * When a user edits content within a view (e.g., editing Note B while viewing Note A),
 * this context tracks the edit to suppress cache invalidation for the originating note,
 * preventing UI flicker during editing.
 */
data class EditContext(
    /** The note whose content is being edited (e.g., Note B) */
    val editedNoteId: String,

    /** The note where the user initiated the edit (e.g., Note A containing the view) */
    val originatingNoteId: String,

    /** Timestamp when the edit session started */
    val editStartTime: Long = System.currentTimeMillis()
) {
    /**
     * Check if a given note is the originating note of this edit.
     * Used to determine if cache invalidation should be suppressed.
     */
    fun isOriginatingNote(noteId: String): Boolean = noteId == originatingNoteId

    /**
     * Check if a given note is the note being edited.
     */
    fun isEditedNote(noteId: String): Boolean = noteId == editedNoteId

    /**
     * Check if this edit session has been active for too long.
     * Used as a safety fallback to prevent stale caches.
     *
     * @param maxDurationMs Maximum edit duration in milliseconds (default 5 minutes)
     */
    fun isExpired(maxDurationMs: Long = DEFAULT_MAX_DURATION_MS): Boolean {
        return System.currentTimeMillis() - editStartTime > maxDurationMs
    }

    companion object {
        /** Default maximum edit session duration: 5 minutes */
        const val DEFAULT_MAX_DURATION_MS = 5 * 60 * 1000L
    }
}

/**
 * Reason for a cache invalidation.
 */
enum class InvalidationReason {
    /** Note content changed */
    CONTENT_CHANGED,

    /** Note was deleted */
    NOTE_DELETED,

    /** Note was created */
    NOTE_CREATED,

    /** Note metadata changed (path, etc.) */
    METADATA_CHANGED,

    /** External refresh requested */
    MANUAL_REFRESH
}

/**
 * A pending cache invalidation that was deferred during an edit session.
 */
data class PendingInvalidation(
    /** The note whose cache should be invalidated */
    val noteId: String,

    /** Reason for the invalidation */
    val reason: InvalidationReason,

    /** When the invalidation was queued */
    val timestamp: Long = System.currentTimeMillis(),

    /** Optional: specific directive keys to invalidate (null = all directives in note) */
    val directiveKeys: Set<String>? = null
)

/**
 * Manages inline edit sessions and deferred cache invalidations.
 *
 * Phase 8: Inline editing support.
 *
 * Key responsibilities:
 * 1. Track active edit context (which note is being edited from where)
 * 2. Suppress cache invalidation for the originating note during editing
 * 3. Queue invalidations that occur during editing
 * 4. Apply queued invalidations when the edit session ends
 *
 * Usage:
 * ```kotlin
 * val manager = EditSessionManager(cacheManager)
 *
 * // When user starts editing Note B from Note A's view
 * manager.startEditSession(editedNoteId = "B", originatingNoteId = "A")
 *
 * // During editing, invalidations for Note A are suppressed
 * manager.shouldSuppressInvalidation("A")  // returns true
 * manager.shouldSuppressInvalidation("X")  // returns false
 *
 * // When edit ends (blur, save, navigation)
 * manager.endEditSession()  // applies queued invalidations
 * ```
 */
class EditSessionManager(
    private val cacheManager: DirectiveCacheManager? = null
) {
    companion object {
        private const val TAG = "EditSessionManager"
    }

    /** Current active edit context, or null if no edit in progress */
    private var activeEditContext: EditContext? = null

    /** Queue of invalidations deferred during the edit session */
    private val pendingInvalidations = mutableListOf<PendingInvalidation>()

    /** Listeners notified when edit session ends */
    private val sessionEndListeners = mutableListOf<() -> Unit>()

    /**
     * Start an edit session.
     *
     * @param editedNoteId The note being edited
     * @param originatingNoteId The note where the edit was initiated
     */
    fun startEditSession(editedNoteId: String, originatingNoteId: String) {
        // End any existing session first (Phase 4: with logging for observability)
        val existingContext = activeEditContext
        if (existingContext != null) {
            val pendingCount = pendingInvalidations.size
            Log.d(TAG, "Switching edit session: ending session for note '${existingContext.editedNoteId}' " +
                "(originated from '${existingContext.originatingNoteId}') with $pendingCount pending invalidations")
            endEditSession()
        }

        activeEditContext = EditContext(
            editedNoteId = editedNoteId,
            originatingNoteId = originatingNoteId
        )
        Log.d(TAG, "Started edit session: editing note '$editedNoteId' from '$originatingNoteId'")
    }

    /**
     * Check if an edit session is currently active.
     */
    fun isEditSessionActive(): Boolean = activeEditContext != null

    /**
     * Get the current edit context.
     */
    fun getEditContext(): EditContext? = activeEditContext

    /**
     * Check if cache invalidation should be suppressed for a given note.
     *
     * Invalidation is suppressed for the originating note of an active edit,
     * unless the edit session has expired.
     *
     * @param noteId The note to check
     * @return true if invalidation should be suppressed
     */
    fun shouldSuppressInvalidation(noteId: String): Boolean {
        val context = activeEditContext ?: return false

        // Don't suppress if session expired
        if (context.isExpired()) {
            return false
        }

        return context.isOriginatingNote(noteId)
    }

    /**
     * Request cache invalidation for a note.
     *
     * If an edit session is active and the note is the originating note,
     * the invalidation is queued. Otherwise, it's applied immediately.
     *
     * @param noteId The note to invalidate
     * @param reason Reason for invalidation
     * @param directiveKeys Optional specific directive keys to invalidate
     * @return true if invalidation was applied immediately, false if queued
     */
    fun requestInvalidation(
        noteId: String,
        reason: InvalidationReason,
        directiveKeys: Set<String>? = null
    ): Boolean {
        if (shouldSuppressInvalidation(noteId)) {
            // Queue the invalidation
            pendingInvalidations.add(
                PendingInvalidation(
                    noteId = noteId,
                    reason = reason,
                    directiveKeys = directiveKeys
                )
            )
            return false
        }

        // Apply immediately
        applyInvalidation(noteId, directiveKeys)
        return true
    }

    /**
     * End the current edit session.
     *
     * This applies all queued invalidations and clears the edit context.
     */
    fun endEditSession() {
        val context = activeEditContext ?: return

        // Apply all pending invalidations
        val pendingCount = pendingInvalidations.size
        Log.d(TAG, "Ending edit session for note '${context.editedNoteId}' " +
            "(originated from '${context.originatingNoteId}'): applying $pendingCount pending invalidations")
        for (invalidation in pendingInvalidations) {
            applyInvalidation(invalidation.noteId, invalidation.directiveKeys)
        }
        pendingInvalidations.clear()

        activeEditContext = null

        // Notify listeners
        for (listener in sessionEndListeners) {
            listener()
        }
    }

    /**
     * Abort the edit session without applying pending invalidations.
     *
     * Use this when the edit is cancelled (e.g., user presses Escape).
     */
    fun abortEditSession() {
        val context = activeEditContext
        if (context != null) {
            val discardedCount = pendingInvalidations.size
            Log.d(TAG, "Aborting edit session for note '${context.editedNoteId}' " +
                "(originated from '${context.originatingNoteId}'): discarding $discardedCount pending invalidations")
        }
        pendingInvalidations.clear()
        activeEditContext = null
    }

    /**
     * Get the number of pending invalidations.
     */
    fun pendingInvalidationCount(): Int = pendingInvalidations.size

    /**
     * Get a copy of the pending invalidations (for debugging/testing).
     */
    fun getPendingInvalidations(): List<PendingInvalidation> = pendingInvalidations.toList()

    /**
     * Add a listener that's called when the edit session ends.
     */
    fun addSessionEndListener(listener: () -> Unit) {
        sessionEndListeners.add(listener)
    }

    /**
     * Remove a session end listener.
     */
    fun removeSessionEndListener(listener: () -> Unit) {
        sessionEndListeners.remove(listener)
    }

    /**
     * Clear all listeners.
     */
    fun clearListeners() {
        sessionEndListeners.clear()
    }

    /**
     * Apply an invalidation to the cache.
     */
    private fun applyInvalidation(noteId: String, directiveKeys: Set<String>?) {
        cacheManager?.clearNote(noteId)
    }

    /**
     * Check and handle expired edit session.
     * Call this periodically to ensure sessions don't stay active too long.
     *
     * @return true if the session was expired and ended
     */
    fun checkAndHandleExpiredSession(): Boolean {
        val context = activeEditContext ?: return false

        if (context.isExpired()) {
            endEditSession()
            return true
        }

        return false
    }
}

/**
 * Extension for DirectiveCacheManager to support edit-aware invalidation.
 */
class EditAwareCacheManager(
    private val cacheManager: DirectiveCacheManager,
    private val editSessionManager: EditSessionManager
) {
    /**
     * Invalidate caches for changed notes, respecting edit session.
     *
     * @param changedNoteIds Notes that changed
     * @param reason Reason for the change
     */
    fun invalidateForChangedNotes(
        changedNoteIds: Set<String>,
        reason: InvalidationReason = InvalidationReason.CONTENT_CHANGED
    ) {
        for (noteId in changedNoteIds) {
            editSessionManager.requestInvalidation(noteId, reason)
        }
    }

    /**
     * Check if a cached result is valid, considering edit session.
     *
     * If we're the origin of an active edit, return the cached result
     * even if it would normally be stale.
     *
     * @param directiveKey Cache key for the directive
     * @param noteId Note containing the directive
     * @param usesSelfAccess Whether the directive uses self-access
     * @param currentNotes All current notes
     * @param currentNote The note containing this directive
     * @return Cached result if valid or suppressed, null otherwise
     */
    fun getIfValidOrSuppressed(
        directiveKey: String,
        noteId: String,
        usesSelfAccess: Boolean,
        currentNotes: List<org.alkaline.taskbrain.data.Note>,
        currentNote: org.alkaline.taskbrain.data.Note?
    ): CachedDirectiveResult? {
        // If we're the origin of an edit, suppress staleness check
        if (editSessionManager.shouldSuppressInvalidation(noteId)) {
            // Return cached result without staleness check
            return cacheManager.get(directiveKey, noteId, usesSelfAccess)
        }

        // Normal validity check
        return cacheManager.getIfValid(
            directiveKey, noteId, usesSelfAccess,
            currentNotes, currentNote
        )
    }

    /**
     * Get the underlying cache manager.
     */
    fun getCacheManager(): DirectiveCacheManager = cacheManager

    /**
     * Get the edit session manager.
     */
    fun getEditSessionManager(): EditSessionManager = editSessionManager
}
