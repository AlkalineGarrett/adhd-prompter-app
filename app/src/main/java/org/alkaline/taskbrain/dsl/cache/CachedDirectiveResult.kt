package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.dsl.runtime.DslValue

/**
 * A cached directive execution result with its dependencies and hashes.
 *
 * Phase 1: Data structures and hashing infrastructure.
 *
 * This is stored in cache and used to check if the cached result is still valid.
 */
data class CachedDirectiveResult(
    /** The computed value from directive execution */
    val result: DslValue,

    /** Dependencies detected during execution */
    val dependencies: DirectiveDependencies,

    /** Content hashes for per-note dependencies (noteId -> hashes) */
    val noteContentHashes: Map<String, ContentHashes>,

    /** Collection-level metadata hashes */
    val metadataHashes: MetadataHashes,

    /** Timestamp when this result was cached */
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * Content hashes for a single note.
 * Tracks the hash of first line (name) and remaining content separately.
 */
data class ContentHashes(
    /** Hash of the first line (null if not depended on) */
    val firstLineHash: String? = null,
    /** Hash of content after first line (null if not depended on) */
    val nonFirstLineHash: String? = null
) {
    companion object {
        val EMPTY = ContentHashes()
    }
}

/**
 * Collection-level metadata hashes.
 * These are computed on-demand during staleness check.
 *
 * Only fields that the directive depends on will have non-null values.
 */
data class MetadataHashes(
    /** Hash of all note paths (null if directive doesn't depend on path) */
    val pathHash: String? = null,
    /** Hash of all modified timestamps */
    val modifiedHash: String? = null,
    /** Hash of all created timestamps */
    val createdHash: String? = null,
    /** Hash of all viewed timestamps */
    val viewedHash: String? = null,
    /** Hash of sorted note IDs (for existence check) */
    val existenceHash: String? = null
) {
    companion object {
        val EMPTY = MetadataHashes()
    }
}
