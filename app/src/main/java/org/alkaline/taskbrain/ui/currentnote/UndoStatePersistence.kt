package org.alkaline.taskbrain.ui.currentnote

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

/**
 * Handles persistence of undo/redo state to survive app backgrounding.
 *
 * State is stored per-note in JSON files in the app's cache directory.
 * All persisted state is cleared on app cold start (session boundary).
 */
object UndoStatePersistence {
    private const val TAG = "UndoStatePersistence"
    private const val UNDO_STATE_DIR = "undo_state"

    /**
     * Clears all persisted undo state. Call on app cold start.
     */
    fun clearAllPersistedState(context: Context) {
        try {
            val dir = getUndoStateDir(context)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
                Log.d(TAG, "Cleared all persisted undo state")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing persisted undo state", e)
        }
    }

    /**
     * Saves undo state for a specific note (async version).
     */
    suspend fun saveState(context: Context, noteId: String, undoManager: UndoManager) {
        withContext(Dispatchers.IO) {
            saveStateInternal(context, noteId, undoManager)
        }
    }

    /**
     * Saves undo state for a specific note (blocking version).
     * Use this in lifecycle callbacks where the save must complete before returning.
     * Safe to call from ON_STOP since undo state is small and writes quickly.
     */
    fun saveStateBlocking(context: Context, noteId: String, undoManager: UndoManager) {
        saveStateInternal(context, noteId, undoManager)
    }

    private fun saveStateInternal(context: Context, noteId: String, undoManager: UndoManager) {
        try {
            val state = undoManager.exportState()
            val json = serializeState(state)
            val file = getStateFile(context, noteId)
            file.parentFile?.mkdirs()
            file.writeText(json.toString())
            Log.d(TAG, "Saved undo state for note: $noteId")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving undo state for note: $noteId", e)
        }
    }

    /**
     * Restores undo state for a specific note.
     * Returns true if state was restored, false if no state existed.
     */
    suspend fun restoreState(context: Context, noteId: String, undoManager: UndoManager): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = getStateFile(context, noteId)
                if (!file.exists()) {
                    Log.d(TAG, "No persisted undo state for note: $noteId")
                    return@withContext false
                }
                val json = JSONObject(file.readText())
                val state = deserializeState(json)
                undoManager.importState(state)
                Log.d(TAG, "Restored undo state for note: $noteId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring undo state for note: $noteId", e)
                false
            }
        }
    }

    /**
     * Clears persisted state for a specific note.
     */
    fun clearStateForNote(context: Context, noteId: String) {
        try {
            val file = getStateFile(context, noteId)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Cleared persisted undo state for note: $noteId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing persisted undo state for note: $noteId", e)
        }
    }

    private fun getUndoStateDir(context: Context): File {
        return File(context.cacheDir, UNDO_STATE_DIR)
    }

    private fun getStateFile(context: Context, noteId: String): File {
        // Sanitize noteId for use as filename
        val safeNoteId = noteId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(getUndoStateDir(context), "$safeNoteId.json")
    }

    // =========================================================================
    // Serialization (internal for testing)
    // =========================================================================

    internal fun serializeState(state: UndoManagerState): JSONObject {
        return JSONObject().apply {
            put("undoStack", serializeSnapshotList(state.undoStack))
            put("redoStack", serializeSnapshotList(state.redoStack))
            put("baselineSnapshot", state.baselineSnapshot?.let { serializeSnapshot(it) })
            put("isAtBaseline", state.isAtBaseline)
            put("pendingSnapshot", state.pendingSnapshot?.let { serializeSnapshot(it) })
            put("editingLineIndex", state.editingLineIndex ?: JSONObject.NULL)
            put("lastCommandType", state.lastCommandType?.name ?: JSONObject.NULL)
            put("lastMoveLineRange", state.lastMoveLineRange?.let {
                JSONObject().apply {
                    put("first", it.first)
                    put("last", it.last)
                }
            } ?: JSONObject.NULL)
        }
    }

    internal fun deserializeState(json: JSONObject): UndoManagerState {
        return UndoManagerState(
            undoStack = deserializeSnapshotList(json.getJSONArray("undoStack")),
            redoStack = deserializeSnapshotList(json.getJSONArray("redoStack")),
            baselineSnapshot = if (json.isNull("baselineSnapshot") || !json.has("baselineSnapshot")) null
                else deserializeSnapshot(json.getJSONObject("baselineSnapshot")),
            isAtBaseline = json.optBoolean("isAtBaseline", false),
            pendingSnapshot = if (json.isNull("pendingSnapshot")) null
                else deserializeSnapshot(json.getJSONObject("pendingSnapshot")),
            editingLineIndex = if (json.isNull("editingLineIndex")) null
                else json.getInt("editingLineIndex"),
            lastCommandType = if (json.isNull("lastCommandType")) null
                else CommandType.valueOf(json.getString("lastCommandType")),
            lastMoveLineRange = if (!json.has("lastMoveLineRange") || json.isNull("lastMoveLineRange")) null
                else json.getJSONObject("lastMoveLineRange").let { rangeJson ->
                    rangeJson.getInt("first")..rangeJson.getInt("last")
                }
        )
    }

    private fun serializeSnapshotList(snapshots: List<UndoSnapshot>): JSONArray {
        return JSONArray().apply {
            snapshots.forEach { put(serializeSnapshot(it)) }
        }
    }

    private fun deserializeSnapshotList(array: JSONArray): List<UndoSnapshot> {
        return (0 until array.length()).map { i ->
            deserializeSnapshot(array.getJSONObject(i))
        }
    }

    private fun serializeSnapshot(snapshot: UndoSnapshot): JSONObject {
        return JSONObject().apply {
            put("lineContents", JSONArray(snapshot.lineContents))
            put("focusedLineIndex", snapshot.focusedLineIndex)
            put("cursorPosition", snapshot.cursorPosition)
            put("createdAlarm", snapshot.createdAlarm?.let { serializeAlarmSnapshot(it) })
        }
    }

    private fun deserializeSnapshot(json: JSONObject): UndoSnapshot {
        val lineContentsArray = json.getJSONArray("lineContents")
        val lineContents = (0 until lineContentsArray.length()).map { i ->
            lineContentsArray.getString(i)
        }
        return UndoSnapshot(
            lineContents = lineContents,
            focusedLineIndex = json.getInt("focusedLineIndex"),
            cursorPosition = json.getInt("cursorPosition"),
            createdAlarm = if (json.isNull("createdAlarm")) null
                else deserializeAlarmSnapshot(json.getJSONObject("createdAlarm"))
        )
    }

    private fun serializeAlarmSnapshot(alarm: AlarmSnapshot): JSONObject {
        return JSONObject().apply {
            put("id", alarm.id)
            put("noteId", alarm.noteId)
            put("lineContent", alarm.lineContent)
            put("upcomingTime", alarm.upcomingTime?.toDate()?.time ?: JSONObject.NULL)
            put("notifyTime", alarm.notifyTime?.toDate()?.time ?: JSONObject.NULL)
            put("urgentTime", alarm.urgentTime?.toDate()?.time ?: JSONObject.NULL)
            put("alarmTime", alarm.alarmTime?.toDate()?.time ?: JSONObject.NULL)
        }
    }

    private fun deserializeAlarmSnapshot(json: JSONObject): AlarmSnapshot {
        return AlarmSnapshot(
            id = json.getString("id"),
            noteId = json.getString("noteId"),
            lineContent = json.getString("lineContent"),
            upcomingTime = if (json.isNull("upcomingTime")) null
                else Timestamp(Date(json.getLong("upcomingTime"))),
            notifyTime = if (json.isNull("notifyTime")) null
                else Timestamp(Date(json.getLong("notifyTime"))),
            urgentTime = if (json.isNull("urgentTime")) null
                else Timestamp(Date(json.getLong("urgentTime"))),
            alarmTime = if (json.isNull("alarmTime")) null
                else Timestamp(Date(json.getLong("alarmTime")))
        )
    }
}

/**
 * Represents the full state of an UndoManager for serialization.
 */
data class UndoManagerState(
    val undoStack: List<UndoSnapshot>,
    val redoStack: List<UndoSnapshot>,
    val baselineSnapshot: UndoSnapshot?,
    val isAtBaseline: Boolean = false,
    val pendingSnapshot: UndoSnapshot?,
    val editingLineIndex: Int?,
    val lastCommandType: CommandType?,
    val lastMoveLineRange: IntRange? = null,
    val hasUncommittedChanges: Boolean = false
)
