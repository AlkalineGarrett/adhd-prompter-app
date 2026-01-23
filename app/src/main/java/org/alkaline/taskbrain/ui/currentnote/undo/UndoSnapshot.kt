package org.alkaline.taskbrain.ui.currentnote.undo

import com.google.firebase.Timestamp

/**
 * Snapshot of editor state for undo/redo operations.
 * Captures the full state at an undo boundary.
 */
data class UndoSnapshot(
    val lineContents: List<String>,
    val focusedLineIndex: Int,
    val cursorPosition: Int,
    val createdAlarm: AlarmSnapshot? = null
)

/**
 * Snapshot of alarm data for undo/redo.
 * Stores all data needed to recreate an alarm on redo.
 */
data class AlarmSnapshot(
    val id: String,
    val noteId: String,
    val lineContent: String,
    val upcomingTime: Timestamp?,
    val notifyTime: Timestamp?,
    val urgentTime: Timestamp?,
    val alarmTime: Timestamp?
)
