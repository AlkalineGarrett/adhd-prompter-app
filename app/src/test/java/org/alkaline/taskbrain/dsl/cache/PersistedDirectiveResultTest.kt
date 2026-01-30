package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.dsl.runtime.NumberVal
import org.alkaline.taskbrain.dsl.runtime.StringVal
import org.alkaline.taskbrain.dsl.runtime.BooleanVal
import org.alkaline.taskbrain.dsl.runtime.ListVal
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for PersistedDirectiveResult serialization.
 * Phase 6: Firestore persistence layer.
 */
class PersistedDirectiveResultTest {

    // region Success Result Serialization

    @Test
    fun `serializes and deserializes simple number result`() {
        val original = CachedDirectiveResult.success(
            result = NumberVal(42.0),
            dependencies = DirectiveDependencies.EMPTY
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertTrue(restored.isSuccess)
        assertEquals(42.0, (restored.result as NumberVal).value, 0.001)
    }

    @Test
    fun `serializes and deserializes string result`() {
        val original = CachedDirectiveResult.success(
            result = StringVal("hello world"),
            dependencies = DirectiveDependencies.EMPTY
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertTrue(restored.isSuccess)
        assertEquals("hello world", (restored.result as StringVal).value)
    }

    @Test
    fun `serializes and deserializes boolean result`() {
        val original = CachedDirectiveResult.success(
            result = BooleanVal(true),
            dependencies = DirectiveDependencies.EMPTY
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertTrue(restored.isSuccess)
        assertEquals(true, (restored.result as BooleanVal).value)
    }

    @Test
    fun `serializes and deserializes list result`() {
        val original = CachedDirectiveResult.success(
            result = ListVal(listOf(NumberVal(1.0), NumberVal(2.0), NumberVal(3.0))),
            dependencies = DirectiveDependencies.EMPTY
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertTrue(restored.isSuccess)
        val list = restored.result as ListVal
        assertEquals(3, list.items.size)
        assertEquals(1.0, (list.items[0] as NumberVal).value, 0.001)
        assertEquals(2.0, (list.items[1] as NumberVal).value, 0.001)
        assertEquals(3.0, (list.items[2] as NumberVal).value, 0.001)
    }

    // endregion

    // region Error Result Serialization

    @Test
    fun `serializes and deserializes syntax error`() {
        val original = CachedDirectiveResult.error(
            error = SyntaxError("Unexpected token", position = 42)
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertTrue(restored.isError)
        val error = restored.error as SyntaxError
        assertEquals("Unexpected token", error.message)
        assertEquals(42, error.position)
        assertTrue(error.isDeterministic)
    }

    @Test
    fun `serializes and deserializes type error`() {
        val original = CachedDirectiveResult.error(
            error = TypeError("Expected number")
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertTrue(restored.isError)
        assertTrue(restored.error is TypeError)
        assertTrue(restored.error!!.isDeterministic)
    }

    @Test
    fun `serializes and deserializes network error`() {
        val original = CachedDirectiveResult.error(
            error = NetworkError("Connection refused")
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertTrue(restored.isError)
        val error = restored.error as NetworkError
        assertEquals("Connection refused", error.message)
        assertFalse(error.isDeterministic)
    }

    @Test
    fun `serializes and deserializes all error types`() {
        val errorTypes = listOf(
            SyntaxError("msg"),
            TypeError("msg"),
            ArgumentError("msg"),
            FieldAccessError("msg"),
            ValidationError("msg"),
            UnknownIdentifierError("msg"),
            CircularDependencyError("msg"),
            ArithmeticError("msg"),
            NetworkError("msg"),
            TimeoutError("msg"),
            ResourceUnavailableError("msg"),
            PermissionError("msg"),
            ExternalServiceError("msg")
        )

        for (errorType in errorTypes) {
            val original = CachedDirectiveResult.error(error = errorType)
            val persisted = PersistedDirectiveResult.fromCachedResult(original)
            val restored = PersistedDirectiveResult.toCachedResult(persisted)

            assertEquals(
                "Failed for ${errorType::class.simpleName}",
                errorType::class,
                restored.error!!::class
            )
            assertEquals(errorType.isDeterministic, restored.error!!.isDeterministic)
        }
    }

    // endregion

    // region Dependencies Serialization

    @Test
    fun `serializes and deserializes empty dependencies`() {
        val original = CachedDirectiveResult.success(
            result = NumberVal(1.0),
            dependencies = DirectiveDependencies.EMPTY
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertEquals(DirectiveDependencies.EMPTY, restored.dependencies)
    }

    @Test
    fun `serializes and deserializes metadata dependencies`() {
        val deps = DirectiveDependencies.EMPTY.copy(
            dependsOnPath = true,
            dependsOnModified = true,
            dependsOnNoteExistence = true,
            usesSelfAccess = true
        )
        val original = CachedDirectiveResult.success(
            result = NumberVal(1.0),
            dependencies = deps
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertTrue(restored.dependencies.dependsOnPath)
        assertTrue(restored.dependencies.dependsOnModified)
        assertFalse(restored.dependencies.dependsOnCreated)
        assertFalse(restored.dependencies.dependsOnViewed)
        assertTrue(restored.dependencies.dependsOnNoteExistence)
        assertTrue(restored.dependencies.usesSelfAccess)
    }

    @Test
    fun `serializes and deserializes content dependencies`() {
        val deps = DirectiveDependencies.EMPTY.copy(
            firstLineNotes = setOf("note1", "note2"),
            nonFirstLineNotes = setOf("note3")
        )
        val original = CachedDirectiveResult.success(
            result = NumberVal(1.0),
            dependencies = deps
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertEquals(setOf("note1", "note2"), restored.dependencies.firstLineNotes)
        assertEquals(setOf("note3"), restored.dependencies.nonFirstLineNotes)
    }

    @Test
    fun `serializes and deserializes hierarchy dependencies`() {
        val deps = DirectiveDependencies.EMPTY.copy(
            hierarchyDeps = listOf(
                HierarchyDependency(
                    path = HierarchyPath.Up,
                    resolvedNoteId = "parent123",
                    field = NoteField.NAME,
                    fieldHash = "abc123"
                ),
                HierarchyDependency(
                    path = HierarchyPath.UpN(2),
                    resolvedNoteId = "grandparent",
                    field = NoteField.PATH,
                    fieldHash = "def456"
                ),
                HierarchyDependency(
                    path = HierarchyPath.Root,
                    resolvedNoteId = "root",
                    field = null,
                    fieldHash = null
                )
            )
        )
        val original = CachedDirectiveResult.success(
            result = NumberVal(1.0),
            dependencies = deps
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertEquals(3, restored.dependencies.hierarchyDeps.size)

        val dep0 = restored.dependencies.hierarchyDeps[0]
        assertEquals(HierarchyPath.Up, dep0.path)
        assertEquals("parent123", dep0.resolvedNoteId)
        assertEquals(NoteField.NAME, dep0.field)
        assertEquals("abc123", dep0.fieldHash)

        val dep1 = restored.dependencies.hierarchyDeps[1]
        assertTrue(dep1.path is HierarchyPath.UpN)
        assertEquals(2, (dep1.path as HierarchyPath.UpN).levels)
        assertEquals("grandparent", dep1.resolvedNoteId)
        assertEquals(NoteField.PATH, dep1.field)

        val dep2 = restored.dependencies.hierarchyDeps[2]
        assertEquals(HierarchyPath.Root, dep2.path)
        assertEquals("root", dep2.resolvedNoteId)
        assertNull(dep2.field)
        assertNull(dep2.fieldHash)
    }

    // endregion

    // region Hashes Serialization

    @Test
    fun `serializes and deserializes metadata hashes`() {
        val hashes = MetadataHashes(
            pathHash = "path-hash",
            modifiedHash = "modified-hash",
            createdHash = null,
            viewedHash = "viewed-hash",
            existenceHash = "existence-hash"
        )
        val original = CachedDirectiveResult.success(
            result = NumberVal(1.0),
            dependencies = DirectiveDependencies.EMPTY,
            metadataHashes = hashes
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertEquals("path-hash", restored.metadataHashes.pathHash)
        assertEquals("modified-hash", restored.metadataHashes.modifiedHash)
        assertNull(restored.metadataHashes.createdHash)
        assertEquals("viewed-hash", restored.metadataHashes.viewedHash)
        assertEquals("existence-hash", restored.metadataHashes.existenceHash)
    }

    @Test
    fun `serializes and deserializes content hashes`() {
        val contentHashes = mapOf(
            "note1" to ContentHashes(firstLineHash = "fl1", nonFirstLineHash = "nfl1"),
            "note2" to ContentHashes(firstLineHash = "fl2", nonFirstLineHash = null)
        )
        val original = CachedDirectiveResult.success(
            result = NumberVal(1.0),
            dependencies = DirectiveDependencies.EMPTY,
            noteContentHashes = contentHashes
        )

        val persisted = PersistedDirectiveResult.fromCachedResult(original)
        val restored = PersistedDirectiveResult.toCachedResult(persisted)

        assertEquals(2, restored.noteContentHashes.size)
        assertEquals("fl1", restored.noteContentHashes["note1"]?.firstLineHash)
        assertEquals("nfl1", restored.noteContentHashes["note1"]?.nonFirstLineHash)
        assertEquals("fl2", restored.noteContentHashes["note2"]?.firstLineHash)
        assertNull(restored.noteContentHashes["note2"]?.nonFirstLineHash)
    }

    // endregion

    // region DirectiveErrorSerializer

    @Test
    fun `DirectiveErrorSerializer preserves error position`() {
        val error = SyntaxError("Parse error", position = 100)
        val serialized = DirectiveErrorSerializer.serialize(error)
        val deserialized = DirectiveErrorSerializer.deserialize(serialized)

        assertEquals(100, deserialized.position)
    }

    @Test
    fun `DirectiveErrorSerializer handles null position`() {
        val error = TypeError("Type mismatch")
        val serialized = DirectiveErrorSerializer.serialize(error)
        val deserialized = DirectiveErrorSerializer.deserialize(serialized)

        assertNull(deserialized.position)
    }

    // endregion
}
