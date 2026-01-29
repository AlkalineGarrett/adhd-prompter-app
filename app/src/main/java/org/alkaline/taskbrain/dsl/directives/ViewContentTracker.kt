package org.alkaline.taskbrain.dsl.directives

import org.alkaline.taskbrain.data.Note

/**
 * Represents a range of content in a view that maps to a source note.
 *
 * @property startOffset The starting character offset in the displayed content
 * @property endOffset The ending character offset (exclusive) in the displayed content
 * @property sourceNoteId The ID of the note this content came from
 * @property sourceStartLine The starting line in the source note (0-indexed)
 * @property originalContent The original content from the source note (for detecting edits)
 *
 * Milestone 10.
 */
data class ViewRange(
    val startOffset: Int,
    val endOffset: Int,
    val sourceNoteId: String,
    val sourceStartLine: Int,
    val originalContent: String
) {
    /** The length of this range in characters */
    val length: Int get() = endOffset - startOffset

    /** Check if an offset is within this range */
    fun contains(offset: Int): Boolean = offset in startOffset until endOffset

    /** Check if this range overlaps with another range */
    fun overlaps(other: ViewRange): Boolean =
        startOffset < other.endOffset && endOffset > other.startOffset

    /** Check if this range overlaps with a character range */
    fun overlaps(start: Int, end: Int): Boolean =
        startOffset < end && endOffset > start
}

/**
 * Tracks the mapping between displayed view content and source notes.
 *
 * When a view directive renders content from multiple notes, this tracker
 * maintains the mapping so that edits can be propagated back to the correct
 * source notes on save.
 *
 * Usage:
 * 1. Call [buildFromNotes] to create a tracker from a list of viewed notes
 * 2. Use [mapOffsetToSource] to find which note a cursor position belongs to
 * 3. After user edits, call [updateRangesAfterEdit] to adjust offsets
 * 4. On save, use [getModifiedRanges] to find which source notes need updating
 *
 * Milestone 10.
 */
class ViewContentTracker {
    private val ranges = mutableListOf<ViewRange>()

    /**
     * The total length of the tracked content.
     */
    val totalLength: Int get() = ranges.lastOrNull()?.endOffset ?: 0

    /**
     * Check if the tracker has any ranges.
     */
    fun isEmpty(): Boolean = ranges.isEmpty()

    /**
     * Get all tracked ranges.
     */
    fun getRanges(): List<ViewRange> = ranges.toList()

    /**
     * Map an offset in the displayed content to its source note range.
     * Returns null if the offset is not within any tracked range.
     */
    fun mapOffsetToSource(offset: Int): ViewRange? {
        return ranges.find { it.contains(offset) }
    }

    /**
     * Find all ranges that overlap with a selection range.
     */
    fun findRangesInSelection(selectionStart: Int, selectionEnd: Int): List<ViewRange> {
        return ranges.filter { it.overlaps(selectionStart, selectionEnd) }
    }

    /**
     * Update range offsets after an edit operation.
     *
     * @param editStart The starting offset of the edit
     * @param editEnd The ending offset of the edit (original, before the edit)
     * @param newLength The new length of the edited content
     */
    fun updateRangesAfterEdit(editStart: Int, editEnd: Int, newLength: Int) {
        val delta = newLength - (editEnd - editStart)

        ranges.forEachIndexed { index, range ->
            when {
                // Edit is completely before this range - shift the range
                editEnd <= range.startOffset -> {
                    ranges[index] = range.copy(
                        startOffset = range.startOffset + delta,
                        endOffset = range.endOffset + delta
                    )
                }
                // Edit is completely within this range - expand/contract the range
                editStart >= range.startOffset && editEnd <= range.endOffset -> {
                    ranges[index] = range.copy(
                        endOffset = range.endOffset + delta
                    )
                }
                // Edit overlaps start of range
                editStart < range.startOffset && editEnd > range.startOffset && editEnd <= range.endOffset -> {
                    ranges[index] = range.copy(
                        startOffset = editStart + newLength,
                        endOffset = range.endOffset + delta
                    )
                }
                // Edit overlaps end of range
                editStart >= range.startOffset && editStart < range.endOffset && editEnd > range.endOffset -> {
                    ranges[index] = range.copy(
                        endOffset = editStart + newLength
                    )
                }
                // Edit completely contains this range
                editStart <= range.startOffset && editEnd >= range.endOffset -> {
                    ranges[index] = range.copy(
                        startOffset = editStart,
                        endOffset = editStart + newLength
                    )
                }
                // Edit is completely after this range - no change needed
            }
        }
    }

    /**
     * Check if any range has been modified from its original content.
     *
     * @param currentContent The current full content being displayed
     */
    fun getModifiedRanges(currentContent: String): List<ViewRange> {
        return ranges.filter { range ->
            val currentRangeContent = if (range.startOffset < currentContent.length) {
                currentContent.substring(
                    range.startOffset,
                    minOf(range.endOffset, currentContent.length)
                )
            } else ""
            currentRangeContent != range.originalContent
        }
    }

    /**
     * Get the current content for a specific range.
     */
    fun getCurrentContent(range: ViewRange, currentContent: String): String {
        return if (range.startOffset < currentContent.length) {
            currentContent.substring(
                range.startOffset,
                minOf(range.endOffset, currentContent.length)
            )
        } else ""
    }

    /**
     * Add a range to the tracker.
     */
    internal fun addRange(range: ViewRange) {
        ranges.add(range)
    }

    /**
     * Clear all tracked ranges.
     */
    fun clear() {
        ranges.clear()
    }

    companion object {
        /**
         * Divider string used between notes in a view.
         */
        const val NOTE_DIVIDER = "\n---\n"

        /**
         * Build a tracker from a list of notes.
         * Creates ranges for each note's content, accounting for dividers between notes.
         *
         * @param notes The notes being viewed
         * @return A new ViewContentTracker with ranges for all notes
         */
        fun buildFromNotes(notes: List<Note>): ViewContentTracker {
            val tracker = ViewContentTracker()
            var currentOffset = 0

            notes.forEachIndexed { index, note ->
                val content = note.content
                val range = ViewRange(
                    startOffset = currentOffset,
                    endOffset = currentOffset + content.length,
                    sourceNoteId = note.id,
                    sourceStartLine = 0,
                    originalContent = content
                )
                tracker.addRange(range)
                currentOffset += content.length

                // Add divider offset between notes (but not after the last note)
                if (index < notes.size - 1) {
                    currentOffset += NOTE_DIVIDER.length
                }
            }

            return tracker
        }
    }
}
