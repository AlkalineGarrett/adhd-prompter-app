package org.alkaline.taskbrain.dsl.cache

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Interface for L2 cache operations.
 *
 * Allows for testing with mock implementations.
 */
interface L2DirectiveCache {
    suspend fun getGlobal(directiveHash: String): CachedDirectiveResult?
    suspend fun putGlobal(directiveHash: String, result: CachedDirectiveResult)
    suspend fun removeGlobal(directiveHash: String)

    suspend fun getPerNote(noteId: String, directiveHash: String): CachedDirectiveResult?
    suspend fun putPerNote(noteId: String, directiveHash: String, result: CachedDirectiveResult)
    suspend fun removePerNote(noteId: String, directiveHash: String)
    suspend fun clearNote(noteId: String)
}

/**
 * Firestore-backed directive cache (L2).
 *
 * Provides persistent caching across app sessions. Used as a fallback
 * when L1 (in-memory) cache misses.
 *
 * Storage locations:
 * - Global cache: `users/{userId}/directiveCache/{directiveHash}`
 * - Per-note cache: `users/{userId}/notes/{noteId}/directiveResults/{directiveHash}`
 */
class FirestoreDirectiveCache(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : L2DirectiveCache {

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User not signed in")

    /** Get the global cache collection for a user */
    private fun globalCollection(): CollectionReference =
        db.collection("users")
            .document(requireUserId())
            .collection("directiveCache")

    /** Get the per-note cache collection for a user's note */
    private fun perNoteCollection(noteId: String): CollectionReference =
        db.collection("users")
            .document(requireUserId())
            .collection("notes")
            .document(noteId)
            .collection("directiveResults")

    /** Generic get operation for any collection */
    private suspend fun get(
        collection: CollectionReference,
        key: String,
        logContext: String
    ): CachedDirectiveResult? = withContext(Dispatchers.IO) {
        try {
            val doc = collection.document(key).get().await()
            if (!doc.exists()) return@withContext null

            val persisted = doc.toObject(PersistedDirectiveResult::class.java)
                ?: return@withContext null

            PersistedDirectiveResult.toCachedResult(persisted)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading cache for $logContext", e)
            null
        }
    }

    /** Generic put operation for any collection */
    private suspend fun put(
        collection: CollectionReference,
        key: String,
        result: CachedDirectiveResult,
        logContext: String
    ) = withContext(Dispatchers.IO) {
        try {
            val persisted = PersistedDirectiveResult.fromCachedResult(result)
            collection.document(key).set(persisted).await()
        } catch (e: Exception) {
            Log.w(TAG, "Error writing cache for $logContext", e)
        }
    }

    /** Generic remove operation for any collection */
    private suspend fun remove(
        collection: CollectionReference,
        key: String,
        logContext: String
    ) = withContext(Dispatchers.IO) {
        try {
            collection.document(key).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Error removing cache for $logContext", e)
        }
    }

    // Global cache operations

    override suspend fun getGlobal(directiveHash: String): CachedDirectiveResult? =
        get(globalCollection(), directiveHash, "global/$directiveHash")

    override suspend fun putGlobal(directiveHash: String, result: CachedDirectiveResult) {
        put(globalCollection(), directiveHash, result, "global/$directiveHash")
    }

    override suspend fun removeGlobal(directiveHash: String) {
        remove(globalCollection(), directiveHash, "global/$directiveHash")
    }

    // Per-note cache operations

    override suspend fun getPerNote(noteId: String, directiveHash: String): CachedDirectiveResult? =
        get(perNoteCollection(noteId), directiveHash, "$noteId/$directiveHash")

    override suspend fun putPerNote(noteId: String, directiveHash: String, result: CachedDirectiveResult) {
        put(perNoteCollection(noteId), directiveHash, result, "$noteId/$directiveHash")
    }

    override suspend fun removePerNote(noteId: String, directiveHash: String) {
        remove(perNoteCollection(noteId), directiveHash, "$noteId/$directiveHash")
    }

    override suspend fun clearNote(noteId: String) = withContext(Dispatchers.IO) {
        try {
            val collection = perNoteCollection(noteId)
            val docs = collection.get().await()

            if (docs.isEmpty) return@withContext

            val batch = db.batch()
            for (doc in docs.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing per-note cache for $noteId", e)
        }
    }

    companion object {
        private const val TAG = "FirestoreDirectiveCache"
    }
}

/**
 * No-op L2 cache for testing or when persistence is disabled.
 */
class NoOpL2Cache : L2DirectiveCache {
    override suspend fun getGlobal(directiveHash: String): CachedDirectiveResult? = null
    override suspend fun putGlobal(directiveHash: String, result: CachedDirectiveResult) {}
    override suspend fun removeGlobal(directiveHash: String) {}

    override suspend fun getPerNote(noteId: String, directiveHash: String): CachedDirectiveResult? = null
    override suspend fun putPerNote(noteId: String, directiveHash: String, result: CachedDirectiveResult) {}
    override suspend fun removePerNote(noteId: String, directiveHash: String) {}
    override suspend fun clearNote(noteId: String) {}
}
