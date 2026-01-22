package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Represents the state of a single line in the editor.
 * Handles prefix extraction, cursor position, and line operations.
 */
class LineState(
    text: String,
    cursorPosition: Int = text.length
) {
    var text by mutableStateOf(text)
        private set
    var cursorPosition by mutableIntStateOf(cursorPosition.coerceIn(0, text.length))
        private set

    /** The prefix portion (tabs + bullet/checkbox) */
    val prefix: String get() = extractPrefix(text)

    /** The content portion (after the prefix) */
    val content: String get() {
        val p = prefix
        return if (p.length < text.length) text.substring(p.length) else ""
    }

    /** Cursor position relative to content (for the TextField) */
    val contentCursorPosition: Int get() = (cursorPosition - prefix.length).coerceIn(0, content.length)

    /**
     * Updates the content portion of the line, keeping the prefix.
     */
    fun updateContent(newContent: String, newContentCursor: Int) {
        text = prefix + newContent
        cursorPosition = prefix.length + newContentCursor.coerceIn(0, newContent.length)
    }

    /**
     * Updates the full line text and cursor position.
     */
    fun updateFull(newText: String, newCursor: Int) {
        text = newText
        cursorPosition = newCursor.coerceIn(0, newText.length)
    }

    /**
     * Adds a tab at the beginning of the line.
     */
    fun indent() {
        text = "\t" + text
        cursorPosition = (cursorPosition + 1).coerceIn(0, text.length)
    }

    /**
     * Removes a tab from the beginning of the line (if present).
     */
    fun unindent(): Boolean {
        if (text.startsWith("\t")) {
            text = text.substring(1)
            cursorPosition = (cursorPosition - 1).coerceAtLeast(0)
            return true
        }
        return false
    }

    /**
     * Toggles bullet prefix on the line.
     */
    fun toggleBullet() {
        val tabCount = text.takeWhile { it == '\t' }.length
        val tabs = text.substring(0, tabCount)
        val afterTabs = text.substring(tabCount)

        val (newAfterTabs, cursorDelta) = when {
            afterTabs.startsWith(LinePrefixes.BULLET) -> {
                afterTabs.substring(LinePrefixes.BULLET.length) to -LinePrefixes.BULLET.length
            }
            afterTabs.startsWith(LinePrefixes.CHECKBOX_UNCHECKED) -> {
                LinePrefixes.BULLET + afterTabs.substring(LinePrefixes.CHECKBOX_UNCHECKED.length) to
                    (LinePrefixes.BULLET.length - LinePrefixes.CHECKBOX_UNCHECKED.length)
            }
            afterTabs.startsWith(LinePrefixes.CHECKBOX_CHECKED) -> {
                LinePrefixes.BULLET + afterTabs.substring(LinePrefixes.CHECKBOX_CHECKED.length) to
                    (LinePrefixes.BULLET.length - LinePrefixes.CHECKBOX_CHECKED.length)
            }
            else -> {
                LinePrefixes.BULLET + afterTabs to LinePrefixes.BULLET.length
            }
        }

        text = tabs + newAfterTabs
        cursorPosition = (cursorPosition + cursorDelta).coerceIn(0, text.length)
    }

    /**
     * Toggles checkbox prefix on the line.
     * Cycles: nothing → unchecked → checked → removed
     */
    fun toggleCheckbox() {
        val tabCount = text.takeWhile { it == '\t' }.length
        val tabs = text.substring(0, tabCount)
        val afterTabs = text.substring(tabCount)

        val (newAfterTabs, cursorDelta) = when {
            afterTabs.startsWith(LinePrefixes.CHECKBOX_UNCHECKED) -> {
                LinePrefixes.CHECKBOX_CHECKED + afterTabs.substring(LinePrefixes.CHECKBOX_UNCHECKED.length) to 0
            }
            afterTabs.startsWith(LinePrefixes.CHECKBOX_CHECKED) -> {
                afterTabs.substring(LinePrefixes.CHECKBOX_CHECKED.length) to -LinePrefixes.CHECKBOX_CHECKED.length
            }
            afterTabs.startsWith(LinePrefixes.BULLET) -> {
                LinePrefixes.CHECKBOX_UNCHECKED + afterTabs.substring(LinePrefixes.BULLET.length) to
                    (LinePrefixes.CHECKBOX_UNCHECKED.length - LinePrefixes.BULLET.length)
            }
            else -> {
                LinePrefixes.CHECKBOX_UNCHECKED + afterTabs to LinePrefixes.CHECKBOX_UNCHECKED.length
            }
        }

        text = tabs + newAfterTabs
        cursorPosition = (cursorPosition + cursorDelta).coerceIn(0, text.length)
    }

    /**
     * Toggles checkbox between checked and unchecked states only.
     * Does not remove the checkbox. Only works if line already has a checkbox.
     */
    fun toggleCheckboxState() {
        val tabCount = text.takeWhile { it == '\t' }.length
        val tabs = text.substring(0, tabCount)
        val afterTabs = text.substring(tabCount)

        val newAfterTabs = when {
            afterTabs.startsWith(LinePrefixes.CHECKBOX_UNCHECKED) -> {
                LinePrefixes.CHECKBOX_CHECKED + afterTabs.substring(LinePrefixes.CHECKBOX_UNCHECKED.length)
            }
            afterTabs.startsWith(LinePrefixes.CHECKBOX_CHECKED) -> {
                LinePrefixes.CHECKBOX_UNCHECKED + afterTabs.substring(LinePrefixes.CHECKBOX_CHECKED.length)
            }
            else -> return // Not a checkbox, do nothing
        }

        text = tabs + newAfterTabs
        // Cursor position doesn't change since checkbox length is the same
    }

    companion object {
        /**
         * Extracts the prefix (leading tabs + bullet/checkbox) from a line.
         */
        fun extractPrefix(line: String): String {
            var position = 0

            while (position < line.length && line[position] == '\t') {
                position++
            }

            val afterTabs = if (position < line.length) line.substring(position) else ""
            val prefixEnd = when {
                afterTabs.startsWith(LinePrefixes.BULLET) -> position + LinePrefixes.BULLET.length
                afterTabs.startsWith(LinePrefixes.CHECKBOX_UNCHECKED) -> position + LinePrefixes.CHECKBOX_UNCHECKED.length
                afterTabs.startsWith(LinePrefixes.CHECKBOX_CHECKED) -> position + LinePrefixes.CHECKBOX_CHECKED.length
                else -> position
            }

            return if (prefixEnd > 0) line.substring(0, prefixEnd) else ""
        }
    }
}

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

/**
 * State holder for the entire editor.
 */
class EditorState {
    internal val lines: SnapshotStateList<LineState> = mutableStateListOf(LineState(""))
    var focusedLineIndex by mutableIntStateOf(0)
        internal set

    var selection by mutableStateOf(EditorSelection.None)
        internal set

    internal var onTextChange: ((String) -> Unit)? = null

    /** Timestamp of the last space-triggered indent (for double-space → unindent) */
    private var lastSpaceIndentTime: Long = 0L

    companion object {
        /** Maximum time between space presses for double-space unindent (in milliseconds) */
        const val DOUBLE_SPACE_THRESHOLD_MS = 250L
    }

    /**
     * Version counter that increments on any state change requiring focus update.
     * Observed by the editor composable to trigger focus requests.
     */
    var stateVersion by mutableIntStateOf(0)
        private set

    /**
     * Increments state version to signal that focus should be updated.
     */
    internal fun requestFocusUpdate() {
        stateVersion++
    }

    val text: String get() = lines.joinToString("\n") { it.text }

    val currentLine: LineState? get() = lines.getOrNull(focusedLineIndex)

    val hasSelection: Boolean get() = selection.hasSelection

    /**
     * Gets the character offset where a line starts in the full text.
     */
    fun getLineStartOffset(lineIndex: Int): Int {
        var offset = 0
        for (i in 0 until lineIndex.coerceIn(0, lines.size)) {
            offset += lines[i].text.length + 1
        }
        return offset
    }

    /**
     * Gets the line index and local offset for a global character offset.
     */
    fun getLineAndLocalOffset(globalOffset: Int): Pair<Int, Int> {
        var remaining = globalOffset
        for (i in lines.indices) {
            val lineLength = lines[i].text.length
            if (remaining <= lineLength) {
                return i to remaining
            }
            remaining -= lineLength + 1
        }
        return (lines.lastIndex.coerceAtLeast(0)) to (lines.lastOrNull()?.text?.length ?: 0)
    }

    /**
     * Gets the selection range for a specific line (in local line offsets).
     * Returns null if the line has no selection.
     */
    fun getLineSelection(lineIndex: Int): IntRange? {
        if (!hasSelection || lineIndex !in lines.indices) {
            return null
        }

        val lineStart = getLineStartOffset(lineIndex)
        val lineEnd = lineStart + lines[lineIndex].text.length

        val selMin = selection.min
        val selMax = selection.max

        if (selMax <= lineStart || selMin > lineEnd) {
            return null
        }

        val localStart = (selMin - lineStart).coerceIn(0, lines[lineIndex].text.length)
        val localEnd = (selMax - lineStart).coerceIn(0, lines[lineIndex].text.length)

        if (localStart == localEnd) {
            return null
        }

        return localStart until localEnd
    }

    fun setSelection(start: Int, end: Int) {
        selection = EditorSelection(start, end)

        // Move cursor to the start of the selection (invisibly, since cursor is hidden during selection)
        // This ensures that when selection is cleared, cursor is already at the right position
        val cursorPos = selection.min
        val (lineIndex, localOffset) = getLineAndLocalOffset(cursorPos)
        focusedLineIndex = lineIndex
        lines.getOrNull(lineIndex)?.updateFull(lines[lineIndex].text, localOffset)
    }

    fun clearSelection() {
        selection = EditorSelection.None
    }

    /**
     * Returns the effective selection range, possibly extended to include a trailing newline.
     *
     * When full lines are selected (selection starts at beginning of a line and ends at
     * end of a line), the trailing newline is included. This ensures that:
     * - Copying full lines includes the newline for proper pasting
     * - Deleting full lines removes the newline to avoid empty lines
     *
     * The extension only applies when the FIRST line is fully selected (selection starts
     * at line beginning). Partial selections starting mid-line are not extended.
     */
    private fun getEffectiveSelectionRange(fullText: String): Pair<Int, Int> {
        val selStart = selection.min.coerceIn(0, fullText.length)
        val selEnd = selection.max.coerceIn(0, fullText.length)

        // Check if we should extend to include trailing newline
        val extendedEnd = if (shouldExtendSelectionToNewline(fullText, selStart, selEnd)) {
            selEnd + 1
        } else {
            selEnd
        }

        return selStart to extendedEnd
    }

    /**
     * Checks if a selection should be extended to include the trailing newline.
     *
     * Returns true when:
     * 1. Selection ends right before a newline (at end of line content)
     * 2. Selection starts at the beginning of a line (position 0 or right after a newline)
     */
    private fun shouldExtendSelectionToNewline(fullText: String, selStart: Int, selEnd: Int): Boolean {
        // Must end right before a newline
        if (selEnd >= fullText.length || fullText[selEnd] != '\n') return false

        // First line must be fully selected: selection starts at beginning of a line
        // This means either position 0, or the character before is a newline
        val startsAtLineBeginning = selStart == 0 || fullText[selStart - 1] == '\n'
        return startsAtLineBeginning
    }

    fun getSelectedText(): String {
        if (!hasSelection) return ""
        val fullText = text
        val (selStart, selEnd) = getEffectiveSelectionRange(fullText)
        return fullText.substring(selStart, selEnd)
    }

    /**
     * Internal: Use EditorController.deleteSelectionWithUndo() for proper undo handling.
     */
    internal fun deleteSelectionInternal(): Int {
        if (!hasSelection) return -1

        val fullText = text
        val (selStart, selEnd) = getEffectiveSelectionRange(fullText)
        val newText = fullText.substring(0, selStart) + fullText.substring(selEnd)
        val newCursorPos = selStart

        updateFromText(newText)
        removeEmptyLineAtCursor(newCursorPos)
        clearSelection()

        val (lineIndex, localOffset) = getLineAndLocalOffset(newCursorPos)
        focusedLineIndex = lineIndex
        lines.getOrNull(lineIndex)?.updateFull(lines[lineIndex].text, localOffset)

        requestFocusUpdate()
        notifyChange()
        return newCursorPos
    }

    /**
     * Replaces the current selection with new text.
     * If no selection, inserts at the current cursor position.
     * Returns the new cursor position (at the end of inserted text).
     * Internal: Use EditorController.paste() for proper undo handling.
     */
    internal fun replaceSelectionInternal(replacement: String): Int {
        val fullText = text
        val insertPos: Int
        val newText: String

        if (hasSelection) {
            val (selStart, selEnd) = getEffectiveSelectionRange(fullText)
            insertPos = selStart
            newText = fullText.substring(0, selStart) + replacement + fullText.substring(selEnd)
        } else {
            // Insert at current cursor position
            val currentLine = lines.getOrNull(focusedLineIndex) ?: return 0
            insertPos = getLineStartOffset(focusedLineIndex) + currentLine.cursorPosition
            newText = fullText.substring(0, insertPos) + replacement + fullText.substring(insertPos)
        }

        val newCursorPos = insertPos + replacement.length

        updateFromText(newText)
        removeEmptyLineAtCursor(newCursorPos)
        clearSelection()

        val (lineIndex, localOffset) = getLineAndLocalOffset(newCursorPos)
        focusedLineIndex = lineIndex
        lines.getOrNull(lineIndex)?.updateFull(lines[lineIndex].text, localOffset)

        requestFocusUpdate()
        notifyChange()
        return newCursorPos
    }

    /**
     * Selects all text in the editor.
     */
    fun selectAll() {
        val fullText = text
        if (fullText.isNotEmpty()) {
            setSelection(0, fullText.length)
        }
    }

    /**
     * Handles space key when there's a selection.
     *
     * - Single space: indent all selected lines
     * - Double space (within 250ms): undo the indent and unindent instead
     *
     * Uses the same indent/unindent methods as the command buttons.
     * Called via EditorController which handles undo tracking.
     *
     * @return true if the space was handled (there was a selection), false otherwise
     */
    internal fun handleSpaceWithSelectionInternal(): Boolean {
        if (!hasSelection) return false

        val now = System.currentTimeMillis()
        val timeSinceLastIndent = now - lastSpaceIndentTime

        if (timeSinceLastIndent <= DOUBLE_SPACE_THRESHOLD_MS && lastSpaceIndentTime > 0) {
            // Double-space: undo the indent and unindent
            // First undo the indent we just did by unindenting twice
            unindentInternal()
            unindentInternal()
            lastSpaceIndentTime = 0L // Reset so triple-space doesn't re-trigger
        } else {
            // Single space: indent
            indentInternal()
            lastSpaceIndentTime = now
        }

        return true
    }

    internal fun indentInternal() {
        val linesToIndent = getSelectedLineIndices()
        val hadSelection = hasSelection
        val oldSelStart = selection.start
        val oldSelEnd = selection.end

        linesToIndent.forEach { lineIndex ->
            lines.getOrNull(lineIndex)?.indent()
        }

        // Adjust selection to account for added tabs
        if (hadSelection) {
            val (startLine, _) = getLineAndLocalOffset(oldSelStart)
            val (endLine, _) = getLineAndLocalOffset(oldSelEnd)

            // Count tabs added before/at each selection endpoint
            val tabsBeforeStart = linesToIndent.count { it <= startLine }
            val tabsBeforeEnd = linesToIndent.count { it <= endLine }

            selection = EditorSelection(
                oldSelStart + tabsBeforeStart,
                oldSelEnd + tabsBeforeEnd
            )
        }

        requestFocusUpdate()
        notifyChange()
    }

    internal fun unindentInternal() {
        val linesToUnindent = getSelectedLineIndices()
        val hadSelection = hasSelection
        val oldSelStart = selection.start
        val oldSelEnd = selection.end
        val (startLine, _) = if (hadSelection) getLineAndLocalOffset(oldSelStart) else (0 to 0)
        val (endLine, _) = if (hadSelection) getLineAndLocalOffset(oldSelEnd) else (0 to 0)

        // Track which lines actually unindented
        val unindentedLines = mutableListOf<Int>()
        linesToUnindent.forEach { lineIndex ->
            if (lines.getOrNull(lineIndex)?.unindent() == true) {
                unindentedLines.add(lineIndex)
            }
        }

        if (unindentedLines.isNotEmpty()) {
            // Adjust selection to account for removed tabs
            if (hadSelection) {
                val tabsRemovedBeforeStart = unindentedLines.count { it <= startLine }
                val tabsRemovedBeforeEnd = unindentedLines.count { it <= endLine }

                selection = EditorSelection(
                    (oldSelStart - tabsRemovedBeforeStart).coerceAtLeast(0),
                    (oldSelEnd - tabsRemovedBeforeEnd).coerceAtLeast(0)
                )
            }

            requestFocusUpdate()
            notifyChange()
        }
    }

    /**
     * Gets the indices of all lines that are part of the current selection.
     * If no selection, returns just the focused line index.
     */
    private fun getSelectedLineIndices(): List<Int> {
        if (!hasSelection) {
            return listOf(focusedLineIndex)
        }

        val startLine = getLineAndLocalOffset(selection.min).first
        val endLine = getLineAndLocalOffset(selection.max).first
        return (startLine..endLine).toList()
    }

    internal fun toggleBulletInternal() {
        currentLine?.toggleBullet()
        requestFocusUpdate()
        notifyChange()
    }

    internal fun toggleCheckboxInternal() {
        currentLine?.toggleCheckbox()
        requestFocusUpdate()
        notifyChange()
    }

    // =========================================================================
    // Move Lines Operations
    // =========================================================================

    /**
     * Gets the indentation level of a line (number of leading tabs).
     * Empty lines have indent level 0.
     */
    fun getIndentLevel(lineIndex: Int): Int {
        val text = lines.getOrNull(lineIndex)?.text ?: return 0
        if (text.isEmpty()) return 0
        return text.takeWhile { it == '\t' }.length
    }

    /**
     * Gets the logical block for a line: the line itself plus all deeper-indented children below it.
     */
    fun getLogicalBlock(startIndex: Int): IntRange {
        if (startIndex !in lines.indices) return startIndex..startIndex
        val startIndent = getIndentLevel(startIndex)
        var endIndex = startIndex
        for (i in (startIndex + 1) until lines.size) {
            if (getIndentLevel(i) <= startIndent) break
            endIndex = i
        }
        return startIndex..endIndex
    }

    /**
     * Gets the line indices covered by the current selection.
     * Returns the focused line if there's no selection.
     *
     * If the selection ends exactly at position 0 of a line (e.g., gutter selection
     * that includes the trailing newline), the previous line is used as the end.
     */
    fun getSelectedLineRange(): IntRange {
        if (!hasSelection) {
            return focusedLineIndex..focusedLineIndex
        }
        val startLine = getLineAndLocalOffset(selection.min).first
        val (endLine, endLocal) = getLineAndLocalOffset(selection.max)

        // If selection ends at the very start of a line (offset 0),
        // consider the previous line as the actual end of the range
        val adjustedEndLine = if (endLocal == 0 && endLine > startLine) {
            endLine - 1
        } else {
            endLine
        }

        return startLine..adjustedEndLine
    }

    /**
     * Finds the target position for moving a range up.
     * Returns null if at document boundary.
     */
    fun findMoveUpTarget(lineRange: IntRange): Int? {
        if (lineRange.first <= 0) return null
        val firstIndent = getIndentLevel(lineRange.first)
        var target = lineRange.first - 1

        // Find the head of the previous group at same-or-less indent.
        // Skip over any deeper lines (children of a previous group).
        while (target > 0 && getIndentLevel(target) > firstIndent) {
            target--
        }

        // target is now at the head of the previous group (same-or-less indent)
        return target
    }

    /**
     * Finds the target position for moving a range down.
     * Returns null if at document boundary.
     */
    fun findMoveDownTarget(lineRange: IntRange): Int? {
        if (lineRange.last >= lines.lastIndex) return null
        val firstIndent = getIndentLevel(lineRange.first)
        var target = lineRange.last + 1
        val targetIndent = getIndentLevel(target)

        // Find end of target's logical block (at same-or-less indent)
        if (targetIndent <= firstIndent) {
            // Target is at same/less indent - find end of its block
            while (target < lines.lastIndex && getIndentLevel(target + 1) > targetIndent) {
                target++
            }
        }
        // Target position is after the block
        return target + 1
    }

    /**
     * Gets the move target for the current state.
     * Handles both selection and no-selection cases, including the special case
     * where selected lines span different indent levels.
     */
    fun getMoveTarget(moveUp: Boolean): Int? {
        val range = if (hasSelection) getSelectedLineRange() else getLogicalBlock(focusedLineIndex)

        if (hasSelection) {
            // Check if first selected line is the shallowest
            val shallowest = range.minOfOrNull { getIndentLevel(it) } ?: 0
            val firstIndent = getIndentLevel(range.first)
            if (firstIndent > shallowest) {
                // First line isn't shallowest - move one line only
                return if (moveUp) {
                    if (range.first > 0) range.first - 1 else null
                } else {
                    if (range.last < lines.lastIndex) range.last + 2 else null
                }
            }
        }
        return if (moveUp) findMoveUpTarget(range) else findMoveDownTarget(range)
    }

    /**
     * Checks if moving with the current selection would break parent-child relationships.
     * Returns true when:
     * 1. Selection excludes children of the first selected line (orphaning children below)
     * 2. First selected line isn't the shallowest in selection (selecting children without parent)
     */
    fun wouldOrphanChildren(): Boolean {
        if (!hasSelection) return false
        val selectedRange = getSelectedLineRange()

        // Check if first line isn't the shallowest (selecting children without their context)
        val shallowest = selectedRange.minOfOrNull { getIndentLevel(it) } ?: 0
        val firstIndent = getIndentLevel(selectedRange.first)
        if (firstIndent > shallowest) {
            return true
        }

        // Check if selection excludes children of the first line
        val logicalBlock = getLogicalBlock(selectedRange.first)
        return selectedRange.last < logicalBlock.last
    }

    /**
     * Moves lines from sourceRange to targetIndex.
     * Adjusts focused line and selection to follow the moved lines.
     * Returns the new range of the moved lines, or null if move failed.
     */
    internal fun moveLinesInternal(sourceRange: IntRange, targetIndex: Int): IntRange? {
        // Validate parameters
        if (sourceRange.first < 0 || sourceRange.last >= lines.size) return null
        if (targetIndex < 0 || targetIndex > lines.size) return null
        // No-op if target is within or adjacent to source
        if (targetIndex >= sourceRange.first && targetIndex <= sourceRange.last + 1) return null

        // Track focused line for adjustment
        val oldFocusedLine = focusedLineIndex

        // IMPORTANT: Capture selection info BEFORE modifying lines array
        val hadSelection = hasSelection
        val selStartLine: Int
        val selStartLocal: Int
        val selEndLine: Int
        val selEndLocal: Int
        if (hadSelection) {
            val (sl, slo) = getLineAndLocalOffset(selection.start)
            val (el, elo) = getLineAndLocalOffset(selection.end)
            selStartLine = sl
            selStartLocal = slo
            selEndLine = el
            selEndLocal = elo
        } else {
            selStartLine = -1
            selStartLocal = 0
            selEndLine = -1
            selEndLocal = 0
        }

        // Extract lines to move
        val linesToMove = sourceRange.map { lines[it] }
        val moveCount = linesToMove.size

        // Remove lines (in reverse to maintain indices)
        for (i in sourceRange.last downTo sourceRange.first) {
            lines.removeAt(i)
        }

        // Adjust target index if we removed lines before it
        val adjustedTarget = if (targetIndex > sourceRange.first) {
            targetIndex - moveCount
        } else {
            targetIndex
        }

        // Insert lines at new position
        linesToMove.forEachIndexed { index, line ->
            lines.add(adjustedTarget + index, line)
        }

        // Calculate the new range of moved lines
        val newRange = adjustedTarget until (adjustedTarget + moveCount)

        // Adjust focused line index
        focusedLineIndex = when {
            oldFocusedLine in sourceRange -> {
                // Focus was in moved block - follow it
                val offsetInBlock = oldFocusedLine - sourceRange.first
                adjustedTarget + offsetInBlock
            }
            targetIndex <= oldFocusedLine && sourceRange.first > oldFocusedLine -> {
                // Moved lines inserted before focus, after their original position
                oldFocusedLine + moveCount
            }
            sourceRange.first <= oldFocusedLine && targetIndex > oldFocusedLine -> {
                // Moved lines removed before focus, inserted after
                oldFocusedLine - moveCount
            }
            else -> oldFocusedLine
        }

        // Adjust selection using the line info captured BEFORE the move
        if (hadSelection) {
            val newStartLine = adjustLineIndexForMove(selStartLine, sourceRange, adjustedTarget, moveCount)

            // If selection ends at offset 0 of a line, it conceptually means "end of previous line".
            // We need to adjust the previous line (which may have moved) and then reference the next line.
            val newEndLine: Int
            val newEndLocal: Int
            if (selEndLocal == 0 && selEndLine > 0) {
                val adjustedPrevLine = adjustLineIndexForMove(selEndLine - 1, sourceRange, adjustedTarget, moveCount)
                newEndLine = adjustedPrevLine + 1
                newEndLocal = 0
            } else {
                newEndLine = adjustLineIndexForMove(selEndLine, sourceRange, adjustedTarget, moveCount)
                newEndLocal = selEndLocal
            }

            val newSelStart = getLineStartOffset(newStartLine) +
                selStartLocal.coerceAtMost(lines.getOrNull(newStartLine)?.text?.length ?: 0)
            val newSelEnd = getLineStartOffset(newEndLine) +
                newEndLocal.coerceAtMost(lines.getOrNull(newEndLine)?.text?.length ?: 0)

            selection = EditorSelection(newSelStart, newSelEnd)
        }

        requestFocusUpdate()
        notifyChange()
        return newRange
    }

    /**
     * Helper to adjust a line index after a move operation.
     */
    private fun adjustLineIndexForMove(
        lineIndex: Int,
        sourceRange: IntRange,
        targetIndex: Int,
        moveCount: Int
    ): Int {
        return when {
            lineIndex in sourceRange -> {
                // Line was in moved block - calculate new position
                val offsetInBlock = lineIndex - sourceRange.first
                targetIndex + offsetInBlock
            }
            targetIndex <= lineIndex && sourceRange.first > lineIndex -> {
                // Moved lines inserted before this line, from after it
                lineIndex + moveCount
            }
            sourceRange.first <= lineIndex && targetIndex > lineIndex -> {
                // Moved lines removed before this line, inserted after it
                lineIndex - moveCount
            }
            else -> lineIndex
        }
    }

    /**
     * Inserts text at the end of the current line.
     * Adds a space before the text if the line doesn't end with whitespace.
     * Internal: Use EditorController.insertAtEndOfCurrentLine() for proper undo handling.
     */
    internal fun insertAtEndOfCurrentLineInternal(textToInsert: String) {
        val line = currentLine ?: return
        val lineText = line.text

        // Add space before if line doesn't end with whitespace and isn't empty
        val prefix = if (lineText.isNotEmpty() && !lineText.last().isWhitespace()) " " else ""
        val newLineText = lineText + prefix + textToInsert

        line.updateFull(newLineText, line.cursorPosition)
        notifyChange()
    }

    internal fun updateFromText(newText: String) {
        val newLines = newText.split("\n")
        lines.clear()
        newLines.forEach { lineText ->
            lines.add(LineState(lineText))
        }
        focusedLineIndex = focusedLineIndex.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
    }

    /**
     * Removes the empty line at the cursor position if it exists.
     * This cleans up empty lines created by deletion without affecting
     * pre-existing empty lines elsewhere in the document.
     *
     * @param cursorPosition The global character offset where the cursor is after deletion
     */
    private fun removeEmptyLineAtCursor(cursorPosition: Int) {
        if (lines.size <= 1) return

        val (lineIndex, _) = getLineAndLocalOffset(cursorPosition)

        // Don't remove the last line (keep for typing convenience)
        if (lineIndex >= lines.lastIndex) return

        // Only remove if the line at cursor is empty
        if (lines[lineIndex].text.isEmpty()) {
            lines.removeAt(lineIndex)
        }
    }

    internal fun notifyChange() {
        onTextChange?.invoke(text)
    }
}

/**
 * Creates and remembers an EditorState.
 */
@Composable
fun rememberEditorState(): EditorState {
    return remember { EditorState() }
}

// Backward compatibility alias
typealias HangingIndentEditorState = EditorState

@Composable
fun rememberHangingIndentEditorState(): HangingIndentEditorState = rememberEditorState()
