package org.alkaline.taskbrain.dsl.cache

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firestore-backed directive cache (L2).
 *
 * Phase 6: Firestore persistence layer.
 *
 * Provides persistent caching across app sessions. Used as a fallback
 * when L1 (in-memory) cache misses.
 *
 * Storage locations:
 * - Global cache: `directiveCache/{directiveHash}`
 * - Per-note cache: `notes/{noteId}/directiveResults/{directiveHash}`
 */
class FirestoreDirectiveCache(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User not signed in")

    // region Global Cache

    /**
     * Get a globally cached result from Firestore.
     *
     * @param directiveHash The cache key (hash of normalized AST)
     * @return Cached result if present, null otherwise
     */
    suspend fun getGlobal(directiveHash: String): CachedDirectiveResult? = withContext(Dispatchers.IO) {
        try {
            val userId = requireUserId()
            val doc = db.collection("users")
                .document(userId)
                .collection("directiveCache")
                .document(directiveHash)
                .get()
                .await()

            if (!doc.exists()) return@withContext null

            val persisted = doc.toObject(PersistedDirectiveResult::class.java)
                ?: return@withContext null

            PersistedDirectiveResult.toCachedResult(persisted)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading global cache for $directiveHash", e)
            null
        }
    }

    /**
     * Store a result in the global Firestore cache.
     *
     * @param directiveHash The cache key
     * @param result The result to cache
     */
    suspend fun putGlobal(directiveHash: String, result: CachedDirectiveResult) = withContext(Dispatchers.IO) {
        try {
            val userId = requireUserId()
            val persisted = PersistedDirectiveResult.fromCachedResult(result)

            db.collection("users")
                .document(userId)
                .collection("directiveCache")
                .document(directiveHash)
                .set(persisted)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error writing global cache for $directiveHash", e)
        }
    }

    /**
     * Remove a result from the global Firestore cache.
     */
    suspend fun removeGlobal(directiveHash: String) = withContext(Dispatchers.IO) {
        try {
            val userId = requireUserId()
            db.collection("users")
                .document(userId)
                .collection("directiveCache")
                .document(directiveHash)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error removing global cache for $directiveHash", e)
        }
    }

    // endregion

    // region Per-Note Cache

    /**
     * Get a per-note cached result from Firestore.
     *
     * @param noteId The note containing the directive
     * @param directiveHash The cache key
     * @return Cached result if present, null otherwise
     */
    suspend fun getPerNote(noteId: String, directiveHash: String): CachedDirectiveResult? = withContext(Dispatchers.IO) {
        try {
            val userId = requireUserId()
            val doc = db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .collection("directiveResults")
                .document(directiveHash)
                .get()
                .await()

            if (!doc.exists()) return@withContext null

            val persisted = doc.toObject(PersistedDirectiveResult::class.java)
                ?: return@withContext null

            PersistedDirectiveResult.toCachedResult(persisted)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading per-note cache for $noteId/$directiveHash", e)
            null
        }
    }

    /**
     * Store a result in the per-note Firestore cache.
     *
     * @param noteId The note containing the directive
     * @param directiveHash The cache key
     * @param result The result to cache
     */
    suspend fun putPerNote(noteId: String, directiveHash: String, result: CachedDirectiveResult) = withContext(Dispatchers.IO) {
        try {
            val userId = requireUserId()
            val persisted = PersistedDirectiveResult.fromCachedResult(result)

            db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .collection("directiveResults")
                .document(directiveHash)
                .set(persisted)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error writing per-note cache for $noteId/$directiveHash", e)
        }
    }

    /**
     * Remove a result from the per-note Firestore cache.
     */
    suspend fun removePerNote(noteId: String, directiveHash: String) = withContext(Dispatchers.IO) {
        try {
            val userId = requireUserId()
            db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .collection("directiveResults")
                .document(directiveHash)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Error removing per-note cache for $noteId/$directiveHash", e)
        }
    }

    /**
     * Clear all cached results for a specific note.
     *
     * Note: Firestore doesn't support deleting subcollections directly,
     * so this fetches all documents and deletes them individually.
     */
    suspend fun clearNote(noteId: String) = withContext(Dispatchers.IO) {
        try {
            val userId = requireUserId()
            val collection = db.collection("users")
                .document(userId)
                .collection("notes")
                .document(noteId)
                .collection("directiveResults")

            // Fetch all documents in the collection
            val docs = collection.get().await()

            // Delete in batches
            val batch = db.batch()
            for (doc in docs.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing per-note cache for $noteId", e)
        }
    }

    // endregion

    companion object {
        private const val TAG = "FirestoreDirectiveCache"
    }
}

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
 * Adapter to make FirestoreDirectiveCache implement L2DirectiveCache.
 */
class FirestoreL2Cache(
    private val firestoreCache: FirestoreDirectiveCache = FirestoreDirectiveCache()
) : L2DirectiveCache {
    override suspend fun getGlobal(directiveHash: String) = firestoreCache.getGlobal(directiveHash)
    override suspend fun putGlobal(directiveHash: String, result: CachedDirectiveResult) {
        firestoreCache.putGlobal(directiveHash, result)
    }
    override suspend fun removeGlobal(directiveHash: String) {
        firestoreCache.removeGlobal(directiveHash)
    }

    override suspend fun getPerNote(noteId: String, directiveHash: String) = firestoreCache.getPerNote(noteId, directiveHash)
    override suspend fun putPerNote(noteId: String, directiveHash: String, result: CachedDirectiveResult) {
        firestoreCache.putPerNote(noteId, directiveHash, result)
    }
    override suspend fun removePerNote(noteId: String, directiveHash: String) {
        firestoreCache.removePerNote(noteId, directiveHash)
    }
    override suspend fun clearNote(noteId: String) {
        firestoreCache.clearNote(noteId)
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
