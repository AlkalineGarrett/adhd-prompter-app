package org.alkaline.taskbrain.dsl.cache

/**
 * A simple LRU (Least Recently Used) cache implementation.
 *
 * Phase 5: Cache architecture.
 *
 * Uses a LinkedHashMap with access-order iteration to automatically
 * maintain LRU ordering. When the cache exceeds maxSize, the eldest
 * (least recently used) entry is removed.
 *
 * @param maxSize Maximum number of entries in the cache
 */
class LruCache<K, V>(private val maxSize: Int) {

    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }

    // LinkedHashMap with accessOrder=true maintains LRU order
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    /** Number of entries currently in the cache */
    val size: Int get() = synchronized(map) { map.size }

    /** Get a value from the cache, or null if not present */
    fun get(key: K): V? = synchronized(map) {
        map[key]
    }

    /** Put a value in the cache */
    fun put(key: K, value: V) = synchronized(map) {
        map[key] = value
    }

    /** Get a value or compute and store it if absent */
    fun getOrPut(key: K, defaultValue: () -> V): V = synchronized(map) {
        map[key] ?: defaultValue().also { map[key] = it }
    }

    /** Remove a specific key from the cache */
    fun remove(key: K): V? = synchronized(map) {
        map.remove(key)
    }

    /** Check if a key exists in the cache */
    fun containsKey(key: K): Boolean = synchronized(map) {
        map.containsKey(key)
    }

    /** Clear all entries from the cache */
    fun clear() = synchronized(map) {
        map.clear()
    }

    /** Get all keys currently in the cache (snapshot) */
    fun keys(): Set<K> = synchronized(map) {
        map.keys.toSet()
    }

    /** Get all values currently in the cache (snapshot) */
    fun values(): List<V> = synchronized(map) {
        map.values.toList()
    }

    /** Evict entries until size is at or below the target */
    fun evictTo(targetSize: Int) = synchronized(map) {
        if (targetSize < 0) return@synchronized
        val iterator = map.entries.iterator()
        while (map.size > targetSize && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    /** Get cache statistics */
    fun stats(): CacheStats = synchronized(map) {
        CacheStats(
            size = map.size,
            maxSize = maxSize,
            utilizationPercent = if (maxSize > 0) (map.size * 100) / maxSize else 0
        )
    }
}

/**
 * Statistics about cache usage.
 */
data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val utilizationPercent: Int
)
