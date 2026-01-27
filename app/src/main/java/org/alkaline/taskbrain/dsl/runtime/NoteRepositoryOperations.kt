package org.alkaline.taskbrain.dsl.runtime

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.alkaline.taskbrain.data.Note

/**
 * Implementation of NoteOperations that uses Firebase Firestore directly.
 *
 * This class provides the note mutation capabilities needed by the DSL
 * (new, maybe_new, append, property setting) by directly interacting
 * with Firestore.
 *
 * Milestone 7.
 */
class NoteRepositoryOperations(
    private val db: FirebaseFirestore,
    private val userId: String
) : NoteOperations {

    private val notesCollection get() = db.collection("notes")

    override suspend fun createNote(path: String, content: String): Note {
        // Check if path already exists
        if (noteExistsAtPath(path)) {
            throw NoteOperationException("Note already exists at path: $path")
        }

        val noteRef = notesCollection.document()
        val noteData = hashMapOf(
            "userId" to userId,
            "content" to content,
            "path" to path,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        noteRef.set(noteData).await()

        // Return the created note
        return Note(
            id = noteRef.id,
            userId = userId,
            path = path,
            content = content,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    }

    override suspend fun getNoteById(noteId: String): Note? {
        val doc = notesCollection.document(noteId).get().await()
        if (!doc.exists()) return null
        return doc.toObject(Note::class.java)?.copy(id = doc.id)
    }

    override suspend fun findByPath(path: String): Note? {
        val query = notesCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("path", path)
            .limit(1)
            .get()
            .await()

        return query.documents.firstOrNull()?.let { doc ->
            doc.toObject(Note::class.java)?.copy(id = doc.id)
        }
    }

    override suspend fun noteExistsAtPath(path: String): Boolean {
        val query = notesCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("path", path)
            .limit(1)
            .get()
            .await()

        return query.documents.isNotEmpty()
    }

    override suspend fun updatePath(noteId: String, newPath: String): Note {
        // Check if new path is already taken by another note
        val existingAtPath = findByPath(newPath)
        if (existingAtPath != null && existingAtPath.id != noteId) {
            throw NoteOperationException("Path already in use: $newPath")
        }

        val noteRef = notesCollection.document(noteId)
        noteRef.update(
            mapOf(
                "path" to newPath,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Fetch and return updated note
        val doc = noteRef.get().await()
        return doc.toObject(Note::class.java)?.copy(id = doc.id)
            ?: throw NoteOperationException("Note not found after update: $noteId")
    }

    override suspend fun updateContent(noteId: String, newContent: String): Note {
        val noteRef = notesCollection.document(noteId)
        noteRef.update(
            mapOf(
                "content" to newContent,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Fetch and return updated note
        val doc = noteRef.get().await()
        return doc.toObject(Note::class.java)?.copy(id = doc.id)
            ?: throw NoteOperationException("Note not found after update: $noteId")
    }

    override suspend fun appendToNote(noteId: String, text: String): Note {
        val noteRef = notesCollection.document(noteId)

        // Get current content
        val doc = noteRef.get().await()
        val note = doc.toObject(Note::class.java)
            ?: throw NoteOperationException("Note not found: $noteId")

        // Append text (with newline if content exists)
        val newContent = if (note.content.isEmpty()) {
            text
        } else {
            "${note.content}\n$text"
        }

        // Update the note
        noteRef.update(
            mapOf(
                "content" to newContent,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        return note.copy(
            id = doc.id,
            content = newContent,
            updatedAt = Timestamp.now()
        )
    }

    companion object {
        /**
         * Create a NoteRepositoryOperations instance if user is authenticated.
         * Returns null if no user is signed in.
         */
        fun createIfAuthenticated(db: FirebaseFirestore, userId: String?): NoteRepositoryOperations? {
            return userId?.let { NoteRepositoryOperations(db, it) }
        }
    }
}
