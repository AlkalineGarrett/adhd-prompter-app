package org.alkaline.taskbrain.ui.currentnote

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.alkaline.taskbrain.dsl.ui.ButtonExecutionState
import org.alkaline.taskbrain.ui.currentnote.rendering.ButtonCallbacks
import org.alkaline.taskbrain.ui.currentnote.rendering.DirectiveCallbacks

/**
 * Creates [DirectiveCallbacks] for the note editor, handling directive tap, edit,
 * confirm, cancel, refresh, and view-note interactions.
 */
@Composable
fun rememberDirectiveCallbacks(
    editorState: EditorState,
    controller: EditorController,
    currentNoteViewModel: CurrentNoteViewModel,
    recentTabsViewModel: RecentTabsViewModel,
    inlineEditState: InlineEditState,
    userContent: String,
    onContentChanged: (String) -> Unit,
    onMarkUnsaved: () -> Unit,
): DirectiveCallbacks = remember(editorState, controller, currentNoteViewModel) {
    DirectiveCallbacks(
        onDirectiveTap = { positionKey, sourceText ->
            resolveDirectiveUuid(currentNoteViewModel, positionKey)?.let { uuid ->
                currentNoteViewModel.toggleDirectiveCollapsed(uuid, sourceText)
            }
        },
        onViewDirectiveConfirm = { lineIndex, _, sourceText, newText ->
            handleDirectiveConfirm(
                lineIndex, sourceText, newText,
                editorState, controller, currentNoteViewModel,
                onContentChanged, onMarkUnsaved
            )
        },
        onViewDirectiveCancel = { lineIndex, positionKey, sourceText ->
            resolveDirectiveUuid(currentNoteViewModel, positionKey)?.let { uuid ->
                currentNoteViewModel.toggleDirectiveCollapsed(uuid)
            }
            moveCursorToEndOfDirective(editorState, controller, lineIndex, sourceText)
        },
        onViewDirectiveRefresh = { lineIndex, positionKey, sourceText, newText ->
            handleDirectiveRefresh(
                lineIndex, positionKey, sourceText, newText,
                editorState, controller, currentNoteViewModel, userContent,
                onContentChanged, onMarkUnsaved
            )
        },
        onViewNoteTap = { _, noteId, noteContent ->
            Log.d("CurrentNoteScreen", "onViewNoteTap: saving noteId=$noteId")
            currentNoteViewModel.saveInlineNoteContent(
                noteId = noteId,
                newContent = noteContent,
                onSuccess = {
                    recentTabsViewModel.invalidateCache(noteId)
                    currentNoteViewModel.forceRefreshAllDirectives(userContent) {
                        currentNoteViewModel.endInlineEditSession()
                        inlineEditState.endSession()
                    }
                }
            )
        },
        onViewEditDirective = { directiveKey, sourceText ->
            resolveDirectiveUuid(currentNoteViewModel, directiveKey)?.let { uuid ->
                currentNoteViewModel.toggleDirectiveCollapsed(uuid, sourceText)
            }
        },
    )
}

/**
 * Creates [ButtonCallbacks] for directive button interactions.
 */
@Composable
fun rememberButtonCallbacks(
    currentNoteViewModel: CurrentNoteViewModel,
    executionStates: Map<String, ButtonExecutionState>,
    errors: Map<String, String>,
): ButtonCallbacks = remember(currentNoteViewModel) {
    ButtonCallbacks(
        onClick = { directiveKey, buttonVal, sourceText ->
            currentNoteViewModel.executeButton(directiveKey, buttonVal, sourceText)
        },
        executionStates = executionStates,
        errors = errors,
    )
}

// --- Private helpers ---

private fun resolveDirectiveUuid(
    viewModel: CurrentNoteViewModel,
    positionKey: String
): String? {
    val parts = positionKey.split(":")
    val lineIndex = parts.getOrNull(0)?.toIntOrNull()
    val startOffset = parts.getOrNull(1)?.toIntOrNull()
    return if (lineIndex != null && startOffset != null) {
        viewModel.getDirectiveUuid(lineIndex, startOffset)
    } else null
}

private fun handleDirectiveConfirm(
    lineIndex: Int,
    sourceText: String,
    newText: String,
    editorState: EditorState,
    controller: EditorController,
    viewModel: CurrentNoteViewModel,
    onContentChanged: (String) -> Unit,
    onMarkUnsaved: () -> Unit,
) {
    val lineContent = editorState.lines.getOrNull(lineIndex)?.content ?: ""
    val startOffset = lineContent.indexOf(sourceText)
    val uuid = if (startOffset >= 0) viewModel.getDirectiveUuid(lineIndex, startOffset) else null

    if (sourceText != newText && startOffset >= 0) {
        val endOffset = startOffset + sourceText.length
        controller.confirmDirectiveEdit(lineIndex, startOffset, endOffset, newText)
        onContentChanged(editorState.text)
        onMarkUnsaved()
        viewModel.executeDirectivesLive(editorState.text)

        val cursorPos = startOffset + newText.length
        val prefixLength = editorState.lines.getOrNull(lineIndex)?.prefix?.length ?: 0
        controller.setCursor(lineIndex, prefixLength + cursorPos)

        val newUuid = viewModel.getDirectiveUuid(lineIndex, startOffset)
        if (newUuid != null) {
            viewModel.confirmDirective(newUuid, newText)
        }
    } else if (startOffset >= 0 && uuid != null) {
        moveCursorToEndOfDirective(editorState, controller, lineIndex, sourceText)
        viewModel.confirmDirective(uuid, sourceText)
    }
}

private fun handleDirectiveRefresh(
    lineIndex: Int,
    positionKey: String,
    sourceText: String,
    newText: String,
    editorState: EditorState,
    controller: EditorController,
    viewModel: CurrentNoteViewModel,
    userContent: String,
    onContentChanged: (String) -> Unit,
    onMarkUnsaved: () -> Unit,
) {
    val lineContent = editorState.lines.getOrNull(lineIndex)?.content ?: ""
    val startOffset = lineContent.indexOf(sourceText)

    if (sourceText != newText && startOffset >= 0) {
        val endOffset = startOffset + sourceText.length
        controller.confirmDirectiveEdit(lineIndex, startOffset, endOffset, newText)
        onContentChanged(editorState.text)
        onMarkUnsaved()
        viewModel.executeDirectivesLive(editorState.text)

        val newUuid = viewModel.getDirectiveUuid(lineIndex, startOffset)
        if (newUuid != null) {
            viewModel.refreshDirective(newUuid, newText)
        }
    } else {
        val uuid = resolveDirectiveUuid(viewModel, positionKey)
        if (uuid != null) {
            viewModel.refreshDirective(uuid, sourceText)
        } else {
            viewModel.executeDirectivesLive(userContent)
        }
    }
}

private fun moveCursorToEndOfDirective(
    editorState: EditorState,
    controller: EditorController,
    lineIndex: Int,
    sourceText: String
) {
    val lineContent = editorState.lines.getOrNull(lineIndex)?.content ?: ""
    val startOffset = lineContent.indexOf(sourceText)
    if (startOffset >= 0) {
        val cursorPos = startOffset + sourceText.length
        val prefixLength = editorState.lines.getOrNull(lineIndex)?.prefix?.length ?: 0
        controller.setCursor(lineIndex, prefixLength + cursorPos)
    }
}
