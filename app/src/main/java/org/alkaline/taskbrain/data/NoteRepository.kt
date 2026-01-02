package org.alkaline.taskbrain.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for managing composable notes in Firestore.
 * Notes can contain other notes via the containedNotes field.
 */
class NoteRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val notesCollection get() = db.collection("notes")

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User not signed in")

    private fun noteRef(noteId: String): DocumentReference = notesCollection.document(noteId)

    private fun newNoteRef(): DocumentReference = notesCollection.document()

    private fun baseNoteData(userId: String, content: String) = hashMapOf(
        "userId" to userId,
        "content" to content,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun newNoteData(userId: String, content: String, parentNoteId: String? = null): HashMap<String, Any?> =
        hashMapOf(
            "userId" to userId,
            "content" to content,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "parentNoteId" to parentNoteId
        )

    /**
     * Loads a note and its child notes, returning a flat list of NoteLines.
     */
    suspend fun loadNoteWithChildren(noteId: String): Result<List<NoteLine>> = runCatching {
        withContext(Dispatchers.IO) {
            requireUserId()
            val document = noteRef(noteId).get().await()

            if (!document.exists()) {
                return@withContext listOf(NoteLine("", noteId))
            }

            val note = document.toObject(Note::class.java)
                ?: return@withContext listOf(NoteLine("", noteId))

            val parentLine = NoteLine(note.content, noteId)
            val childLines = loadChildNotes(note.containedNotes)

            listOf(parentLine) + childLines
        }
    }.onFailure { Log.e(TAG, "Error loading note", it) }

    private suspend fun loadChildNotes(childIds: List<String>): List<NoteLine> =
        withContext(Dispatchers.IO) {
            childIds.map { childId ->
                async { loadChildNote(childId) }
            }.awaitAll()
        }

    private suspend fun loadChildNote(childId: String): NoteLine {
        if (childId.isEmpty()) return NoteLine("", null)

        return try {
            val childDoc = noteRef(childId).get().await()
            if (childDoc.exists()) {
                val content = childDoc.toObject(Note::class.java)?.content ?: ""
                NoteLine(content, childId)
            } else {
                NoteLine("", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching child note $childId", e)
            NoteLine("", null)
        }
    }

    /**
     * Saves a note with its child notes structure.
     * Returns a map of line indices to newly created note IDs.
     */
    suspend fun saveNoteWithChildren(
        noteId: String,
        trackedLines: List<NoteLine>
    ): Result<Map<Int, String>> = runCatching {
        withContext(Dispatchers.IO) {
            val userId = requireUserId()
            val parentRef = noteRef(noteId)

            db.runTransaction { transaction ->
                val oldChildIds = getExistingChildIds(transaction, parentRef)
                val idsToDelete = oldChildIds.filter { it.isNotEmpty() }.toMutableSet()

                val parentContent = trackedLines.firstOrNull()?.content ?: ""
                val childLines = trackedLines.drop(1)

                val (newContainedNotes, createdIds) = processChildLines(
                    transaction, userId, noteId, childLines, idsToDelete
                )

                updateParentNote(transaction, parentRef, userId, parentContent, newContainedNotes)
                softDeleteRemovedNotes(transaction, idsToDelete)

                createdIds
            }.await()
        }
    }.onFailure { Log.e(TAG, "Error saving note", it) }

    private fun getExistingChildIds(transaction: Transaction, parentRef: DocumentReference): List<String> {
        val snapshot = transaction.get(parentRef)
        if (!snapshot.exists()) return emptyList()
        @Suppress("UNCHECKED_CAST")
        return snapshot.get("containedNotes") as? List<String> ?: emptyList()
    }

    private fun processChildLines(
        transaction: Transaction,
        userId: String,
        parentNoteId: String,
        childLines: List<NoteLine>,
        idsToDelete: MutableSet<String>
    ): Pair<List<String>, Map<Int, String>> {
        val newContainedNotes = mutableListOf<String>()
        val createdIds = mutableMapOf<Int, String>()

        childLines.forEachIndexed { index, line ->
            val lineIndex = index + 1 // Offset for parent line
            val childId = processChildLine(transaction, userId, parentNoteId, line, idsToDelete)
            newContainedNotes.add(childId)
            if (line.noteId == null && line.content.isNotEmpty()) {
                createdIds[lineIndex] = childId
            }
        }

        return newContainedNotes to createdIds
    }

    private fun processChildLine(
        transaction: Transaction,
        userId: String,
        parentNoteId: String,
        line: NoteLine,
        idsToDelete: MutableSet<String>
    ): String {
        if (line.content.isEmpty()) return ""

        return if (line.noteId != null) {
            updateExistingChild(transaction, line.noteId, line.content)
            idsToDelete.remove(line.noteId)
            line.noteId
        } else {
            createNewChild(transaction, userId, parentNoteId, line.content)
        }
    }

    private fun updateExistingChild(transaction: Transaction, noteId: String, content: String) {
        transaction.set(
            noteRef(noteId),
            mapOf("content" to content, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge()
        )
    }

    private fun createNewChild(
        transaction: Transaction,
        userId: String,
        parentNoteId: String,
        content: String
    ): String {
        val newRef = newNoteRef()
        transaction.set(newRef, newNoteData(userId, content, parentNoteId))
        return newRef.id
    }

    private fun updateParentNote(
        transaction: Transaction,
        parentRef: DocumentReference,
        userId: String,
        content: String,
        containedNotes: List<String>
    ) {
        val data = baseNoteData(userId, content).apply {
            put("containedNotes", containedNotes)
        }
        transaction.set(parentRef, data, SetOptions.merge())
    }

    private fun softDeleteRemovedNotes(transaction: Transaction, idsToDelete: Set<String>) {
        for (id in idsToDelete) {
            transaction.update(
                noteRef(id),
                mapOf("state" to "deleted", "updatedAt" to FieldValue.serverTimestamp())
            )
        }
    }

    /**
     * Loads all top-level notes for the current user (excludes children and deleted).
     */
    suspend fun loadUserNotes(): Result<List<Note>> = runCatching {
        withContext(Dispatchers.IO) {
            val userId = requireUserId()
            val result = notesCollection.whereEqualTo("userId", userId).get().await()

            result.mapNotNull { doc ->
                try {
                    doc.toObject(Note::class.java).copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing note", e)
                    null
                }
            }.filter { it.parentNoteId == null && it.state != "deleted" }
        }
    }.onFailure { Log.e(TAG, "Error loading notes", it) }

    /**
     * Creates a new empty note.
     */
    suspend fun createNote(): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val userId = requireUserId()
            val ref = notesCollection.add(newNoteData(userId, "")).await()
            Log.d(TAG, "Note created with ID: ${ref.id}")
            ref.id
        }
    }.onFailure { Log.e(TAG, "Error creating note", it) }

    /**
     * Creates a new multi-line note (parent + children) using a batch operation.
     */
    suspend fun createMultiLineNote(content: String): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val userId = requireUserId()
            val lines = content.lines()
            val firstLine = lines.firstOrNull() ?: ""
            val childLines = lines.drop(1)

            val batch = db.batch()
            val parentRef = newNoteRef()

            val childIds = childLines.map { line ->
                if (line.isNotEmpty()) {
                    val childRef = newNoteRef()
                    batch.set(childRef, newNoteData(userId, line, parentRef.id))
                    childRef.id
                } else {
                    ""
                }
            }

            val parentData = newNoteData(userId, firstLine).apply {
                put("containedNotes", childIds)
            }
            batch.set(parentRef, parentData)
            batch.commit().await()

            Log.d(TAG, "Multi-line note created with ID: ${parentRef.id}")
            parentRef.id
        }
    }.onFailure { Log.e(TAG, "Error creating multi-line note", it) }

    companion object {
        private const val TAG = "NoteRepository"
    }
}
