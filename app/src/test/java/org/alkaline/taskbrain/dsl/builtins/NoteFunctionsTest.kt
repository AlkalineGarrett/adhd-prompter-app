package org.alkaline.taskbrain.dsl.builtins

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.dsl.language.Lexer
import org.alkaline.taskbrain.dsl.language.Parser
import org.alkaline.taskbrain.dsl.runtime.DslValue
import org.alkaline.taskbrain.dsl.runtime.Environment
import org.alkaline.taskbrain.dsl.runtime.Executor
import org.alkaline.taskbrain.dsl.runtime.ListVal
import org.alkaline.taskbrain.dsl.runtime.NoteVal
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Tests for NoteFunctions (find).
 *
 * Milestone 5.
 */
class NoteFunctionsTest {

    private lateinit var executor: Executor

    @Before
    fun setUp() {
        executor = Executor()
    }

    private fun execute(source: String, notes: List<Note>? = null): DslValue {
        val tokens = Lexer(source).tokenize()
        val directive = Parser(tokens, source).parseDirective()
        val env = if (notes != null) Environment.withNotes(notes) else Environment()
        return executor.execute(directive, env)
    }

    // region Sample notes for testing

    private val testNotes = listOf(
        Note(
            id = "note1",
            userId = "user1",
            path = "2026-01-15",
            content = "Journal entry for Jan 15",
            createdAt = Timestamp(Date())
        ),
        Note(
            id = "note2",
            userId = "user1",
            path = "2026-01-16",
            content = "Journal entry for Jan 16",
            createdAt = Timestamp(Date())
        ),
        Note(
            id = "note3",
            userId = "user1",
            path = "journal/2026-01-17",
            content = "Journal entry for Jan 17",
            createdAt = Timestamp(Date())
        ),
        Note(
            id = "note4",
            userId = "user1",
            path = "tasks/inbox",
            content = "Task inbox",
            createdAt = Timestamp(Date())
        ),
        Note(
            id = "note5",
            userId = "user1",
            path = "",
            content = "Note without path",
            createdAt = Timestamp(Date())
        )
    )

    // endregion

    // region Find with no notes

    @Test
    fun `find with no notes returns empty list`() {
        val result = execute("[find(path: \"anything\")]")

        assertTrue(result is ListVal)
        assertTrue((result as ListVal).isEmpty())
    }

    @Test
    fun `find with null environment notes returns empty list`() {
        val result = execute("[find(path: \"anything\")]", notes = null)

        assertTrue(result is ListVal)
        assertTrue((result as ListVal).isEmpty())
    }

    @Test
    fun `find with empty notes list returns empty list`() {
        val result = execute("[find(path: \"anything\")]", notes = emptyList())

        assertTrue(result is ListVal)
        assertTrue((result as ListVal).isEmpty())
    }

    // endregion

    // region Find with exact path match

    @Test
    fun `find with exact path returns matching note`() {
        val result = execute("[find(path: \"2026-01-15\")]", notes = testNotes)

        assertTrue(result is ListVal)
        val list = result as ListVal
        assertEquals(1, list.size)

        val noteVal = list[0] as NoteVal
        assertEquals("note1", noteVal.note.id)
        assertEquals("2026-01-15", noteVal.note.path)
    }

    @Test
    fun `find with exact path returns empty when no match`() {
        val result = execute("[find(path: \"2099-12-31\")]", notes = testNotes)

        assertTrue(result is ListVal)
        assertTrue((result as ListVal).isEmpty())
    }

    @Test
    fun `find with exact nested path`() {
        val result = execute("[find(path: \"tasks/inbox\")]", notes = testNotes)

        assertTrue(result is ListVal)
        val list = result as ListVal
        assertEquals(1, list.size)

        val noteVal = list[0] as NoteVal
        assertEquals("note4", noteVal.note.id)
    }

    // endregion

    // region Find with pattern match

    @Test
    fun `find with date pattern returns matching notes`() {
        // Pattern: digit*4 "-" digit*2 "-" digit*2 matches "2026-01-15", "2026-01-16"
        val result = execute(
            "[find(path: pattern(digit*4 \"-\" digit*2 \"-\" digit*2))]",
            notes = testNotes
        )

        assertTrue(result is ListVal)
        val list = result as ListVal
        assertEquals(2, list.size)

        val paths = list.items.map { (it as NoteVal).note.path }.toSet()
        assertTrue(paths.contains("2026-01-15"))
        assertTrue(paths.contains("2026-01-16"))
    }

    @Test
    fun `find with prefix pattern`() {
        // Pattern: "journal/" any*(1..) matches "journal/2026-01-17"
        val result = execute(
            "[find(path: pattern(\"journal/\" any*(1..)))]",
            notes = testNotes
        )

        assertTrue(result is ListVal)
        val list = result as ListVal
        assertEquals(1, list.size)

        val noteVal = list[0] as NoteVal
        assertEquals("journal/2026-01-17", noteVal.note.path)
    }

    @Test
    fun `find with pattern returns empty when no match`() {
        val result = execute(
            "[find(path: pattern(\"archive/\" any*(1..)))]",
            notes = testNotes
        )

        assertTrue(result is ListVal)
        assertTrue((result as ListVal).isEmpty())
    }

    // endregion

    // region Find without path filter

    @Test
    fun `find without arguments returns all notes`() {
        val result = execute("[find()]", notes = testNotes)

        assertTrue(result is ListVal)
        val list = result as ListVal
        assertEquals(5, list.size)
    }

    // endregion

    // region Find with name filter

    @Test
    fun `find with exact name returns matching note`() {
        val result = execute("[find(name: \"Task inbox\")]", notes = testNotes)

        assertTrue(result is ListVal)
        val list = result as ListVal
        assertEquals(1, list.size)

        val noteVal = list[0] as NoteVal
        assertEquals("note4", noteVal.note.id)
    }

    @Test
    fun `find with exact name returns empty when no match`() {
        val result = execute("[find(name: \"Nonexistent note\")]", notes = testNotes)

        assertTrue(result is ListVal)
        assertTrue((result as ListVal).isEmpty())
    }

    @Test
    fun `find with name pattern returns matching notes`() {
        // Pattern: "Journal" any*(0..) matches all notes starting with "Journal"
        val result = execute(
            "[find(name: pattern(\"Journal\" any*(0..)))]",
            notes = testNotes
        )

        assertTrue(result is ListVal)
        val list = result as ListVal
        assertEquals(3, list.size)

        val names = list.items.map { (it as NoteVal).note.content.lines().first() }.toSet()
        assertTrue(names.contains("Journal entry for Jan 15"))
        assertTrue(names.contains("Journal entry for Jan 16"))
        assertTrue(names.contains("Journal entry for Jan 17"))
    }

    @Test
    fun `find with name pattern returns empty when no match`() {
        val result = execute(
            "[find(name: pattern(\"Meeting\" any*(0..)))]",
            notes = testNotes
        )

        assertTrue(result is ListVal)
        assertTrue((result as ListVal).isEmpty())
    }

    @Test
    fun `find with simple literal pattern returns empty when no match`() {
        // This is the user's exact case - pattern("second") should match nothing
        val result = execute(
            "[find(name:pattern(\"second\"))]",
            notes = testNotes
        )

        assertTrue(result is ListVal)
        assertTrue("Expected empty list but got ${(result as ListVal).size} items", (result as ListVal).isEmpty())
    }


    // endregion

    // region Find with combined filters

    @Test
    fun `find with both path and name filters`() {
        // Only notes with path matching date pattern AND name starting with "Journal"
        val result = execute(
            "[find(path: pattern(digit*4 \"-\" digit*2 \"-\" digit*2), name: pattern(\"Journal\" any*(0..)))]",
            notes = testNotes
        )

        assertTrue(result is ListVal)
        val list = result as ListVal
        // note1 and note2 have date paths and Journal names
        // note3 has "journal/" prefix path (doesn't match date pattern)
        assertEquals(2, list.size)

        val ids = list.items.map { (it as NoteVal).note.id }.toSet()
        assertTrue(ids.contains("note1"))
        assertTrue(ids.contains("note2"))
    }

    @Test
    fun `find with path and name where only path matches returns empty`() {
        val result = execute(
            "[find(path: \"2026-01-15\", name: \"Task inbox\")]",
            notes = testNotes
        )

        assertTrue(result is ListVal)
        assertTrue((result as ListVal).isEmpty())
    }

    // endregion

    // region NoteVal display string

    @Test
    fun `NoteVal displays path when set`() {
        val note = Note(id = "1", path = "my/path", content = "Content")
        val noteVal = NoteVal(note)

        assertEquals("my/path", noteVal.toDisplayString())
    }

    @Test
    fun `NoteVal displays first line of content when path empty`() {
        val note = Note(id = "1", path = "", content = "First line\nSecond line")
        val noteVal = NoteVal(note)

        assertEquals("First line", noteVal.toDisplayString())
    }

    @Test
    fun `NoteVal displays id when path and content empty`() {
        val note = Note(id = "note-id-123", path = "", content = "")
        val noteVal = NoteVal(note)

        assertEquals("note-id-123", noteVal.toDisplayString())
    }

    // endregion

    // region ListVal display string

    @Test
    fun `ListVal displays empty brackets for empty list`() {
        val list = ListVal(emptyList())

        assertEquals("[]", list.toDisplayString())
    }

    @Test
    fun `ListVal displays items comma-separated`() {
        val note1 = Note(id = "1", path = "path1", content = "")
        val note2 = Note(id = "2", path = "path2", content = "")
        val list = ListVal(listOf(NoteVal(note1), NoteVal(note2)))

        assertEquals("[path1, path2]", list.toDisplayString())
    }

    // endregion

    // region Serialization

    @Test
    fun `NoteVal serializes and deserializes`() {
        val note = Note(
            id = "note-123",
            userId = "user-456",
            path = "journal/2026-01-25",
            content = "Test content"
        )
        val original = NoteVal(note)

        val serialized = original.serialize()
        val deserialized = DslValue.deserialize(serialized)

        assertTrue(deserialized is NoteVal)
        val restored = deserialized as NoteVal
        assertEquals("note-123", restored.note.id)
        assertEquals("user-456", restored.note.userId)
        assertEquals("journal/2026-01-25", restored.note.path)
        assertEquals("Test content", restored.note.content)
    }

    @Test
    fun `ListVal serializes and deserializes`() {
        val note1 = Note(id = "1", path = "path1", content = "Content 1")
        val note2 = Note(id = "2", path = "path2", content = "Content 2")
        val original = ListVal(listOf(NoteVal(note1), NoteVal(note2)))

        val serialized = original.serialize()
        val deserialized = DslValue.deserialize(serialized)

        assertTrue(deserialized is ListVal)
        val restored = deserialized as ListVal
        assertEquals(2, restored.size)

        val restoredNote1 = restored[0] as NoteVal
        val restoredNote2 = restored[1] as NoteVal
        assertEquals("path1", restoredNote1.note.path)
        assertEquals("path2", restoredNote2.note.path)
    }

    @Test
    fun `empty ListVal serializes and deserializes`() {
        val original = ListVal(emptyList())

        val serialized = original.serialize()
        val deserialized = DslValue.deserialize(serialized)

        assertTrue(deserialized is ListVal)
        assertTrue((deserialized as ListVal).isEmpty())
    }

    // endregion

    // region find is static

    @Test
    fun `find is classified as static`() {
        assertFalse(org.alkaline.taskbrain.dsl.runtime.BuiltinRegistry.isDynamic("find"))
    }

    // endregion
}
