package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.data.Note

/**
 * Interface for directive result caching.
 *
 * Phase 5: Cache architecture.
 */
interface DirectiveCache {
    /** Get a cached result, or null if not present */
    fun get(key: String): CachedDirectiveResult?

    /** Store a result in the cache */
    fun put(key: String, result: CachedDirectiveResult)

    /** Remove a cached result */
    fun remove(key: String)

    /** Clear all cached results */
    fun clear()

    /** Get cache statistics */
    fun stats(): CacheStats
}

/**
 * Global cache for self-less directives (no `.` self-reference).
 *
 * These directives can be shared across all notes since they don't
 * depend on the current note's context. Keyed by directive hash.
 *
 * Example: `find(path: "inbox")` - same result regardless of which note contains it
 */
class GlobalDirectiveCache(
    maxSize: Int = DEFAULT_GLOBAL_CACHE_SIZE
) : DirectiveCache {

    private val cache = LruCache<String, CachedDirectiveResult>(maxSize)

    override fun get(key: String): CachedDirectiveResult? = cache.get(key)

    override fun put(key: String, result: CachedDirectiveResult) = cache.put(key, result)

    override fun remove(key: String) { cache.remove(key) }

    override fun clear() = cache.clear()

    override fun stats(): CacheStats = cache.stats()

    companion object {
        const val DEFAULT_GLOBAL_CACHE_SIZE = 1000
    }
}

/**
 * Per-note cache for self-referencing directives (uses `.` to access current note).
 *
 * These directives depend on the note containing them, so results must be
 * cached per-note. Keyed by directive hash within each note's cache.
 *
 * Example: `[.name]` - result depends on which note contains the directive
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
 * Central manager for all directive caches.
 *
 * Coordinates between global and per-note caches based on directive analysis.
 * Handles staleness checking and cache invalidation.
 */
class DirectiveCacheManager(
    private val globalCache: GlobalDirectiveCache = GlobalDirectiveCache(),
    private val perNoteCache: PerNoteDirectiveCache = PerNoteDirectiveCache()
) {
    /**
     * Get a cached result for a directive.
     *
     * @param directiveKey The cache key (hash of normalized AST)
     * @param noteId The note containing the directive (for per-note cache lookup)
     * @param usesSelfAccess Whether the directive uses `.` self-reference
     * @return Cached result if present and not requiring refresh, null otherwise
     */
    fun get(
        directiveKey: String,
        noteId: String,
        usesSelfAccess: Boolean
    ): CachedDirectiveResult? {
        return if (usesSelfAccess) {
            perNoteCache.get(noteId, directiveKey)
        } else {
            globalCache.get(directiveKey)
        }
    }

    /**
     * Get a cached result if it's still valid (not stale).
     *
     * @param directiveKey The cache key
     * @param noteId The note containing the directive
     * @param usesSelfAccess Whether the directive uses `.` self-reference
     * @param currentNotes All current notes for staleness check
     * @param currentNote The note containing this directive (for hierarchy checks)
     * @return Cached result if valid, null if stale or not present
     */
    fun getIfValid(
        directiveKey: String,
        noteId: String,
        usesSelfAccess: Boolean,
        currentNotes: List<Note>,
        currentNote: Note?
    ): CachedDirectiveResult? {
        val cached = get(directiveKey, noteId, usesSelfAccess) ?: return null

        // Check if we should re-execute (handles both error retry and staleness)
        if (StalenessChecker.shouldReExecute(cached, currentNotes, currentNote)) {
            return null
        }

        return cached
    }

    /**
     * Store a result in the appropriate cache.
     *
     * @param directiveKey The cache key
     * @param noteId The note containing the directive
     * @param usesSelfAccess Whether the directive uses `.` self-reference
     * @param result The result to cache
     */
    fun put(
        directiveKey: String,
        noteId: String,
        usesSelfAccess: Boolean,
        result: CachedDirectiveResult
    ) {
        if (usesSelfAccess) {
            perNoteCache.put(noteId, directiveKey, result)
        } else {
            globalCache.put(directiveKey, result)
        }
    }

    /**
     * Invalidate cached results that depend on specific notes.
     *
     * Called when notes are modified to trigger re-evaluation of dependent directives.
     *
     * @param changedNoteIds IDs of notes that changed
     */
    fun invalidateForNotes(changedNoteIds: Set<String>) {
        // Clear per-note caches for changed notes
        for (noteId in changedNoteIds) {
            perNoteCache.clearNote(noteId)
        }

        // Note: Global cache invalidation is handled via staleness check
        // since global directives don't depend on specific notes directly
    }

    /**
     * Clear a specific note's cache.
     */
    fun clearNote(noteId: String) {
        perNoteCache.clearNote(noteId)
    }

    /**
     * Clear all caches.
     */
    fun clearAll() {
        globalCache.clear()
        perNoteCache.clear()
    }

    /**
     * Evict entries to reduce memory pressure.
     *
     * @param percentToKeep Percentage of entries to keep (0-100)
     */
    fun evictForMemoryPressure(percentToKeep: Int = 50) {
        val globalStats = globalCache.stats()
        val targetGlobalSize = (globalStats.size * percentToKeep) / 100

        // Re-create global cache with reduced entries
        // Note: LruCache.evictTo handles this
        val lruCacheField = globalCache.javaClass.getDeclaredField("cache")
        lruCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val lruCache = lruCacheField.get(globalCache) as LruCache<String, CachedDirectiveResult>
        lruCache.evictTo(targetGlobalSize)
    }

    /**
     * Get combined statistics for logging.
     */
    fun stats(): CombinedCacheStats {
        return CombinedCacheStats(
            global = globalCache.stats(),
            perNote = perNoteCache.stats()
        )
    }
}

/**
 * Combined statistics from all caches.
 */
data class CombinedCacheStats(
    val global: CacheStats,
    val perNote: PerNoteCacheStats
) {
    fun toLogString(): String {
        return "Global cache: ${global.utilizationPercent}% used (${global.size}/${global.maxSize}), " +
               "Per-note: ${perNote.utilizationPercent}% used (${perNote.totalEntries} entries across ${perNote.noteCount} notes)"
    }
}
