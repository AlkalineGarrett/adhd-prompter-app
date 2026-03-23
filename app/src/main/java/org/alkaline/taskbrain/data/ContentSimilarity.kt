package org.alkaline.taskbrain.data

/**
 * Content similarity utilities for matching edited lines to their originals.
 *
 * Used by line matching algorithms (NoteLineTracker, EditorState.updateFromText)
 * to assign note IDs to the line fragment with the highest proportion of original
 * content after a line split.
 */

/**
 * Returns the proportion of [oldContent] that appears in [newContent],
 * measured by longest common subsequence length / old content length.
 * Returns 0.0 early if the strings share no characters.
 */
fun contentOverlapProportion(oldContent: String, newContent: String): Double {
    if (oldContent.isEmpty()) return 0.0
    if (!sharesAnyCharacter(oldContent, newContent)) return 0.0
    return lcsLength(oldContent, newContent).toDouble() / oldContent.length.toDouble()
}

private fun sharesAnyCharacter(a: String, b: String): Boolean {
    val charSet = a.toSet()
    return b.any { it in charSet }
}

/**
 * Computes the length of the longest common subsequence between two strings.
 * Space-optimized to O(min(m, n)).
 */
fun lcsLength(a: String, b: String): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    val m = a.length
    val n = b.length
    var prev = IntArray(n + 1)
    var curr = IntArray(n + 1)
    for (i in 1..m) {
        for (j in 1..n) {
            curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1
            else maxOf(prev[j], curr[j - 1])
        }
        val temp = prev
        prev = curr
        curr = temp
        curr.fill(0)
    }
    return prev[n]
}

/**
 * Matches unmatched new lines to unconsumed old lines by content similarity.
 *
 * For each (old, new) pair, computes the proportion of old content present in the
 * new content (via LCS). Greedily assigns the highest-proportion match first, ensuring
 * that when a line is split, the fragment with more original content keeps the ID.
 *
 * @param oldContents text of old lines
 * @param newContents text of new lines
 * @param oldConsumed which old lines are already matched; updated in place
 * @param newMatched which new lines are already matched (non-null entries); updated in place
 * @param getOldContent extracts the text to compare from old line index
 * @param getNewContent extracts the text to compare from new line index
 */
fun performSimilarityMatching(
    unmatchedNewIndices: Set<Int>,
    unconsumedOldIndices: List<Int>,
    getOldContent: (Int) -> String,
    getNewContent: (Int) -> String,
    onMatch: (oldIdx: Int, newIdx: Int) -> Unit
) {
    data class Match(val oldIdx: Int, val newIdx: Int, val proportion: Double)

    val candidates = mutableListOf<Match>()
    for (oldIdx in unconsumedOldIndices) {
        val oldContent = getOldContent(oldIdx)
        for (newIdx in unmatchedNewIndices) {
            val proportion = contentOverlapProportion(oldContent, getNewContent(newIdx))
            if (proportion > 0.0) {
                candidates.add(Match(oldIdx, newIdx, proportion))
            }
        }
    }
    candidates.sortByDescending { it.proportion }
    val matchedOld = mutableSetOf<Int>()
    val matchedNew = mutableSetOf<Int>()
    for (match in candidates) {
        if (match.oldIdx in matchedOld || match.newIdx in matchedNew) continue
        onMatch(match.oldIdx, match.newIdx)
        matchedOld.add(match.oldIdx)
        matchedNew.add(match.newIdx)
    }
}

/**
 * Determines which half of a split line should keep the noteIds.
 *
 * @param noteIds the original line's noteIds
 * @param beforeContentLen length of content before the split point (excluding prefix)
 * @param afterContentLen length of content after the split point
 * @param beforeHasContent whether the before half has any content
 * @param afterHasContent whether the after half has any content
 * @return pair of (currentLineNoteIds, newLineNoteIds)
 */
fun splitNoteIds(
    noteIds: List<String>,
    beforeContentLen: Int,
    afterContentLen: Int,
    beforeHasContent: Boolean,
    afterHasContent: Boolean
): Pair<List<String>, List<String>> = when {
    !beforeHasContent && afterHasContent -> emptyList<String>() to noteIds
    beforeHasContent && !afterHasContent -> noteIds to emptyList()
    else -> if (beforeContentLen >= afterContentLen) noteIds to emptyList()
    else emptyList<String>() to noteIds
}
