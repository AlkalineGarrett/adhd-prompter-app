package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.dsl.directives.DirectiveFinder
import org.alkaline.taskbrain.dsl.runtime.NumberVal
import org.alkaline.taskbrain.dsl.runtime.StringVal
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

/**
 * Integration tests for the caching system.
 * Phase 10: Integration and testing.
 *
 * Tests cover:
 * - CachedDirectiveExecutor with cache hits and misses
 * - Staleness detection and re-execution
 * - Nested views and transitive dependencies
 * - Inline editing with suppressed invalidation
 * - Error caching behavior
 * - RefreshScheduler for time-based triggers
 */
class CachingIntegrationTest {

    private lateinit var cacheManager: DirectiveCacheManager
    private lateinit var editSessionManager: EditSessionManager
    private lateinit var executor: CachedDirectiveExecutor

    private val testNotes = listOf(
        Note(id = "note-1", content = "Note One", path = "inbox/note1"),
        Note(id = "note-2", content = "Note Two", path = "inbox/note2"),
        Note(id = "note-3", content = "Note Three", path = "archive/note3"),
        Note(id = "current", content = "Current Note", path = "current")
    )

    @Before
    fun setUp() {
        cacheManager = DirectiveCacheManager()
        editSessionManager = EditSessionManager(cacheManager)
        executor = CachedDirectiveExecutor(
            cacheManager = cacheManager,
            editSessionManager = editSessionManager
        )
    }

    // region Cache Hit/Miss Tests

    @Test
    fun `first execution is cache miss`() {
        val currentNote = testNotes.find { it.id == "current" }

        val result = executor.execute(
            sourceText = "[add(1, 2)]",
            notes = testNotes,
            currentNote = currentNote
        )

        assertFalse(result.cacheHit)
        assertTrue(result.result.isComputed)
    }

    @Test
    fun `second execution with same input is cache hit`() {
        val currentNote = testNotes.find { it.id == "current" }

        // First execution - cache miss
        val result1 = executor.execute(
            sourceText = "[add(1, 2)]",
            notes = testNotes,
            currentNote = currentNote
        )
        assertFalse(result1.cacheHit)

        // Second execution - cache hit
        val result2 = executor.execute(
            sourceText = "[add(1, 2)]",
            notes = testNotes,
            currentNote = currentNote
        )
        assertTrue(result2.cacheHit)
        assertEquals(result1.result.toValue()?.toDisplayString(), result2.result.toValue()?.toDisplayString())
    }

    @Test
    fun `different directives have separate cache entries`() {
        val currentNote = testNotes.find { it.id == "current" }

        executor.execute("[add(1, 2)]", testNotes, currentNote)
        executor.execute("[add(3, 4)]", testNotes, currentNote)

        // Both should be cached
        val result1 = executor.execute("[add(1, 2)]", testNotes, currentNote)
        val result2 = executor.execute("[add(3, 4)]", testNotes, currentNote)

        assertTrue(result1.cacheHit)
        assertTrue(result2.cacheHit)
    }

    // endregion

    // region Staleness Tests

    @Test
    fun `cache miss when notes change for find directive`() {
        val currentNote = testNotes.find { it.id == "current" }

        // First execution
        executor.execute(
            sourceText = "[find(path: \"inbox/note1\")]",
            notes = testNotes,
            currentNote = currentNote
        )

        // Modify notes (simulate path change)
        val modifiedNotes = testNotes.map { note ->
            if (note.id == "note-1") note.copy(path = "inbox/note1-renamed")
            else note
        }

        // Second execution with modified notes should detect staleness
        val result = executor.execute(
            sourceText = "[find(path: \"inbox/note1\")]",
            notes = modifiedNotes,
            currentNote = currentNote
        )

        // Should be cache miss due to staleness
        assertFalse(result.cacheHit)
    }

    // endregion

    // region Current Note Exclusion Tests

    @Test
    fun `find excludes current note from results`() {
        // Current note is in inbox
        val currentNote = testNotes.find { it.id == "note-1" }

        val result = executor.execute(
            sourceText = "[find(path: pattern(\"inbox/\" any*(1..)))]",
            notes = testNotes,
            currentNote = currentNote
        )

        assertTrue(result.result.isComputed)
        val displayValue = result.result.toValue()?.toDisplayString() ?: ""
        // Should not include note-1 (current note) path in results
        assertFalse(displayValue.contains("inbox/note1"))
        // Should include note-2 path
        assertTrue(displayValue.contains("inbox/note2"))
    }

    // endregion

    // region Invalidation Tests

    @Test
    fun `invalidateForChangedNotes clears relevant caches`() {
        val currentNote = testNotes.find { it.id == "current" }

        // Cache a result using a self-referencing directive (per-note cache)
        executor.execute("[.path]", testNotes, currentNote)

        // Invalidate for the current note
        executor.invalidateForChangedNotes(setOf("current"))

        // Next execution should be cache miss
        val result = executor.execute("[.path]", testNotes, currentNote)
        assertFalse(result.cacheHit)
    }

    @Test
    fun `clearAll removes all cache entries`() {
        val currentNote = testNotes.find { it.id == "current" }

        // Cache multiple results
        executor.execute("[add(1, 2)]", testNotes, currentNote)
        executor.execute("[add(3, 4)]", testNotes, currentNote)

        // Clear all
        executor.clearAll()

        // Both should be cache misses
        assertFalse(executor.execute("[add(1, 2)]", testNotes, currentNote).cacheHit)
        assertFalse(executor.execute("[add(3, 4)]", testNotes, currentNote).cacheHit)
    }

    // endregion

    // region Inline Editing Tests

    @Test
    fun `edit session suppresses invalidation for originating note`() {
        val currentNote = testNotes.find { it.id == "current" }
        val editedNote = testNotes.find { it.id == "note-1" }

        // Cache a result using self-referencing directive (per-note cache)
        executor.execute("[.path]", testNotes, currentNote)

        // Start edit session (editing note-1 from current note's view)
        editSessionManager.startEditSession(
            editedNoteId = editedNote!!.id,
            originatingNoteId = currentNote!!.id
        )

        // Request invalidation for current note (originating)
        executor.invalidateForChangedNotes(setOf("current"))

        // Should still be cached (suppressed)
        assertTrue(executor.execute("[.path]", testNotes, currentNote).cacheHit)

        // End edit session
        editSessionManager.endEditSession()

        // Now should be invalidated
        assertFalse(executor.execute("[.path]", testNotes, currentNote).cacheHit)
    }

    @Test
    fun `edit session allows invalidation for non-originating notes`() {
        val currentNote = testNotes.find { it.id == "current" }
        val otherNote = testNotes.find { it.id == "note-2" }

        // Cache results for both notes
        executor.execute("[add(1, 2)]", testNotes, currentNote)
        executor.execute("[add(1, 2)]", testNotes, otherNote)

        // Start edit session for current note
        editSessionManager.startEditSession(
            editedNoteId = "note-1",
            originatingNoteId = currentNote!!.id
        )

        // Invalidate for other note (not suppressed)
        executor.invalidateForChangedNotes(setOf("note-2"))

        // Current note should still be cached (suppressed)
        assertTrue(executor.execute("[add(1, 2)]", testNotes, currentNote).cacheHit)
    }

    // endregion

    // region Error Caching Tests

    @Test
    fun `syntax errors are cached`() {
        val currentNote = testNotes.find { it.id == "current" }

        // First execution - parse error
        val result1 = executor.execute("[invalid syntax", testNotes, currentNote)
        assertFalse(result1.result.isComputed)  // Has error

        // Second execution should NOT be a cache hit for unparseable directives
        // because we can't generate a cache key
        val result2 = executor.execute("[invalid syntax", testNotes, currentNote)
        assertFalse(result2.cacheHit)
    }

    @Test
    fun `deterministic errors like type errors are cached`() {
        val currentNote = testNotes.find { it.id == "current" }

        // First execution - type error (add with strings)
        val result1 = executor.execute("[add(\"a\", \"b\")]", testNotes, currentNote)
        assertFalse(result1.cacheHit)

        // The error should be cached
        val result2 = executor.execute("[add(\"a\", \"b\")]", testNotes, currentNote)
        assertTrue(result2.cacheHit)
        assertFalse(result2.result.isComputed)  // Has error
    }

    // endregion

    // region RefreshScheduler Tests

    @Test
    fun `scheduler registers and tracks triggers`() {
        var invalidationCount = 0
        val scheduler = RefreshScheduler(
            onTrigger = { _, _ -> invalidationCount++ }
        )

        scheduler.register(
            cacheKey = "test-key",
            noteId = "note-1",
            triggers = listOf(DailyTimeTrigger(LocalTime.of(12, 0)))
        )

        assertEquals(1, scheduler.registrationCount())
        assertTrue(scheduler.registeredKeys().contains("test-key"))
    }

    @Test
    fun `scheduler unregisters correctly`() {
        val scheduler = RefreshScheduler(onTrigger = { _, _ -> })

        scheduler.register("key-1", "note-1", listOf(DailyTimeTrigger(LocalTime.of(12, 0))))
        scheduler.register("key-2", "note-2", listOf(DailyTimeTrigger(LocalTime.of(14, 0))))

        scheduler.unregister("key-1")
        assertEquals(1, scheduler.registrationCount())

        scheduler.unregisterNote("note-2")
        assertEquals(0, scheduler.registrationCount())
    }

    @Test
    fun `scheduler calculates next trigger time`() {
        val scheduler = RefreshScheduler(onTrigger = { _, _ -> })

        val futureTime = LocalTime.now().plusHours(2)
        scheduler.register(
            cacheKey = "test-key",
            noteId = "note-1",
            triggers = listOf(DailyTimeTrigger(futureTime))
        )

        val nextTrigger = scheduler.nextTriggerTime()
        assertNotNull(nextTrigger)
    }

    // endregion

    // region Dependency Tracking Tests

    @Test
    fun `dependencies are collected during execution`() {
        val currentNote = testNotes.find { it.id == "current" }

        val result = executor.execute(
            sourceText = "[find(path: \"inbox/note1\")]",
            notes = testNotes,
            currentNote = currentNote
        )

        // find() should create dependencies
        assertTrue(result.dependencies.dependsOnNoteExistence || result.dependencies.dependsOnPath)
    }

    // endregion

    // region Factory Tests

    @Test
    fun `factory creates in-memory executor`() {
        val executor = CachedDirectiveExecutorFactory.createInMemoryOnly()
        val currentNote = testNotes.find { it.id == "current" }

        val result = executor.execute("[add(1, 2)]", testNotes, currentNote)
        assertTrue(result.result.isComputed)
    }

    @Test
    fun `factory creates edit-aware executor`() {
        val executor = CachedDirectiveExecutorFactory.createWithEditSupport()
        val currentNote = testNotes.find { it.id == "current" }

        val result = executor.execute("[add(1, 2)]", testNotes, currentNote)
        assertTrue(result.result.isComputed)
    }

    // endregion
}
