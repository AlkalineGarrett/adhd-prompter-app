package org.alkaline.taskbrain.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for LruCache implementation.
 */
class LruCacheTest {

    // region Basic Operations

    @Test
    fun `empty cache has size zero`() {
        val cache = LruCache<String, Int>(10)
        assertEquals(0, cache.size)
    }

    @Test
    fun `put and get returns stored value`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 42)

        assertEquals(42, cache.get("key1"))
    }

    @Test
    fun `get returns null for missing key`() {
        val cache = LruCache<String, Int>(10)
        assertNull(cache.get("missing"))
    }

    @Test
    fun `put updates existing key`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 42)
        cache.put("key1", 100)

        assertEquals(100, cache.get("key1"))
        assertEquals(1, cache.size)
    }

    @Test
    fun `containsKey returns true for existing key`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 42)

        assertTrue(cache.containsKey("key1"))
    }

    @Test
    fun `containsKey returns false for missing key`() {
        val cache = LruCache<String, Int>(10)

        assertFalse(cache.containsKey("missing"))
    }

    @Test
    fun `remove deletes key and returns value`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 42)

        val removed = cache.remove("key1")

        assertEquals(42, removed)
        assertNull(cache.get("key1"))
        assertEquals(0, cache.size)
    }

    @Test
    fun `remove returns null for missing key`() {
        val cache = LruCache<String, Int>(10)

        assertNull(cache.remove("missing"))
    }

    @Test
    fun `clear removes all entries`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 1)
        cache.put("key2", 2)
        cache.put("key3", 3)

        cache.clear()

        assertEquals(0, cache.size)
        assertNull(cache.get("key1"))
        assertNull(cache.get("key2"))
        assertNull(cache.get("key3"))
    }

    // endregion

    // region LRU Eviction

    @Test
    fun `evicts eldest entry when exceeding max size`() {
        val cache = LruCache<String, Int>(3)
        cache.put("key1", 1)
        cache.put("key2", 2)
        cache.put("key3", 3)

        // This should evict key1 (eldest)
        cache.put("key4", 4)

        assertEquals(3, cache.size)
        assertNull(cache.get("key1"))
        assertEquals(2, cache.get("key2"))
        assertEquals(3, cache.get("key3"))
        assertEquals(4, cache.get("key4"))
    }

    @Test
    fun `access updates LRU order`() {
        val cache = LruCache<String, Int>(3)
        cache.put("key1", 1)
        cache.put("key2", 2)
        cache.put("key3", 3)

        // Access key1, making it most recently used
        cache.get("key1")

        // This should evict key2 (now eldest)
        cache.put("key4", 4)

        assertEquals(3, cache.size)
        assertEquals(1, cache.get("key1"))  // Still present
        assertNull(cache.get("key2"))        // Evicted
        assertEquals(3, cache.get("key3"))
        assertEquals(4, cache.get("key4"))
    }

    @Test
    fun `put updates LRU order for existing key`() {
        val cache = LruCache<String, Int>(3)
        cache.put("key1", 1)
        cache.put("key2", 2)
        cache.put("key3", 3)

        // Update key1, making it most recently used
        cache.put("key1", 100)

        // This should evict key2 (now eldest)
        cache.put("key4", 4)

        assertEquals(3, cache.size)
        assertEquals(100, cache.get("key1"))  // Updated and present
        assertNull(cache.get("key2"))          // Evicted
    }

    // endregion

    // region getOrPut

    @Test
    fun `getOrPut returns existing value without calling factory`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 42)

        var factoryCalled = false
        val result = cache.getOrPut("key1") {
            factoryCalled = true
            100
        }

        assertEquals(42, result)
        assertFalse(factoryCalled)
    }

    @Test
    fun `getOrPut calls factory for missing key`() {
        val cache = LruCache<String, Int>(10)

        var factoryCalled = false
        val result = cache.getOrPut("key1") {
            factoryCalled = true
            42
        }

        assertEquals(42, result)
        assertTrue(factoryCalled)
        assertEquals(42, cache.get("key1"))
    }

    // endregion

    // region evictTo

    @Test
    fun `evictTo reduces cache to target size`() {
        val cache = LruCache<String, Int>(10)
        for (i in 1..10) {
            cache.put("key$i", i)
        }

        cache.evictTo(5)

        assertEquals(5, cache.size)
    }

    @Test
    fun `evictTo evicts eldest entries first`() {
        val cache = LruCache<String, Int>(10)
        for (i in 1..10) {
            cache.put("key$i", i)
        }

        cache.evictTo(5)

        // Keys 1-5 should be evicted, keys 6-10 should remain
        for (i in 1..5) {
            assertNull(cache.get("key$i"))
        }
        for (i in 6..10) {
            assertEquals(i, cache.get("key$i"))
        }
    }

    @Test
    fun `evictTo with zero clears cache`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 1)
        cache.put("key2", 2)

        cache.evictTo(0)

        assertEquals(0, cache.size)
    }

    @Test
    fun `evictTo with negative does nothing`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 1)
        cache.put("key2", 2)

        cache.evictTo(-1)

        assertEquals(2, cache.size)
    }

    @Test
    fun `evictTo with larger target does nothing`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 1)
        cache.put("key2", 2)

        cache.evictTo(100)

        assertEquals(2, cache.size)
    }

    // endregion

    // region keys and values

    @Test
    fun `keys returns all keys`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 1)
        cache.put("key2", 2)
        cache.put("key3", 3)

        val keys = cache.keys()

        assertEquals(setOf("key1", "key2", "key3"), keys)
    }

    @Test
    fun `values returns all values`() {
        val cache = LruCache<String, Int>(10)
        cache.put("key1", 1)
        cache.put("key2", 2)
        cache.put("key3", 3)

        val values = cache.values()

        assertEquals(listOf(1, 2, 3), values.sorted())
    }

    // endregion

    // region stats

    @Test
    fun `stats returns correct values`() {
        val cache = LruCache<String, Int>(100)
        for (i in 1..25) {
            cache.put("key$i", i)
        }

        val stats = cache.stats()

        assertEquals(25, stats.size)
        assertEquals(100, stats.maxSize)
        assertEquals(25, stats.utilizationPercent)
    }

    @Test
    fun `stats shows 100 percent when full`() {
        val cache = LruCache<String, Int>(10)
        for (i in 1..10) {
            cache.put("key$i", i)
        }

        val stats = cache.stats()

        assertEquals(100, stats.utilizationPercent)
    }

    // endregion

    // region Edge Cases

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects zero max size`() {
        LruCache<String, Int>(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects negative max size`() {
        LruCache<String, Int>(-1)
    }

    @Test
    fun `cache with size 1 works correctly`() {
        val cache = LruCache<String, Int>(1)

        cache.put("key1", 1)
        assertEquals(1, cache.get("key1"))

        cache.put("key2", 2)
        assertNull(cache.get("key1"))
        assertEquals(2, cache.get("key2"))
    }

    // endregion
}
