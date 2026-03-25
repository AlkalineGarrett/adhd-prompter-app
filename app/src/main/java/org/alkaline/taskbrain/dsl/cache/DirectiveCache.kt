package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.util.CacheStats
import org.alkaline.taskbrain.util.LruCache

/**
 * Per-note directive cache. All directive results are scoped to the note containing them.
 * Keyed by directive hash within each note's cache.
 *
 * This class manages multiple note caches with a total entry limit.
 */
class PerNoteDirectiveCache(
    private val maxEntriesPerNote: Int = DEFAULT_PER_NOTE_CACHE_SIZE,
    private val maxTotalNotes: Int = DEFAULT_MAX_NOTES
) {
    // Map of noteId -> cache for that note
    private val noteCaches = LruCache<String, LruCache<String, CachedDirectiveResult>>(maxTotalNotes)

    /** Get a cached result for a specific note */
    fun get(noteId: String, directiveKey: String): CachedDirectiveResult? {
        return noteCaches.get(noteId)?.get(directiveKey)
    }

    /** Store a result for a specific note */
    fun put(noteId: String, directiveKey: String, result: CachedDirectiveResult) {
        val noteCache = noteCaches.getOrPut(noteId) {
            LruCache(maxEntriesPerNote)
        }
        noteCache.put(directiveKey, result)
    }

    /** Remove a cached result for a specific note */
    fun remove(noteId: String, directiveKey: String) {
        noteCaches.get(noteId)?.remove(directiveKey)
    }

    /** Clear all cached results for a specific note */
    fun clearNote(noteId: String) {
        noteCaches.remove(noteId)
    }

    /** Clear all cached results for all notes */
    fun clear() {
        noteCaches.clear()
    }

    /** Get statistics for a specific note's cache */
    fun noteStats(noteId: String): CacheStats? {
        return noteCaches.get(noteId)?.stats()
    }

    /** Get aggregate statistics across all note caches */
    fun stats(): PerNoteCacheStats {
        val noteIds = noteCaches.keys()
        var totalEntries = 0
        var totalCapacity = 0

        for (noteId in noteIds) {
            val noteCache = noteCaches.get(noteId)
            if (noteCache != null) {
                totalEntries += noteCache.size
                totalCapacity += maxEntriesPerNote
            }
        }

        return PerNoteCacheStats(
            noteCount = noteIds.size,
            maxNotes = maxTotalNotes,
            totalEntries = totalEntries,
            maxEntriesPerNote = maxEntriesPerNote,
            utilizationPercent = if (totalCapacity > 0) (totalEntries * 100) / totalCapacity else 0
        )
    }

    companion object {
        const val DEFAULT_PER_NOTE_CACHE_SIZE = 100
        const val DEFAULT_MAX_NOTES = 500
    }
}

/**
 * Statistics for per-note cache.
 */
data class PerNoteCacheStats(
    val noteCount: Int,
    val maxNotes: Int,
    val totalEntries: Int,
    val maxEntriesPerNote: Int,
    val utilizationPercent: Int
)

/**
 * Central manager for directive caches.
 *
 * All directive results are cached per-note — even directives that don't reference
 * the current note are scoped to the note containing them.
 * Handles staleness checking and cache invalidation.
 */
class DirectiveCacheManager(
    private val perNoteCache: PerNoteDirectiveCache = PerNoteDirectiveCache()
) {
    fun get(directiveKey: String, noteId: String): CachedDirectiveResult? {
        return perNoteCache.get(noteId, directiveKey)
    }

    fun getIfValid(
        directiveKey: String,
        noteId: String,
        currentNotes: List<Note>,
        currentNote: Note?
    ): CachedDirectiveResult? {
        val cached = get(directiveKey, noteId) ?: return null
        if (StalenessChecker.shouldReExecute(cached, currentNotes, currentNote)) {
            return null
        }
        return cached
    }

    fun put(directiveKey: String, noteId: String, result: CachedDirectiveResult) {
        perNoteCache.put(noteId, directiveKey, result)
    }

    fun invalidateForNotes(changedNoteIds: Set<String>) {
        for (noteId in changedNoteIds) {
            perNoteCache.clearNote(noteId)
        }
    }

    fun remove(directiveKey: String, noteId: String) {
        perNoteCache.remove(noteId, directiveKey)
    }

    fun clearNote(noteId: String) {
        perNoteCache.clearNote(noteId)
    }

    fun clearAll() {
        perNoteCache.clear()
    }

    fun stats(): PerNoteCacheStats {
        return perNoteCache.stats()
    }
}
