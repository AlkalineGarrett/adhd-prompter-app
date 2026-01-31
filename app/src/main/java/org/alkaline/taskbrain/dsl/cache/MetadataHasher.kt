package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.data.Note

/**
 * Computes collection-level metadata hashes for staleness checks.
 *
 * Phase 1: Data structures and hashing infrastructure.
 * Phase 3 (Caching Audit): Added caching for expensive hash computations.
 *
 * Hashes are computed on-demand and cached until invalidated. This is efficient because:
 * - Only computed for fields the directive actually depends on
 * - Short-circuit evaluation stops at first stale field
 * - Cached results avoid recomputing for multiple directives in the same execution cycle
 * - Cache is invalidated when notes change (via invalidateCache() or when a different notes list is passed)
 */
object MetadataHasher {

    /**
     * Cached metadata hashes along with a snapshot of the notes identity.
     * The cache is only valid if the same notes list (by content) is being used.
     *
     * Phase 3 (Caching Audit): Added to avoid recomputing expensive hashes.
     */
    private data class CachedHashes(
        val notesIdentity: Int,  // Hash of notes list for identity check
        val pathHash: String? = null,
        val modifiedHash: String? = null,
        val createdHash: String? = null,
        val viewedHash: String? = null,
        val existenceHash: String? = null,
        val allNamesHash: String? = null
    )

    @Volatile
    private var cachedHashes: CachedHashes? = null

    /**
     * Compute an identity hash for a notes list.
     * This is used to detect when the notes list has changed.
     *
     * We use the list's identity hash code (reference equality) to ensure
     * cached values are only returned when the EXACT same list object is passed.
     * This is safe because:
     * - In production, the ViewModel reuses the same cachedNotes list for all
     *   directive executions in a cycle, so caching works
     * - When notes change, invalidateCache() is called, clearing the cache
     * - In tests, different list objects trigger recomputation (safe)
     */
    private fun computeNotesIdentity(notes: List<Note>): Int {
        return System.identityHashCode(notes)
    }

    /**
     * Get or create cached hashes for the given notes list.
     * Returns null if cache is invalid (different notes list).
     */
    private fun getCacheIfValid(notes: List<Note>): CachedHashes? {
        val cached = cachedHashes ?: return null
        val currentIdentity = computeNotesIdentity(notes)
        return if (cached.notesIdentity == currentIdentity) cached else null
    }

    /**
     * Update the cache with a new hash value.
     */
    private fun updateCache(notes: List<Note>, updater: (CachedHashes) -> CachedHashes) {
        val identity = computeNotesIdentity(notes)
        val current = cachedHashes?.takeIf { it.notesIdentity == identity }
            ?: CachedHashes(notesIdentity = identity)
        cachedHashes = updater(current)
    }

    /**
     * Invalidate all cached hashes.
     * Call this when the notes collection changes.
     *
     * Phase 3 (Caching Audit).
     */
    fun invalidateCache() {
        cachedHashes = null
    }

    /**
     * Compute hash of all note paths.
     * Format: "noteId1:path1\nnoteId2:path2\n..."
     */
    fun computePathHash(notes: List<Note>): String {
        getCacheIfValid(notes)?.pathHash?.let { return it }

        val hash = computePathHashInternal(notes)
        updateCache(notes) { it.copy(pathHash = hash) }
        return hash
    }

    private fun computePathHashInternal(notes: List<Note>): String {
        val values = notes.sortedBy { it.id }.map { "${it.id}:${it.path}" }
        return ContentHasher.hash(values.joinToString("\n"))
    }

    /**
     * Compute hash of all modified timestamps.
     * Format: "noteId1:timestamp1\nnoteId2:timestamp2\n..."
     */
    fun computeModifiedHash(notes: List<Note>): String {
        getCacheIfValid(notes)?.modifiedHash?.let { return it }

        val hash = computeModifiedHashInternal(notes)
        updateCache(notes) { it.copy(modifiedHash = hash) }
        return hash
    }

    private fun computeModifiedHashInternal(notes: List<Note>): String {
        val values = notes.sortedBy { it.id }.map { note ->
            val timestamp = note.updatedAt?.toDate()?.time ?: 0L
            "${note.id}:$timestamp"
        }
        return ContentHasher.hash(values.joinToString("\n"))
    }

    /**
     * Compute hash of all created timestamps.
     */
    fun computeCreatedHash(notes: List<Note>): String {
        getCacheIfValid(notes)?.createdHash?.let { return it }

        val hash = computeCreatedHashInternal(notes)
        updateCache(notes) { it.copy(createdHash = hash) }
        return hash
    }

    private fun computeCreatedHashInternal(notes: List<Note>): String {
        val values = notes.sortedBy { it.id }.map { note ->
            val timestamp = note.createdAt?.toDate()?.time ?: 0L
            "${note.id}:$timestamp"
        }
        return ContentHasher.hash(values.joinToString("\n"))
    }

    /**
     * Compute hash of all viewed/accessed timestamps.
     */
    fun computeViewedHash(notes: List<Note>): String {
        getCacheIfValid(notes)?.viewedHash?.let { return it }

        val hash = computeViewedHashInternal(notes)
        updateCache(notes) { it.copy(viewedHash = hash) }
        return hash
    }

    private fun computeViewedHashInternal(notes: List<Note>): String {
        val values = notes.sortedBy { it.id }.map { note ->
            val timestamp = note.lastAccessedAt?.toDate()?.time ?: 0L
            "${note.id}:$timestamp"
        }
        return ContentHasher.hash(values.joinToString("\n"))
    }

    /**
     * Compute hash of note IDs (for existence check).
     * Used to detect note creation/deletion.
     */
    fun computeExistenceHash(notes: List<Note>): String {
        getCacheIfValid(notes)?.existenceHash?.let { return it }

        val hash = computeExistenceHashInternal(notes)
        updateCache(notes) { it.copy(existenceHash = hash) }
        return hash
    }

    private fun computeExistenceHashInternal(notes: List<Note>): String {
        val sortedIds = notes.map { it.id }.sorted()
        return ContentHasher.hash(sortedIds.joinToString("\n"))
    }

    /**
     * Compute hash of all note names (first lines).
     * Used for find(name: ...) which checks all note names.
     */
    fun computeAllNamesHash(notes: List<Note>): String {
        getCacheIfValid(notes)?.allNamesHash?.let { return it }

        val hash = computeAllNamesHashInternal(notes)
        updateCache(notes) { it.copy(allNamesHash = hash) }
        return hash
    }

    private fun computeAllNamesHashInternal(notes: List<Note>): String {
        val values = notes.sortedBy { it.id }.map { note ->
            val firstLine = note.content.lines().firstOrNull() ?: ""
            "${note.id}:$firstLine"
        }
        return ContentHasher.hash(values.joinToString("\n"))
    }

    /**
     * Compute all metadata hashes based on which fields are depended on.
     * Only computes hashes for fields that are needed.
     * Uses cached values when available.
     */
    fun computeHashes(notes: List<Note>, deps: DirectiveDependencies): MetadataHashes {
        return MetadataHashes(
            pathHash = if (deps.dependsOnPath) computePathHash(notes) else null,
            modifiedHash = if (deps.dependsOnModified) computeModifiedHash(notes) else null,
            createdHash = if (deps.dependsOnCreated) computeCreatedHash(notes) else null,
            viewedHash = if (deps.dependsOnViewed) computeViewedHash(notes) else null,
            existenceHash = if (deps.dependsOnNoteExistence) computeExistenceHash(notes) else null,
            allNamesHash = if (deps.dependsOnAllNames) computeAllNamesHash(notes) else null
        )
    }
}
