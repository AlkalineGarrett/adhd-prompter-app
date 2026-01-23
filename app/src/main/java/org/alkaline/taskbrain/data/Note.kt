package org.alkaline.taskbrain.data

import com.google.firebase.Timestamp

/** IMPORTANT: Keep in sync with schema.md **/
data class Note(
    val id: String = "",
    val userId: String = "",
    val parentNoteId: String? = null,
    val content: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val lastAccessedAt: Timestamp? = null,
    val tags: List<String> = emptyList(),
    val containedNotes: List<String> = emptyList(),
    val state: String? = null,
)
