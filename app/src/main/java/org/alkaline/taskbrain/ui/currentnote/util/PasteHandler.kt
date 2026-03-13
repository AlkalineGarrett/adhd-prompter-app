package org.alkaline.taskbrain.ui.currentnote.util

import org.alkaline.taskbrain.ui.currentnote.LineState
import org.alkaline.taskbrain.ui.currentnote.selection.EditorSelection
import org.alkaline.taskbrain.ui.currentnote.selection.SelectionCoordinates

/**
 * Result of a structured paste operation.
 */
data class PasteResult(
    val lines: List<LineState>,
    val cursorLineIndex: Int,
    val cursorPosition: Int,   // position within the cursor line's full text
)

/**
 * Implements the 5 paste rules from docs/paste-requirements.md:
 *
 * 1. Source prefix wins; adopt destination prefix if source has none
 * 2. Mid-line multi-line paste splits the target line
 * 3. Full-line selection paste is clean replacement
 * 4. Relative indent shifting
 * 5. Single-line plain text paste is simple insertion
 */
object PasteHandler {

    fun execute(
        lines: List<LineState>,
        focusedLineIndex: Int,
        selection: EditorSelection,
        parsed: List<ParsedLine>,
    ): PasteResult {
        if (parsed.isEmpty()) {
            return PasteResult(lines, focusedLineIndex, 0)
        }

        // Rule 5: single-line paste with no prefix = simple text insertion
        if (parsed.size == 1 && !parsed[0].hasPrefix) {
            return applySingleLinePaste(lines, focusedLineIndex, selection, parsed[0].content)
        }

        // Rule 3: full-line selection = clean replacement
        if (selection.hasSelection && isFullLineSelection(lines, selection)) {
            return applyFullLineReplace(lines, selection, parsed)
        }

        // Rules 1, 2, 4: multi-line or prefixed paste
        return applyStructuredPaste(lines, focusedLineIndex, selection, parsed)
    }

    // --- Full-line detection ---

    fun isFullLineSelection(lines: List<LineState>, selection: EditorSelection): Boolean {
        if (!selection.hasSelection) return false
        val sMin = selection.min
        val sMax = selection.max

        val (startLine, _) = SelectionCoordinates.getLineAndLocalOffset(lines, sMin)
        val startOfLine = SelectionCoordinates.getLineStartOffset(lines, startLine)
        if (sMin != startOfLine) return false

        val (endLine, endLocal) = SelectionCoordinates.getLineAndLocalOffset(lines, sMax)
        val endLineLen = lines.getOrNull(endLine)?.text?.length ?: 0
        return endLocal == endLineLen || (endLocal == 0 && endLine > startLine)
    }

    // --- Rule 5: Simple single-line text insertion ---

    private fun applySingleLinePaste(
        lines: List<LineState>,
        focusedLineIndex: Int,
        selection: EditorSelection,
        text: String,
    ): PasteResult {
        if (selection.hasSelection) {
            val fullText = lines.joinToString("\n") { it.text }
            val sMin = selection.min.coerceIn(0, fullText.length)
            val sMax = selection.max.coerceIn(0, fullText.length)
            val newText = fullText.substring(0, sMin) + text + fullText.substring(sMax)
            val cursorPos = sMin + text.length
            val rebuilt = newText.split('\n').map { LineState(it) }
            val (lineIdx, localOff) = SelectionCoordinates.getLineAndLocalOffset(rebuilt, cursorPos)
            rebuilt.getOrNull(lineIdx)?.updateFull(rebuilt[lineIdx].text, localOff)
            return PasteResult(rebuilt, lineIdx, localOff)
        }

        val newLines = lines.map { LineState(it.text, it.cursorPosition) }
        val line = newLines.getOrNull(focusedLineIndex)
            ?: return PasteResult(newLines, focusedLineIndex, 0)
        val cursor = line.cursorPosition
        val newText = line.text.substring(0, cursor) + text + line.text.substring(cursor)
        val newCursor = cursor + text.length
        line.updateFull(newText, newCursor)
        return PasteResult(newLines, focusedLineIndex, newCursor)
    }

    // --- Rule 3: Full-line replacement ---

    private fun applyFullLineReplace(
        lines: List<LineState>,
        selection: EditorSelection,
        parsed: List<ParsedLine>,
    ): PasteResult {
        val sMin = selection.min
        val sMax = selection.max

        val (startLine, _) = SelectionCoordinates.getLineAndLocalOffset(lines, sMin)
        var (endLine, endLocal) = SelectionCoordinates.getLineAndLocalOffset(lines, sMax)
        if (endLocal == 0 && endLine > startLine) endLine--

        val before = lines.subList(0, startLine)
        val after = lines.subList(endLine + 1, lines.size)
        val pastedLines = parsed.map { LineState(it.toLineText()) }

        val newLines = before + pastedLines + after
        val cursorLineIndex = before.size + pastedLines.size - 1
        val cursorPosition = newLines.getOrNull(cursorLineIndex)?.text?.length ?: 0

        return PasteResult(newLines, cursorLineIndex, cursorPosition)
    }

    // --- Rules 1, 2, 4: Structured paste ---

    private fun applyStructuredPaste(
        lines: List<LineState>,
        focusedLineIndex: Int,
        selection: EditorSelection,
        parsed: List<ParsedLine>,
    ): PasteResult {
        val destLineIndex: Int
        val splitOffset: Int
        val trailingText: String
        val trailingPrefix: String
        val beforeLines: List<LineState>
        val afterLines: List<LineState>

        if (selection.hasSelection) {
            val fullText = lines.joinToString("\n") { it.text }
            val sMin = selection.min.coerceIn(0, fullText.length)
            val sMax = selection.max.coerceIn(0, fullText.length)

            val (startLine, startLocal) = SelectionCoordinates.getLineAndLocalOffset(lines, sMin)
            val (endLine, endLocal) = SelectionCoordinates.getLineAndLocalOffset(lines, sMax)

            destLineIndex = startLine
            splitOffset = startLocal
            trailingText = lines[endLine].text.substring(endLocal)
            trailingPrefix = LineState.extractPrefix(lines[endLine].text)
            beforeLines = lines.subList(0, startLine)
            afterLines = lines.subList(endLine + 1, lines.size)
        } else {
            destLineIndex = focusedLineIndex
            val line = lines[destLineIndex]
            splitOffset = line.cursorPosition
            trailingText = line.text.substring(splitOffset)
            trailingPrefix = LineState.extractPrefix(line.text)
            beforeLines = lines.subList(0, destLineIndex)
            afterLines = lines.subList(destLineIndex + 1, lines.size)
        }

        val destLine = lines[destLineIndex]
        val destPrefix = LineState.extractPrefix(destLine.text)
        val destIndent = destPrefix.takeWhile { it == '\t' }.length
        val destBullet = destPrefix.drop(destIndent)
        val leadingText = destLine.text.substring(0, splitOffset)
        val leadingContent = leadingText.drop(LineState.extractPrefix(leadingText).length)

        // Rule 4: compute indent delta
        val firstPastedIndent = parsed[0].indent
        val indentDelta = destIndent - firstPastedIndent

        // Rule 1: prefix merging + Rule 4: indent shifting
        val pastedLines = parsed.map { p ->
            val newIndent = maxOf(0, p.indent + indentDelta)
            val bullet = p.bullet.ifEmpty { destBullet }  // source wins, adopt dest if absent
            LineState(ParsedLine(newIndent, bullet, p.content).toLineText())
        }

        // Rule 2: build leading half, pasted lines, trailing half
        val result = mutableListOf<LineState>()
        result.addAll(beforeLines)

        val hasLeadingContent = leadingContent.isNotEmpty()
        if (hasLeadingContent) {
            result.add(LineState(leadingText))
        }

        result.addAll(pastedLines)

        val trailingContent = trailingText.drop(LineState.extractPrefix(trailingText).length)
        val hasTrailingContent = trailingContent.isNotEmpty()
        if (hasTrailingContent) {
            result.add(LineState(trailingPrefix + trailingContent))
        }

        result.addAll(afterLines)

        // Cursor at end of last pasted line
        val lastPastedIndex = (if (hasLeadingContent) beforeLines.size + 1 else beforeLines.size) + pastedLines.size - 1
        val cursorPosition = result.getOrNull(lastPastedIndex)?.text?.length ?: 0

        return PasteResult(result, lastPastedIndex, cursorPosition)
    }
}
