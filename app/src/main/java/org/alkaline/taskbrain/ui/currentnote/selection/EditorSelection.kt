package org.alkaline.taskbrain.ui.currentnote.selection

/**
 * Represents a selection range in the editor using global character offsets.
 */
data class EditorSelection(
    val start: Int,
    val end: Int
) {
    val min: Int get() = minOf(start, end)
    val max: Int get() = maxOf(start, end)
    val isCollapsed: Boolean get() = start == end
    val hasSelection: Boolean get() = start != end

    companion object {
        val None = EditorSelection(-1, -1)
    }
}
