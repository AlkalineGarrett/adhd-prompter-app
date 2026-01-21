package org.alkaline.taskbrain.ui.currentnote

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A text input component for a single line that uses EditorController for state management.
 *
 * This component:
 * - Displays text from the controller (via content parameter)
 * - Forwards all IME input to the controller
 * - Does NOT maintain its own text state
 *
 * This ensures there's only ONE source of truth (EditorState via EditorController).
 */
@Composable
internal fun LineTextInput(
    lineIndex: Int,
    content: String,
    contentCursor: Int,
    controller: EditorController,
    isFocused: Boolean,
    hasExternalSelection: Boolean,
    textStyle: TextStyle,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onTextLayoutResult: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier
) {
    // Create LineImeState that delegates to controller
    val imeState = remember(lineIndex, controller) {
        LineImeState(lineIndex, controller)
    }

    // Sync ImeState cache whenever content/cursor changes
    LaunchedEffect(content, contentCursor) {
        imeState.syncFromController()
    }

    val interactionSource = remember { MutableInteractionSource() }
    var textLayoutResultState: TextLayoutResult? by remember { mutableStateOf(null) }

    // Cursor blink animation
    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocusChanged(focusState.isFocused)
            }
            .lineImeConnection(imeState)
            .focusable(interactionSource = interactionSource)
    ) {
        BasicText(
            text = content,
            style = textStyle,
            onTextLayout = { layoutResult ->
                textLayoutResultState = layoutResult
                onTextLayoutResult(layoutResult)
            },
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()

                    // Draw blinking cursor when focused and no external selection
                    if (isFocused && !hasExternalSelection && textLayoutResultState != null) {
                        val layout = textLayoutResultState!!
                        val cursorPos = contentCursor.coerceIn(0, content.length)

                        val cursorRect = try {
                            layout.getCursorRect(cursorPos)
                        } catch (e: Exception) {
                            Rect(0f, 0f, 2.dp.toPx(), layout.size.height.toFloat())
                        }

                        drawLine(
                            color = Color.Black.copy(alpha = cursorAlpha),
                            start = Offset(cursorRect.left, cursorRect.top),
                            end = Offset(cursorRect.left, cursorRect.bottom),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
        )
    }
}

// =============================================================================
// IME Connection Modifier
// =============================================================================

/**
 * Modifier element that creates IME connection for LineImeState.
 */
private data class LineImeConnectionElement(
    val state: LineImeState
) : ModifierNodeElement<LineImeConnectionNode>() {

    override fun create(): LineImeConnectionNode = LineImeConnectionNode(state)

    override fun update(node: LineImeConnectionNode) {
        node.update(state)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "lineImeConnection"
    }
}

/**
 * The actual modifier node that implements IME connection for LineImeState.
 */
private class LineImeConnectionNode(
    private var state: LineImeState
) : DelegatingNode(),
    PlatformTextInputModifierNode,
    androidx.compose.ui.focus.FocusEventModifierNode {

    private var inputSessionJob: Job? = null
    private var isFocused = false

    private var lastSyncedCursor: Int = -1

    fun update(newState: LineImeState) {
        val needsRestart = state !== newState
        state = newState

        if (needsRestart && isFocused) {
            stopInputSession()
            startInputSession()
        } else if (isFocused) {
            // Check if cursor moved - need to restart session so IME knows new position
            state.syncFromController()
            if (state.cursorPosition != lastSyncedCursor) {
                lastSyncedCursor = state.cursorPosition
                stopInputSession()
                startInputSession()
            }
        }
    }

    override fun onFocusEvent(focusState: FocusState) {
        val wasFocused = isFocused
        isFocused = focusState.isFocused

        if (isFocused && !wasFocused) {
            state.syncFromController()
            lastSyncedCursor = state.cursorPosition
            startInputSession()
        } else if (!isFocused && wasFocused) {
            stopInputSession()
            lastSyncedCursor = -1
        }
    }

    private fun startInputSession() {
        inputSessionJob?.cancel()
        inputSessionJob = coroutineScope.launch {
            establishTextInputSession {
                val request = PlatformTextInputMethodRequest { outAttrs ->
                    configureEditorInfo(outAttrs)
                    LineInputConnection(state)
                }
                startInputMethod(request)
            }
        }
    }

    private fun configureEditorInfo(outAttrs: EditorInfo) {
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        outAttrs.initialSelStart = state.cursorPosition
        outAttrs.initialSelEnd = state.cursorPosition
    }

    private fun stopInputSession() {
        inputSessionJob?.cancel()
        inputSessionJob = null
    }
}

/**
 * Modifier that connects a line to the soft keyboard.
 */
private fun Modifier.lineImeConnection(state: LineImeState): Modifier =
    this then LineImeConnectionElement(state)

// =============================================================================
// Input Connection
// =============================================================================

/**
 * InputConnection implementation that forwards all edits to LineImeState.
 */
private class LineInputConnection(
    private val state: LineImeState
) : InputConnection {

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (text != null) {
            state.commitText(text.toString(), newCursorPosition)
        }
        return true
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        state.setComposingText(text?.toString() ?: "", newCursorPosition)
        return true
    }

    override fun finishComposingText(): Boolean {
        state.finishComposingText()
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        state.deleteSurroundingText(beforeLength, afterLength)
        return true
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        state.deleteSurroundingText(beforeLength, afterLength)
        return true
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        state.setSelection(start, end)
        return true
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        val start = (state.cursorPosition - n).coerceAtLeast(0)
        return state.text.substring(start, state.cursorPosition.coerceIn(start, state.text.length))
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
        val cursor = state.cursorPosition.coerceIn(0, state.text.length)
        val end = (cursor + n).coerceAtMost(state.text.length)
        return state.text.substring(cursor, end)
    }

    override fun getSelectedText(flags: Int): CharSequence? = null

    override fun getCursorCapsMode(reqModes: Int): Int {
        return android.text.TextUtils.getCapsMode(state.text, state.cursorPosition, reqModes)
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        return event != null && state.handleKeyEvent(event)
    }

    override fun performEditorAction(editorAction: Int): Boolean = true

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        state.setComposingRegion(start, end)
        return true
    }

    override fun getExtractedText(
        request: android.view.inputmethod.ExtractedTextRequest?,
        flags: Int
    ): android.view.inputmethod.ExtractedText? {
        val extracted = android.view.inputmethod.ExtractedText()
        extracted.text = state.text
        extracted.startOffset = 0
        extracted.selectionStart = state.cursorPosition
        extracted.selectionEnd = state.cursorPosition
        return extracted
    }

    override fun beginBatchEdit(): Boolean = true
    override fun endBatchEdit(): Boolean = true
    override fun clearMetaKeyStates(states: Int): Boolean = false

    override fun performContextMenuAction(id: Int): Boolean {
        // Handle paste from keyboard context menu
        return when (id) {
            android.R.id.paste, android.R.id.pasteAsPlainText -> {
                // Paste is handled by commitText when the system pastes
                // Return true to indicate we support it
                true
            }
            android.R.id.copy, android.R.id.cut -> {
                // These require selection which we handle elsewhere
                true
            }
            android.R.id.selectAll -> {
                // Select all - could implement if needed
                false
            }
            else -> false
        }
    }

    override fun performPrivateCommand(action: String?, data: android.os.Bundle?): Boolean = false
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false
    override fun getHandler(): android.os.Handler? = null

    override fun closeConnection() {
        // Don't clear composing state here - it should persist across session restarts.
        // The composing region is only cleared when:
        // 1. finishComposingText() is explicitly called by the IME
        // 2. commitText() successfully processes a composing region
        // Clearing here causes issues with spelling corrections where setComposingRegion
        // is called, then a session restart clears it, then commitText inserts instead of replaces.
    }

    override fun commitCorrection(correctionInfo: android.view.inputmethod.CorrectionInfo?): Boolean {
        if (correctionInfo != null) {
            // The IME is telling us to replace oldText with newText at the given offset
            val oldText = correctionInfo.oldText.toString()
            val newText = correctionInfo.newText.toString()
            val offset = correctionInfo.offset

            // Set composing region to cover the old text, then commit the new text
            state.setComposingRegion(offset, offset + oldText.length)
            state.commitText(newText, 1)
        }
        return true
    }
    override fun commitCompletion(text: android.view.inputmethod.CompletionInfo?): Boolean = true

    override fun commitContent(
        inputContentInfo: android.view.inputmethod.InputContentInfo,
        flags: Int,
        opts: android.os.Bundle?
    ): Boolean = false

    override fun reportFullscreenMode(enabled: Boolean): Boolean = false
    override fun takeSnapshot(): android.view.inputmethod.TextSnapshot? = null

    override fun replaceText(
        start: Int,
        end: Int,
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: android.view.inputmethod.TextAttribute?
    ): Boolean {
        // Set the composing region to the range being replaced, then commit
        // This ensures commitText properly replaces the specified range
        state.setComposingRegion(start, end)
        state.commitText(text.toString(), newCursorPosition)
        return true
    }

    override fun performSpellCheck(): Boolean = false
    override fun setImeConsumesInput(imeConsumesInput: Boolean): Boolean = false
}
