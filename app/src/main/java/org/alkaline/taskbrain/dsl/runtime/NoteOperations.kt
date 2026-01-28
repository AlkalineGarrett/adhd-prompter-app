package org.alkaline.taskbrain.dsl.runtime

import org.alkaline.taskbrain.data.Note

/**
 * Interface for note mutation operations in Mindl.
 * This abstraction allows Mindl to perform note modifications without
 * direct dependency on the repository, enabling easier testing.
 *
 * Milestone 7.
 */
interface NoteOperations {
    /**
     * Create a new note with the given path and content.
     * @param path The unique path for the note
     * @param content The initial content
     * @return The created note
     * @throws NoteOperationException if a note already exists at the path
     */
    suspend fun createNote(path: String, content: String): Note

    /**
     * Get a note by its ID with fresh data from Firestore.
     * @param noteId The ID of the note
     * @return The note if found, null otherwise
     */
    suspend fun getNoteById(noteId: String): Note?

    /**
     * Find a note by its exact path.
     * @param path The path to search for
     * @return The note if found, null otherwise
     */
    suspend fun findByPath(path: String): Note?

    /**
     * Check if a note exists at the given path.
     * @param path The path to check
     * @return true if a note exists at the path
     */
    suspend fun noteExistsAtPath(path: String): Boolean

    /**
     * Update a note's path.
     * @param noteId The ID of the note to update
     * @param newPath The new path value
     * @return The updated note
     */
    suspend fun updatePath(noteId: String, newPath: String): Note

    /**
     * Update a note's content.
     * @param noteId The ID of the note to update
     * @param newContent The new content value
     * @return The updated note
     */
    suspend fun updateContent(noteId: String, newContent: String): Note

    /**
     * Append text to a note's content.
     * @param noteId The ID of the note
     * @param text The text to append (will be added on a new line)
     * @return The updated note
     */
    suspend fun appendToNote(noteId: String, text: String): Note
}

/**
 * Exception thrown when a note operation fails.
 */
class NoteOperationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
