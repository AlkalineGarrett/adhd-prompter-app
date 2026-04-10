package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.dsl.runtime.values.DslValue

/**
 * OnceCache backed by Firestore-persisted data.
 *
 * Loads initial entries from a note's onceCache field, then tracks
 * any new entries added during execution. After execution, [newEntries]
 * returns only the entries that need to be written back to Firestore.
 */
class PersistableOnceCache(
    persisted: Map<String, Map<String, Any>>
) : OnceCache {

    private val cache = mutableMapOf<String, DslValue>()
    private val persistedKeys = mutableSetOf<String>()

    init {
        for ((key, serialized) in persisted) {
            try {
                @Suppress("UNCHECKED_CAST")
                val value = DslValue.deserialize(serialized as Map<String, Any?>)
                cache[key] = value
                persistedKeys.add(key)
            } catch (_: Exception) {
                // Skip entries that can't be deserialized (e.g., schema change)
            }
        }
    }

    override fun get(key: String): DslValue? = cache[key]

    override fun put(key: String, value: DslValue) {
        cache[key] = value
    }

    override fun contains(key: String): Boolean = cache.containsKey(key)

    /**
     * Returns entries that were added during execution (not already persisted).
     * Each entry is a serialized DslValue map ready for Firestore storage.
     */
    fun newEntries(): Map<String, Map<String, Any?>> {
        val result = mutableMapOf<String, Map<String, Any?>>()
        for ((key, value) in cache) {
            if (key !in persistedKeys) {
                result[key] = value.serialize()
            }
        }
        return result
    }

    /**
     * Whether there are new entries that need to be persisted.
     */
    fun hasNewEntries(): Boolean = cache.keys.any { it !in persistedKeys }
}
