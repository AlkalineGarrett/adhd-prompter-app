package org.alkaline.taskbrain.data

import com.google.firebase.Timestamp

/**
 * Represents an open tab in the recent tabs bar.
 * Stored in Firestore at /users/{userId}/openTabs/{noteId}
 */
data class RecentTab(
    val noteId: String = "",
    val displayText: String = "",
    val lastAccessedAt: Timestamp? = null
)
