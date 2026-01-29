package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.data.Note

/**
 * Computes collection-level metadata hashes for staleness checks.
 *
 * Phase 1: Data structures and hashing infrastructure.
 *
 * Hashes are computed on-demand, not stored. This is efficient because:
 * - Only computed for fields the directive actually depends on
 * - Short-circuit evaluation stops at first stale field
 * - Typical cost: ~1-2ms for 200 notes (imperceptible)
 */
object MetadataHasher {

    /**
     * Compute hash of all note paths.
     * Format: "noteId1:path1\nnoteId2:path2\n..."
     */
    fun computePathHash(notes: List<Note>): String {
        val values = notes.sortedBy { it.id }.map { "${it.id}:${it.path}" }
        return ContentHasher.hash(values.joinToString("\n"))
    }

    /**
     * Compute hash of all modified timestamps.
     * Format: "noteId1:timestamp1\nnoteId2:timestamp2\n..."
     */
    fun computeModifiedHash(notes: List<Note>): String {
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
        val sortedIds = notes.map { it.id }.sorted()
        return ContentHasher.hash(sortedIds.joinToString("\n"))
    }

    /**
     * Compute all metadata hashes based on which fields are depended on.
     * Only computes hashes for fields that are needed.
     */
    fun computeHashes(notes: List<Note>, deps: DirectiveDependencies): MetadataHashes {
        return MetadataHashes(
            pathHash = if (deps.dependsOnPath) computePathHash(notes) else null,
            modifiedHash = if (deps.dependsOnModified) computeModifiedHash(notes) else null,
            createdHash = if (deps.dependsOnCreated) computeCreatedHash(notes) else null,
            viewedHash = if (deps.dependsOnViewed) computeViewedHash(notes) else null,
            existenceHash = if (deps.dependsOnNoteExistence) computeExistenceHash(notes) else null
        )
    }
}
