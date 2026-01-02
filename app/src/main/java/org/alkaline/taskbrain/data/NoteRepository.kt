package org.alkaline.taskbrain.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for managing note operations in Firestore.
 */
class NoteRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    
    /**
     * Loads a note and its child notes from Firestore.
     * Returns a list of NoteLine objects representing the parent and all child notes.
     */
    suspend fun loadNoteWithChildren(noteId: String): Result<List<NoteLine>> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
            if (user == null) {
                return@withContext Result.failure(Exception("User not signed in"))
            }

            val document = db.collection("notes").document(noteId).get().await()
            if (document != null && document.exists()) {
                val note = document.toObject(Note::class.java)
                if (note != null) {
                    val loadedLines = mutableListOf<NoteLine>()
                    // First line is the parent note itself
                    loadedLines.add(NoteLine(note.content, noteId))

                    if (note.containedNotes.isNotEmpty()) {
                        val childDeferreds = note.containedNotes.map { childId ->
                            async {
                                if (childId.isEmpty()) {
                                    NoteLine("", null) // Empty line, no ID yet
                                } else {
                                    try {
                                        val childDoc = db.collection("notes").document(childId).get().await()
                                        if (childDoc.exists()) {
                                            val content = childDoc.toObject(Note::class.java)?.content ?: ""
                                            NoteLine(content, childId)
                                        } else {
                                            NoteLine("", null) // Child doc missing, treat as empty new line
                                        }
                                    } catch (e: Exception) {
                                        Log.e("NoteRepository", "Error fetching child note $childId", e)
                                        NoteLine("", null)
                                    }
                                }
                            }
                        }
                        
                        val childLines = childDeferreds.awaitAll()
                        loadedLines.addAll(childLines)
                    }
                    
                    Result.success(loadedLines)
                } else {
                    Result.success(listOf(NoteLine("", noteId)))
                }
            } else {
                // Document doesn't exist yet (new user/note), treat as empty
                Result.success(listOf(NoteLine("", noteId)))
            }
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error loading note", e)
            Result.failure(e)
        }
    }

    /**
     * Saves a note with its child notes structure to Firestore.
     * Returns a map of line indices to newly created note IDs.
     */
    suspend fun saveNoteWithChildren(
        noteId: String,
        trackedLines: List<NoteLine>
    ): Result<Map<Int, String>> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
            if (user == null) {
                return@withContext Result.failure(Exception("User not signed in"))
            }

            val parentRef = db.collection("notes").document(noteId)
            
            // We use a transaction to ensure atomic updates of parent and child notes
            val newIdsMap = db.runTransaction { transaction ->
                val parentSnapshot = transaction.get(parentRef)
                val oldContainedNotes = if (parentSnapshot.exists()) {
                    parentSnapshot.get("containedNotes") as? List<String> ?: emptyList()
                } else {
                    emptyList()
                }
                
                // Identify IDs to delete: start with all old non-empty IDs
                // We will remove IDs from this set as we encounter them in the new content
                val idsToDelete = oldContainedNotes.filter { it.isNotEmpty() }.toMutableSet()
                val newContainedNotes = mutableListOf<String>()
                val createdIdsMap = mutableMapOf<Int, String>()
                
                // 1. Update Parent Note Content (First Line)
                val parentContent = trackedLines.firstOrNull()?.content ?: ""
                val parentUpdateData = hashMapOf<String, Any>(
                    "content" to parentContent,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "userId" to user.uid
                )
                
                // 2. Process Child Lines (starting from index 1)
                if (trackedLines.size > 1) {
                    for (i in 1 until trackedLines.size) {
                        val line = trackedLines[i]
                        
                        if (line.content.isNotEmpty()) {
                            if (line.noteId != null) {
                                // Existing child note: Update content
                                val childRef = db.collection("notes").document(line.noteId)
                                transaction.set(childRef, mapOf(
                                    "content" to line.content,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                ), SetOptions.merge())
                                
                                newContainedNotes.add(line.noteId)
                                idsToDelete.remove(line.noteId)
                            } else {
                                // New child note: Create
                                val newChildRef = db.collection("notes").document()
                                val newNoteData = hashMapOf(
                                    "userId" to user.uid,
                                    "content" to line.content,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp(),
                                    "parentNoteId" to noteId
                                )
                                transaction.set(newChildRef, newNoteData)
                                
                                newContainedNotes.add(newChildRef.id)
                                createdIdsMap[i] = newChildRef.id
                            }
                        } else {
                            // Empty line: Represents a gap/spacer
                            newContainedNotes.add("")
                        }
                    }
                }
                
                // 3. Update Parent with new structure
                parentUpdateData["containedNotes"] = newContainedNotes
                transaction.set(parentRef, parentUpdateData, SetOptions.merge())
                
                // 4. Soft delete removed notes
                for (idToDelete in idsToDelete) {
                    val docRef = db.collection("notes").document(idToDelete)
                    transaction.update(docRef, mapOf(
                        "state" to "deleted",
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
                
                createdIdsMap
            }.await()

            Log.d("NoteRepository", "Note saved successfully with structure.")
            Result.success(newIdsMap)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error saving note", e)
            Result.failure(e)
        }
    }

    /**
     * Loads all notes for the current user, filtering out child notes and deleted notes.
     */
    suspend fun loadUserNotes(): Result<List<Note>> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
            if (user == null) {
                return@withContext Result.failure(Exception("User not signed in"))
            }

            val result = db.collection("notes")
                .whereEqualTo("userId", user.uid)
                .get()
                .await()

            val notesList = mutableListOf<Note>()
            for (document in result) {
                try {
                    val note = document.toObject(Note::class.java).copy(id = document.id)
                    // Filter out child notes (parentNoteId != null) and deleted notes
                    if (note.parentNoteId == null && note.state != "deleted") {
                        notesList.add(note)
                    }
                } catch (e: Exception) {
                    Log.e("NoteRepository", "Error parsing note", e)
                }
            }

            Result.success(notesList)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error loading notes", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a new empty note.
     */
    suspend fun createNote(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
            if (user == null) {
                return@withContext Result.failure(Exception("User not signed in"))
            }

            val newNote = hashMapOf(
                "userId" to user.uid,
                "content" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "parentNoteId" to null
            )

            val documentReference = db.collection("notes")
                .add(newNote)
                .await()

            Log.d("NoteRepository", "Note created with ID: ${documentReference.id}")
            Result.success(documentReference.id)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error creating note", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a new note with multiple lines (parent + children) using a batch operation.
     */
    suspend fun createMultiLineNote(content: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
            if (user == null) {
                return@withContext Result.failure(Exception("User not signed in"))
            }

            val lines = content.lines()
            val firstLine = lines.firstOrNull() ?: ""
            val childLines = if (lines.size > 1) lines.subList(1, lines.size) else emptyList()

            val batch = db.batch()
            val parentNoteRef = db.collection("notes").document()
            val parentNoteId = parentNoteRef.id

            val childNoteIds = mutableListOf<String>()
            if (childLines.isNotEmpty()) {
                for (childLine in childLines) {
                    if (childLine.isNotBlank()) {
                        val childNoteRef = db.collection("notes").document()
                        childNoteIds.add(childNoteRef.id)
                        val newChildNote = hashMapOf(
                            "userId" to user.uid,
                            "content" to childLine,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "parentNoteId" to parentNoteId
                        )
                        batch.set(childNoteRef, newChildNote)
                    } else {
                        childNoteIds.add("")
                    }
                }
            }

            val newParentNote = hashMapOf(
                "userId" to user.uid,
                "content" to firstLine,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "containedNotes" to childNoteIds,
                "parentNoteId" to null
            )
            batch.set(parentNoteRef, newParentNote)

            batch.commit().await()

            Log.d("NoteRepository", "Multi-line note created with ID: $parentNoteId")
            Result.success(parentNoteId)
        } catch (e: Exception) {
            Log.e("NoteRepository", "Error creating multi-line note", e)
            Result.failure(e)
        }
    }
}
