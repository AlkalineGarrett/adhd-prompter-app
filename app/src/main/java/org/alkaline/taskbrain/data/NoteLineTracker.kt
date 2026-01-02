package org.alkaline.taskbrain.data

/**
 * Tracks note lines and their corresponding note IDs, using heuristics to match lines
 * across edits, insertions, and deletions.
 */
class NoteLineTracker(
    private val parentNoteId: String
) {
    private var trackedLines = listOf<NoteLine>()

    /**
     * Updates the tracked lines based on the new content provided by the user.
     * It uses a heuristic to match lines and preserve Note IDs across edits, insertions, and deletions.
     */
    fun updateTrackedLines(newContent: String) {
        val newLinesContent = newContent.lines()
        val oldLines = trackedLines
        val newTrackedLines = mutableListOf<NoteLine>()
        
        var oldIndex = 0
        var newIndex = 0
        
        while (newIndex < newLinesContent.size) {
            val newLine = newLinesContent[newIndex]
            
            // If we ran out of old lines, everything remaining is new
            if (oldIndex >= oldLines.size) {
                newTrackedLines.add(NoteLine(newLine, null))
                newIndex++
                continue
            }
            
            val oldLine = oldLines[oldIndex]
            
            // 1. Exact Match
            if (oldLine.content == newLine) {
                newTrackedLines.add(oldLine)
                oldIndex++
                newIndex++
                continue
            }
            
            // 2. Look ahead to detect Deletions or Insertions
            val lookAhead = 5
            
            // Check for Deletion: Does the current newLine match a future oldLine?
            // If so, the intermediate oldLines were deleted.
            var foundInOld = -1
            for (k in 1..lookAhead) {
                if (oldIndex + k < oldLines.size && oldLines[oldIndex + k].content == newLine) {
                    foundInOld = oldIndex + k
                    break
                }
            }
            
            // Check for Insertion: Does the current oldLine match a future newLine?
            // If so, the intermediate newLines are insertions.
            var foundInNew = -1
            for (k in 1..lookAhead) {
                if (newIndex + k < newLinesContent.size && newLinesContent[newIndex + k] == oldLine.content) {
                    foundInNew = newIndex + k
                    break
                }
            }
            
            if (foundInOld != -1 && (foundInNew == -1 || foundInOld < foundInNew)) {
                // Detected Deletion (or a closer match in old lines implies deletion is more likely)
                // Consume the match at foundInOld. The lines before it are skipped (deleted).
                newTrackedLines.add(oldLines[foundInOld])
                oldIndex = foundInOld + 1
                newIndex++
                continue
            }
            
            if (foundInNew != -1) {
                // Detected Insertion
                // The current newLine is an insertion. The oldLine is preserved for later.
                newTrackedLines.add(NoteLine(newLine, null))
                newIndex++
                continue
            }
            
            // 3. Fallback: Modification
            // Assume the current line was edited in place.
            val noteId = if (newTrackedLines.isEmpty()) parentNoteId else oldLines[oldIndex].noteId
            newTrackedLines.add(NoteLine(newLine, noteId))
            oldIndex++
            newIndex++
        }
        
        // Ensure first line has the parent ID
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
        val currentList = trackedLines.toMutableList()
        if (index < currentList.size) {
            currentList[index] = currentList[index].copy(noteId = noteId)
            trackedLines = currentList
        }
    }
}

data class NoteLine(
    val content: String,
    val noteId: String? = null // Null if it's a new line or hasn't been persisted yet
)

