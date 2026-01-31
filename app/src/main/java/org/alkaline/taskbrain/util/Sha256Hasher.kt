package org.alkaline.taskbrain.util

import java.security.MessageDigest

/**
 * Utility for computing SHA-256 hashes.
 */
object Sha256Hasher {

    /**
     * Compute a SHA-256 hash of a string, returning hex representation.
     */
    fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
