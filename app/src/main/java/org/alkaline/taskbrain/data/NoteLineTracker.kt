package org.alkaline.taskbrain.data

/**
 * Tracks note lines and their corresponding note IDs, preserving IDs with content
 * across edits, insertions, deletions, and reordering.
 */
class NoteLineTracker(
    private val parentNoteId: String
) {
    private var trackedLines = listOf<NoteLine>()

    /**
     * Updates tracked lines based on new content.
     * Uses content-based matching to preserve IDs when lines are reordered,
     * with positional fallback for modifications.
     */
    fun updateTrackedLines(newContent: String) {
        val newLinesContent = newContent.lines()
        val oldLines = trackedLines

        if (oldLines.isEmpty()) {
            trackedLines = newLinesContent.mapIndexed { index, content ->
                NoteLine(content, if (index == 0) parentNoteId else null)
            }
            return
        }

        // Map content to list of indices in oldLines
        val contentToOldIndices = mutableMapOf<String, MutableList<Int>>()
        oldLines.forEachIndexed { index, line ->
            contentToOldIndices.getOrPut(line.content) { mutableListOf() }.add(index)
        }

        val newIds = arrayOfNulls<String>(newLinesContent.size)
        val oldConsumed = BooleanArray(oldLines.size)

        // Phase 1: Exact matches
        newLinesContent.forEachIndexed { index, content ->
            val indices = contentToOldIndices[content]
            if (!indices.isNullOrEmpty()) {
                val oldIdx = indices.removeAt(0)
                newIds[index] = oldLines[oldIdx].noteId
                oldConsumed[oldIdx] = true
            }
        }

        // Phase 2: Positional matches for modifications
        newLinesContent.forEachIndexed { index, content ->
            if (newIds[index] == null) {
                // Try to take ID from old line at same position if it wasn't consumed
                if (index < oldLines.size && !oldConsumed[index]) {
                    newIds[index] = oldLines[index].noteId
                    oldConsumed[index] = true
                }
            }
        }

        val newTrackedLines = newLinesContent.mapIndexed { index, content ->
            NoteLine(content, newIds[index])
        }.toMutableList()

        // Ensure first line always has parent ID
        if (newTrackedLines.isNotEmpty() && newTrackedLines[0].noteId != parentNoteId) {
            newTrackedLines[0] = newTrackedLines[0].copy(noteId = parentNoteId)
        }

        trackedLines = newTrackedLines
    }

    fun getTrackedLines(): List<NoteLine> = trackedLines

    fun setTrackedLines(lines: List<NoteLine>) {
        trackedLines = lines
    }

    fun updateLineNoteId(index: Int, noteId: String) {
        if (index < trackedLines.size) {
            val currentList = trackedLines.toMutableList()
            currentList[index] = currentList[index].copy(noteId = noteId)
            trackedLines = currentList
        }
    }
}

data class NoteLine(
    val content: String,
    val noteId: String? = null
)
