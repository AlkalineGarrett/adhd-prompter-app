package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.dsl.runtime.values.DateVal
import org.alkaline.taskbrain.dsl.runtime.values.NumberVal
import org.alkaline.taskbrain.dsl.runtime.values.StringVal
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PersistableOnceCacheTest {

    @Test
    fun `loads persisted entries on construction`() {
        val persisted = mapOf(
            "key1" to mapOf("type" to "number", "value" to 42.0),
            "key2" to mapOf("type" to "string", "value" to "hello")
        )
        val cache = PersistableOnceCache(persisted)

        assertEquals(NumberVal(42.0), cache.get("key1"))
        assertEquals(StringVal("hello"), cache.get("key2"))
        assertTrue(cache.contains("key1"))
        assertTrue(cache.contains("key2"))
    }

    @Test
    fun `returns null for missing keys`() {
        val cache = PersistableOnceCache(emptyMap())
        assertNull(cache.get("nonexistent"))
        assertFalse(cache.contains("nonexistent"))
    }

    @Test
    fun `tracks new entries separately from persisted`() {
        val persisted = mapOf(
            "existing" to mapOf("type" to "number", "value" to 1.0)
        )
        val cache = PersistableOnceCache(persisted)

        // No new entries initially
        assertFalse(cache.hasNewEntries())
        assertTrue(cache.newEntries().isEmpty())

        // Add a new entry
        val dateVal = DateVal(LocalDate.of(2026, 4, 9))
        cache.put("new-key", dateVal)

        assertTrue(cache.hasNewEntries())
        val entries = cache.newEntries()
        assertEquals(1, entries.size)
        assertTrue(entries.containsKey("new-key"))
        assertEquals("date", entries["new-key"]!!["type"])

        // Persisted entry is NOT in newEntries
        assertFalse(entries.containsKey("existing"))
    }

    @Test
    fun `overwriting persisted entry does not create new entry`() {
        val persisted = mapOf(
            "key" to mapOf("type" to "number", "value" to 1.0)
        )
        val cache = PersistableOnceCache(persisted)

        // Overwrite with new value — still considered persisted
        cache.put("key", NumberVal(2.0))

        // Even though value changed, key was in persisted set
        assertFalse(cache.hasNewEntries())
    }

    @Test
    fun `skips entries that fail deserialization`() {
        val persisted = mapOf(
            "good" to mapOf("type" to "number", "value" to 42.0),
            "bad" to mapOf("type" to "unknown_type_xyz", "value" to "???")
        )
        val cache = PersistableOnceCache(persisted)

        assertEquals(NumberVal(42.0), cache.get("good"))
        assertNull(cache.get("bad"))
    }

    @Test
    fun `per-line scoped keys work correctly`() {
        // Simulates two lines with the same once[date] expression but different noteIds
        val persisted = mapOf(
            "noteA:CALL(date,)" to mapOf("type" to "date", "value" to "2026-04-08"),
            "noteB:CALL(date,)" to mapOf("type" to "date", "value" to "2026-04-09")
        )
        val cache = PersistableOnceCache(persisted)

        val valA = cache.get("noteA:CALL(date,)") as DateVal
        val valB = cache.get("noteB:CALL(date,)") as DateVal
        assertEquals(LocalDate.of(2026, 4, 8), valA.value)
        assertEquals(LocalDate.of(2026, 4, 9), valB.value)
    }
}
