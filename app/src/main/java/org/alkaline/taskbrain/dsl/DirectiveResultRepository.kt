package org.alkaline.taskbrain.dsl

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for storing and retrieving directive execution results.
 *
 * Results are stored in Firestore as a subcollection under each note:
 * notes/{noteId}/directiveResults/{directiveHash}
 */
class DirectiveResultRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun resultsCollection(noteId: String) =
        db.collection("notes").document(noteId).collection("directiveResults")

    /**
     * Save a directive execution result.
     *
     * @param noteId The parent note ID
     * @param directiveHash The hash of the directive source text
     * @param result The execution result to save
     */
    suspend fun saveResult(
        noteId: String,
        directiveHash: String,
        result: DirectiveResult
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val data = hashMapOf(
                "result" to result.result,
                "executedAt" to FieldValue.serverTimestamp(),
                "error" to result.error,
                "collapsed" to result.collapsed
            )
            resultsCollection(noteId).document(directiveHash).set(data).await()
            Unit
        }
    }.onFailure { Log.e(TAG, "Error saving directive result", it) }

    /**
     * Get all directive results for a note.
     *
     * @param noteId The parent note ID
     * @return Map of directiveHash to DirectiveResult
     */
    suspend fun getResults(noteId: String): Result<Map<String, DirectiveResult>> = runCatching {
        withContext(Dispatchers.IO) {
            val snapshot = resultsCollection(noteId).get().await()
            snapshot.documents.associate { doc ->
                doc.id to (doc.toObject(DirectiveResult::class.java) ?: DirectiveResult())
            }
        }
    }.onFailure { Log.e(TAG, "Error getting directive results", it) }

    /**
     * Get a single directive result.
     *
     * @param noteId The parent note ID
     * @param directiveHash The hash of the directive source text
     * @return The DirectiveResult, or null if not found
     */
    suspend fun getResult(noteId: String, directiveHash: String): Result<DirectiveResult?> = runCatching {
        withContext(Dispatchers.IO) {
            val doc = resultsCollection(noteId).document(directiveHash).get().await()
            if (doc.exists()) {
                doc.toObject(DirectiveResult::class.java)
            } else {
                null
            }
        }
    }.onFailure { Log.e(TAG, "Error getting directive result", it) }

    /**
     * Update the collapsed state of a directive result.
     *
     * @param noteId The parent note ID
     * @param directiveHash The hash of the directive source text
     * @param collapsed The new collapsed state
     */
    suspend fun updateCollapsed(
        noteId: String,
        directiveHash: String,
        collapsed: Boolean
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            resultsCollection(noteId).document(directiveHash)
                .update("collapsed", collapsed)
                .await()
            Unit
        }
    }.onFailure { Log.e(TAG, "Error updating collapsed state", it) }

    /**
     * Delete all directive results for a note.
     * Should be called when a note is deleted.
     *
     * @param noteId The parent note ID
     */
    suspend fun deleteAllResults(noteId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val snapshot = resultsCollection(noteId).get().await()
            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Unit
        }
    }.onFailure { Log.e(TAG, "Error deleting directive results", it) }

    companion object {
        private const val TAG = "DirectiveResultRepo"
    }
}
