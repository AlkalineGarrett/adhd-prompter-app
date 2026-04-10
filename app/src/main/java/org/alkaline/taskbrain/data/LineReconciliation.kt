package org.alkaline.taskbrain.data

/**
 * Pure functions for matching new content lines to old content lines and reconciling
 * per-line metadata (e.g., noteIds).
 *
 * This is the single shared implementation that backs all three places where the
 * editor / save path needs to recover noteIds from a plain-text snapshot:
 *
 *   - [org.alkaline.taskbrain.ui.currentnote.EditorState.updateFromText]
 *   - [org.alkaline.taskbrain.ui.currentnote.CurrentNoteViewModel.updateTrackedLines]
 *   - [NoteRepository] save-from-content path
 *
 * **Use this only as a fallback.** When the caller already has a `LineState`/`NoteLine`
 * list with noteIds attached, use those directly — text matching is lossy and any
 * unmatched line silently loses its id. Pass an [onUnmatchedNonEmpty] callback so the
 * caller can log when this happens.
 */

/** Sentinel returned by [matchLinesByContent] for an unmatched new line. */
const val LINE_MATCH_NONE: Int = -1

/**
 * Matches new lines to old lines via two-phase content matching:
 *
 * 1. **Exact content match**: identical strings are matched 1:1, in order, consuming
 *    duplicates left-to-right.
 * 2. **Similarity match**: remaining lines are matched by longest-common-subsequence
 *    overlap (see [performSimilarityMatching]).
 *
 * This intentionally does NOT do positional fallback — a line whose content has changed
 * beyond similarity recognition is treated as a new line. Inheriting an old noteId by
 * raw position is the source of save-time corruption when content drifts.
 *
 * @return an [IntArray] of length [newContents].size where `result[newIdx]` is the matched
 *   old index, or [LINE_MATCH_NONE] if unmatched.
 */
fun matchLinesByContent(
    oldContents: List<String>,
    newContents: List<String>,
): IntArray {
    val matches = IntArray(newContents.size) { LINE_MATCH_NONE }
    if (oldContents.isEmpty() || newContents.isEmpty()) return matches

    val oldConsumed = BooleanArray(oldContents.size)

    // Phase 1: exact content match (consume duplicates left-to-right)
    val contentToOldIndices = mutableMapOf<String, ArrayDeque<Int>>()
    oldContents.forEachIndexed { i, content ->
        contentToOldIndices.getOrPut(content) { ArrayDeque() }.addLast(i)
    }
    newContents.forEachIndexed { newIdx, content ->
        val queue = contentToOldIndices[content]
        if (queue != null && queue.isNotEmpty()) {
            val oldIdx = queue.removeFirst()
            matches[newIdx] = oldIdx
            oldConsumed[oldIdx] = true
        }
    }

    // Phase 2: similarity match for remaining lines
    performSimilarityMatching(
        unmatchedNewIndices = newContents.indices.filter { matches[it] == LINE_MATCH_NONE }.toSet(),
        unconsumedOldIndices = oldContents.indices.filter { !oldConsumed[it] },
        getOldContent = { oldContents[it] },
        getNewContent = { newContents[it] },
    ) { oldIdx, newIdx ->
        matches[newIdx] = oldIdx
        oldConsumed[oldIdx] = true
    }

    return matches
}

/**
 * Reconciles per-line noteIds from an old line list to a new content snapshot.
 *
 * For each new line, returns the noteIds list from the matched old line, or empty list
 * if unmatched. Empty input lines are silently unmatched (no callback invoked) — only
 * non-empty content lines that fail to match are reported via [onUnmatchedNonEmpty],
 * since those are the cases where useful noteIds are being dropped.
 *
 * @param oldContents old line texts
 * @param oldNoteIds old line noteIds (parallel to [oldContents]; same length)
 * @param newContents new line texts
 * @param onUnmatchedNonEmpty optional callback invoked once per non-empty new line that
 *   could not be matched to any old line. Use this to log diagnostics or surface a warning.
 *   The callback receives `(newIndex, content)` for the unmatched line.
 * @return a list of noteIds lists, one per new line, parallel to [newContents]
 */
fun reconcileLineNoteIds(
    oldContents: List<String>,
    oldNoteIds: List<List<String>>,
    newContents: List<String>,
    onUnmatchedNonEmpty: ((newIndex: Int, content: String) -> Unit)? = null,
): List<List<String>> {
    require(oldContents.size == oldNoteIds.size) {
        "oldContents.size=${oldContents.size} != oldNoteIds.size=${oldNoteIds.size}"
    }

    val matches = matchLinesByContent(oldContents, newContents)
    return List(newContents.size) { newIdx ->
        val oldIdx = matches[newIdx]
        if (oldIdx >= 0) {
            oldNoteIds[oldIdx]
        } else {
            if (onUnmatchedNonEmpty != null && newContents[newIdx].isNotEmpty()) {
                onUnmatchedNonEmpty(newIdx, newContents[newIdx])
            }
            emptyList()
        }
    }
}

/**
 * Ensures the first entry of [noteIds] has [parentNoteId] as its primary id.
 *
 * If line 0's primary already matches, returns [noteIds] unchanged. Otherwise prepends
 * [parentNoteId] to line 0's id list, removing any duplicate occurrence further down so
 * the parent appears exactly once. Other lines are untouched.
 *
 * No-op if [parentNoteId] is empty or [noteIds] is empty.
 */
fun enforceParentNoteId(
    noteIds: List<List<String>>,
    parentNoteId: String,
): List<List<String>> {
    if (parentNoteId.isEmpty() || noteIds.isEmpty()) return noteIds
    val first = noteIds[0]
    if (first.firstOrNull() == parentNoteId) return noteIds
    val newFirst = listOf(parentNoteId) + first.filter { it != parentNoteId }
    return listOf(newFirst) + noteIds.drop(1)
}
