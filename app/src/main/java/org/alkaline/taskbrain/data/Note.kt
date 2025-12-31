package org.alkaline.taskbrain.data

import com.google.firebase.Timestamp

data class Note(
    val id: String = "",
    val content: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val tags: List<String> = emptyList(),
    val primaryLinks: List<String> = emptyList(),
    val userId: String = ""
)
