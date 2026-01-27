package org.alkaline.taskbrain.testutil

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.dsl.runtime.NoteOperations
import java.util.Date

/**
 * Mock implementation of NoteOperations for testing.
 *
 * Provides tracking of all operations performed for assertions in tests.
 */
class MockNoteOperations : NoteOperations {
    private val notes = mutableMapOf<String, Note>()
    private var idCounter = 1

    // Tracking for assertions
    val createdNotes = mutableListOf<Note>()
    val updatedPaths = mutableListOf<Pair<String, String>>()  // (noteId, newPath)
    val updatedContents = mutableListOf<Pair<String, String>>()  // (noteId, newContent)
    val appendedTexts = mutableListOf<Pair<String, String>>()  // (noteId, text)

    /**
     * Add a note to the mock store. Useful for setting up test fixtures.
     */
    fun addNote(note: Note) {
        notes[note.id] = note
    }

    /**
     * Clear all tracked operations. Call between tests if reusing the mock.
     */
    fun clearTracking() {
        createdNotes.clear()
        updatedPaths.clear()
        updatedContents.clear()
        appendedTexts.clear()
    }

    /**
     * Clear all notes from the mock store.
     */
    fun clearNotes() {
        notes.clear()
    }

    override suspend fun createNote(path: String, content: String): Note {
        val note = Note(
            id = "new-note-${idCounter++}",
            userId = "test-user",
            path = path,
            content = content,
            createdAt = Timestamp(Date())
        )
        notes[note.id] = note
        createdNotes.add(note)
        return note
    }

    override suspend fun getNoteById(noteId: String): Note? = notes[noteId]

    override suspend fun findByPath(path: String): Note? =
        notes.values.find { it.path == path }

    override suspend fun noteExistsAtPath(path: String): Boolean =
        notes.values.any { it.path == path }

    override suspend fun updatePath(noteId: String, newPath: String): Note {
        val note = notes[noteId] ?: throw RuntimeException("Note not found: $noteId")
        val updated = note.copy(path = newPath)
        notes[noteId] = updated
        updatedPaths.add(noteId to newPath)
        return updated
    }

    override suspend fun updateContent(noteId: String, newContent: String): Note {
        val note = notes[noteId] ?: throw RuntimeException("Note not found: $noteId")
        val updated = note.copy(content = newContent)
        notes[noteId] = updated
        updatedContents.add(noteId to newContent)
        return updated
    }

    override suspend fun appendToNote(noteId: String, text: String): Note {
        val note = notes[noteId] ?: throw RuntimeException("Note not found: $noteId")
        val newContent = if (note.content.isEmpty()) text else "${note.content}\n$text"
        val updated = note.copy(content = newContent)
        notes[noteId] = updated
        appendedTexts.add(noteId to text)
        return updated
    }
}
