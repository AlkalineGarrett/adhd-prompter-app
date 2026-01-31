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

            val allLines = listOf(parentLine) + childLines

            // Append an empty line for user to type on, unless the note is already
            // a single empty line (new note case - the existing empty line suffices)
            if (allLines.size == 1 && allLines[0].content.isEmpty()) {
                allLines
            } else {
                allLines + NoteLine("", null)
            }
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

            val parentContent = trackedLines.firstOrNull()?.content ?: ""

            val result = db.runTransaction { transaction ->
                val oldChildIds = getExistingChildIds(transaction, parentRef)
                val idsToDelete = oldChildIds.filter { it.isNotEmpty() }.toMutableSet()

                // Drop trailing empty lines (user's typing line) before saving
                val childLines = trackedLines.drop(1).dropLastWhile { it.content.isEmpty() }

                val (newContainedNotes, createdIds) = processChildLines(
                    transaction, userId, noteId, childLines, idsToDelete
                )

                updateParentNote(transaction, parentRef, userId, parentContent, newContainedNotes)
                softDeleteRemovedNotes(transaction, idsToDelete)

                createdIds
            }.await()

            result
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
     * Loads all top-level notes for the current user with their full content reconstructed.
     * Multi-line notes (those with containedNotes) will have their child content appended
     * to the parent content, separated by newlines.
     *
     * Use this method when you need the complete text of notes (e.g., for view() directives).
     * For just metadata/first line, use loadUserNotes() instead.
     */
    suspend fun loadNotesWithFullContent(): Result<List<Note>> = runCatching {
        withContext(Dispatchers.IO) {
            val userId = requireUserId()
            val result = notesCollection.whereEqualTo("userId", userId).get().await()

            val topLevelNotes = result.mapNotNull { doc ->
                try {
                    doc.toObject(Note::class.java).copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing note", e)
                    null
                }
            }.filter { it.parentNoteId == null && it.state != "deleted" }

            // Load full content for notes with children
            topLevelNotes.map { note ->
                async { reconstructNoteContent(note) }
            }.awaitAll()
        }
    }.onFailure { Log.e(TAG, "Error loading notes with full content", it) }

    /**
     * Reconstructs the full content of a note by loading its children.
     * Returns the note with content = parent content + newline-separated child contents.
     */
    private suspend fun reconstructNoteContent(note: Note): Note {
        if (note.containedNotes.isEmpty()) {
            return note
        }

        val childContents = loadChildNotes(note.containedNotes)
        val fullContent = buildString {
            append(note.content)
            for (childLine in childContents) {
                append('\n')
                append(childLine.content)
            }
        }
        return note.copy(content = fullContent)
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
     * Loads all top-level notes for the current user, including deleted ones.
     */
    suspend fun loadAllUserNotes(): Result<List<Note>> = runCatching {
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
            }.filter { it.parentNoteId == null }
        }
    }.onFailure { Log.e(TAG, "Error loading all notes", it) }

    /**
     * Loads a single note by ID.
     * Returns null if the note doesn't exist.
     */
    suspend fun loadNoteById(noteId: String): Result<Note?> = runCatching {
        withContext(Dispatchers.IO) {
            requireUserId()
            val document = noteRef(noteId).get().await()
            if (!document.exists()) return@withContext null
            document.toObject(Note::class.java)?.copy(id = document.id)
        }
    }.onFailure { Log.e(TAG, "Error loading note by ID: $noteId", it) }

    /**
     * Checks if a note is deleted.
     */
    suspend fun isNoteDeleted(noteId: String): Result<Boolean> = runCatching {
        withContext(Dispatchers.IO) {
            requireUserId()
            val document = noteRef(noteId).get().await()
            if (!document.exists()) return@withContext false
            val note = document.toObject(Note::class.java)
            note?.state == "deleted"
        }
    }.onFailure { Log.e(TAG, "Error checking note state", it) }

    /**
     * Soft-deletes a note by setting its state to "deleted".
     */
    suspend fun softDeleteNote(noteId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            requireUserId()
            noteRef(noteId).update(
                mapOf("state" to "deleted", "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Unit
        }
    }.onFailure { Log.e(TAG, "Error soft-deleting note", it) }

    /**
     * Restores a deleted note by clearing its state.
     */
    suspend fun undeleteNote(noteId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            requireUserId()
            noteRef(noteId).update(
                mapOf("state" to null, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Unit
        }
    }.onFailure { Log.e(TAG, "Error undeleting note", it) }

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

    /**
     * Updates the lastAccessedAt timestamp for a note.
     * Used to track recently accessed notes for the tabs bar.
     * This is a fire-and-forget operation - failures are logged but don't block UI.
     */
    suspend fun updateLastAccessed(noteId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            requireUserId()
            noteRef(noteId).update("lastAccessedAt", FieldValue.serverTimestamp()).await()
            Log.d(TAG, "Updated lastAccessedAt for note: $noteId")
            Unit
        }
    }.onFailure { Log.e(TAG, "Error updating lastAccessedAt", it) }

    /**
     * Saves a note with full multi-line content, properly handling child notes.
     * Used for inline editing of notes within view directives.
     *
     * This function:
     * 1. Loads the existing note structure (parent + children)
     * 2. Maps new lines to existing child note IDs using content matching
     * 3. Creates, updates, or deletes child notes as needed
     *
     * @param noteId The ID of the note to update
     * @param newContent The new full content (may be multi-line)
     * @return Result indicating success or failure
     */
    suspend fun saveNoteWithFullContent(noteId: String, newContent: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            requireUserId()

            Log.d(TAG, "saveNoteWithFullContent: START noteId=$noteId")
            Log.d(TAG, "saveNoteWithFullContent: newContent has ${newContent.lines().size} lines, first line='${newContent.lines().firstOrNull()}'")

            // Load existing note to get current structure
            val existingNote = loadNoteById(noteId).getOrNull()
                ?: throw IllegalStateException("Note not found: $noteId")

            Log.d(TAG, "saveNoteWithFullContent: existingNote.content='${existingNote.content}', containedNotes=${existingNote.containedNotes.size}")

            // Build tracked lines from existing structure
            val existingLines = buildExistingLines(noteId, existingNote)
            Log.d(TAG, "saveNoteWithFullContent: existingLines=${existingLines.size}, first='${existingLines.firstOrNull()?.content}'")

            // Split new content into lines
            val newLinesContent = newContent.lines()
            Log.d(TAG, "saveNoteWithFullContent: newLinesContent=${newLinesContent.size}, lines=${newLinesContent.take(3)}")

            // Match new lines to existing IDs (NoteLineTracker algorithm)
            val trackedLines = matchLinesToIds(noteId, existingLines, newLinesContent)
            Log.d(TAG, "saveNoteWithFullContent: trackedLines=${trackedLines.size}, first='${trackedLines.firstOrNull()?.content}', firstId=${trackedLines.firstOrNull()?.noteId}")

            // Save using existing saveNoteWithChildren logic
            saveNoteWithChildren(noteId, trackedLines).getOrThrow()

            Log.d(TAG, "Saved note with full content: $noteId (${trackedLines.size} lines)")

            // Verify what was saved by reloading (debug only)
            val verifyNote = loadNoteById(noteId).getOrNull()
            val verifyChildren = if (verifyNote?.containedNotes?.isNotEmpty() == true) {
                loadChildNotes(verifyNote.containedNotes)
            } else emptyList()
            Log.d(TAG, "saveNoteWithFullContent: VERIFY after save - parent content='${verifyNote?.content}', children=${verifyChildren.size}")
            if (verifyChildren.isNotEmpty()) {
                verifyChildren.forEachIndexed { idx, child ->
                    Log.d(TAG, "saveNoteWithFullContent: VERIFY child $idx: '${child.content}'")
                }
            }

            Unit
        }
    }.onFailure { Log.e(TAG, "Error saving note with full content", it) }

    /**
     * Builds a list of NoteLines from an existing note's structure.
     */
    private suspend fun buildExistingLines(noteId: String, note: Note): List<NoteLine> {
        val lines = mutableListOf<NoteLine>()

        // First line is the parent
        lines.add(NoteLine(note.content, noteId))

        // Load child notes if any
        if (note.containedNotes.isNotEmpty()) {
            val childLines = loadChildNotes(note.containedNotes)
            lines.addAll(childLines)
        }

        return lines
    }

    /**
     * Matches new line content to existing note IDs using a two-phase algorithm:
     * 1. Exact content matches (preserves IDs when lines are reordered)
     * 2. Positional fallback (for modified lines)
     */
    private fun matchLinesToIds(
        parentNoteId: String,
        existingLines: List<NoteLine>,
        newLinesContent: List<String>
    ): List<NoteLine> {
        if (existingLines.isEmpty()) {
            return newLinesContent.mapIndexed { index, content ->
                NoteLine(content, if (index == 0) parentNoteId else null)
            }
        }

        // Map content to list of indices in existing lines
        val contentToOldIndices = mutableMapOf<String, MutableList<Int>>()
        existingLines.forEachIndexed { index, line ->
            contentToOldIndices.getOrPut(line.content) { mutableListOf() }.add(index)
        }

        val newIds = arrayOfNulls<String>(newLinesContent.size)
        val oldConsumed = BooleanArray(existingLines.size)

        // Phase 1: Exact matches
        newLinesContent.forEachIndexed { index, content ->
            val indices = contentToOldIndices[content]
            if (!indices.isNullOrEmpty()) {
                val oldIdx = indices.removeAt(0)
                newIds[index] = existingLines[oldIdx].noteId
                oldConsumed[oldIdx] = true
            }
        }

        // Phase 2: Positional matches for modifications
        newLinesContent.forEachIndexed { index, content ->
            if (newIds[index] == null) {
                if (index < existingLines.size && !oldConsumed[index]) {
                    newIds[index] = existingLines[index].noteId
                    oldConsumed[index] = true
                }
            }
        }

        val trackedLines = newLinesContent.mapIndexed { index, content ->
            NoteLine(content, newIds[index])
        }.toMutableList()

        // Ensure first line always has parent ID
        if (trackedLines.isNotEmpty() && trackedLines[0].noteId != parentNoteId) {
            trackedLines[0] = trackedLines[0].copy(noteId = parentNoteId)
        }

        return trackedLines
    }

    companion object {
        private const val TAG = "NoteRepository"
    }
}
