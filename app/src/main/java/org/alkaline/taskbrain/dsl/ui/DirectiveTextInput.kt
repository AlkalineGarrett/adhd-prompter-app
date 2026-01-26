package org.alkaline.taskbrain.dsl.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.TextAttribute
import android.view.inputmethod.TextSnapshot
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A standalone text input component for the directive editor.
 * Uses BasicText + custom IME connection (same pattern as main editor)
 * but without dependency on EditorController.
 *
 * This resolves issues with BasicTextField where wrapped text lines
 * cannot be tapped properly.
 */
@Composable
fun DirectiveTextInput(
    text: String,
    cursorPosition: Int,
    textStyle: TextStyle,
    focusRequester: FocusRequester,
    onTextChange: (newText: String, newCursor: Int) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val hostView = LocalView.current
    val imeState = remember { DirectiveImeState(text, cursorPosition, onTextChange) }

    // Sync external state changes
    LaunchedEffect(text, cursorPosition) {
        if (!imeState.isInBatchEdit) {
            imeState.syncFromExternal(text, cursorPosition)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    var textLayoutResult: TextLayoutResult? by remember { mutableStateOf(null) }
    var isFocused by remember { mutableStateOf(false) }

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
                isFocused = focusState.isFocused
                onFocusChanged(focusState.isFocused)
            }
            .directiveImeConnection(imeState, hostView)
            .focusable(interactionSource = interactionSource)
    ) {
        BasicText(
            text = text,
            style = textStyle,
            onTextLayout = { layout -> textLayoutResult = layout },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(text) {
                    detectTapGestures { tapOffset ->
                        // Request focus first (in case we lost it to the main editor)
                        focusRequester.requestFocus()
                        textLayoutResult?.let { layout ->
                            val newCursor = layout.getOffsetForPosition(tapOffset)
                            onTextChange(text, newCursor)
                        }
                    }
                }
                .drawWithContent {
                    drawContent()
                    if (isFocused && textLayoutResult != null) {
                        val layout = textLayoutResult!!
                        val cursorPos = cursorPosition.coerceIn(0, text.length)
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
// IME State - Standalone version for directive editor
// =============================================================================

/**
 * IME state for directive text input.
 * Manages text buffer and cursor, calling onTextChange when content changes.
 */
class DirectiveImeState(
    initialText: String,
    initialCursor: Int,
    private val onTextChange: (String, Int) -> Unit
) {
    private val buffer = DirectiveEditingBuffer(initialText, initialCursor)

    // Batch edit state
    private var batchDepth: Int = 0
    private var needsSyncAfterBatch: Boolean = false

    // Notification state
    private var notificationCallback: DirectiveImeCallback? = null
    private var extractedTextToken: Int = 0
    private var isMonitoringExtractedText: Boolean = false

    // Public read access
    val text: String get() = buffer.text
    val cursorPosition: Int get() = buffer.cursor
    val composingStart: Int get() = buffer.compositionStart
    val composingEnd: Int get() = buffer.compositionEnd
    val isInBatchEdit: Boolean get() = batchDepth > 0

    fun syncFromExternal(text: String, cursor: Int) {
        buffer.reset(text, cursor)
    }

    fun setNotificationCallback(callback: DirectiveImeCallback?) {
        notificationCallback = callback
    }

    fun startExtractedTextMonitoring(token: Int) {
        extractedTextToken = token
        isMonitoringExtractedText = true
    }

    private fun sendNotification() {
        val callback = notificationCallback ?: return
        callback.notifySelectionUpdate(
            buffer.cursor, buffer.cursor,
            buffer.compositionStart, buffer.compositionEnd
        )
        if (isMonitoringExtractedText) {
            val extracted = ExtractedText().apply {
                text = buffer.text
                startOffset = 0
                selectionStart = buffer.cursor
                selectionEnd = buffer.cursor
            }
            callback.notifyExtractedTextUpdate(extractedTextToken, extracted)
        }
    }

    private fun notifyTextChange() {
        onTextChange(buffer.text, buffer.cursor)
    }

    // Batch edit support
    fun beginBatchEdit(): Boolean {
        batchDepth++
        return true
    }

    fun endBatchEdit(): Boolean {
        if (batchDepth > 0) batchDepth--
        if (batchDepth == 0 && needsSyncAfterBatch) {
            needsSyncAfterBatch = false
            notifyTextChange()
            sendNotification()
        }
        return batchDepth > 0
    }

    private fun applyEdit(edit: () -> Unit) {
        edit()
        if (batchDepth > 0) {
            needsSyncAfterBatch = true
        } else {
            notifyTextChange()
            sendNotification()
        }
    }

    // IME operations
    fun commitText(text: String, newCursorPosition: Int) {
        applyEdit {
            if (buffer.hasComposition()) {
                val start = buffer.compositionStart
                buffer.replace(buffer.compositionStart, buffer.compositionEnd, text)
                buffer.cursor = if (newCursorPosition > 0) {
                    start + text.length + newCursorPosition - 1
                } else {
                    start + newCursorPosition
                }.coerceIn(0, buffer.length)
                buffer.commitComposition()
            } else {
                val start = buffer.selectionStart
                buffer.replace(buffer.selectionStart, buffer.selectionEnd, text)
                buffer.cursor = if (newCursorPosition > 0) {
                    start + text.length + newCursorPosition - 1
                } else {
                    start + newCursorPosition
                }.coerceIn(0, buffer.length)
            }
        }
    }

    fun setComposingText(text: String, newCursorPosition: Int) {
        applyEdit {
            val start = if (buffer.hasComposition()) buffer.compositionStart else buffer.selectionStart
            val end = if (buffer.hasComposition()) buffer.compositionEnd else buffer.selectionEnd
            buffer.replace(start, end, text)
            buffer.setComposition(start, start + text.length)
            buffer.cursor = if (newCursorPosition > 0) {
                start + text.length + newCursorPosition - 1
            } else {
                start + newCursorPosition
            }.coerceIn(0, buffer.length)
        }
    }

    fun finishComposingText() {
        applyEdit { buffer.commitComposition() }
    }

    fun setComposingRegion(start: Int, end: Int) {
        applyEdit {
            buffer.commitComposition()
            if (start != end && start >= 0 && end >= 0) {
                val s = start.coerceIn(0, buffer.length)
                val e = end.coerceIn(0, buffer.length)
                if (s < e) buffer.setComposition(s, e) else buffer.setComposition(e, s)
            }
        }
    }

    fun setSelection(start: Int, end: Int) {
        applyEdit {
            buffer.setSelection(
                start.coerceIn(0, buffer.length),
                end.coerceIn(0, buffer.length)
            )
        }
    }

    fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        applyEdit {
            if (beforeLength == 0 && afterLength == 0) return@applyEdit
            val cursor = buffer.cursor
            val deleteStart = (cursor - beforeLength).coerceAtLeast(0)
            val deleteEnd = (cursor + afterLength).coerceAtMost(buffer.length)
            if (deleteStart < deleteEnd) {
                buffer.delete(deleteStart, deleteEnd)
                buffer.cursor = deleteStart
            }
            buffer.commitComposition()
        }
    }

    fun handleKeyEvent(keyEvent: KeyEvent): Boolean {
        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    deleteSurroundingText(1, 0)
                    return true
                }
                KeyEvent.KEYCODE_FORWARD_DEL -> {
                    deleteSurroundingText(0, 1)
                    return true
                }
            }
        }
        return false
    }
}

// =============================================================================
// Editing Buffer
// =============================================================================

private class DirectiveEditingBuffer(initialText: String = "", initialCursor: Int = 0) {
    private val sb = StringBuilder(initialText)

    var selectionStart: Int = initialCursor.coerceIn(0, sb.length)
        private set
    var selectionEnd: Int = initialCursor.coerceIn(0, sb.length)
        private set
    var compositionStart: Int = -1
        private set
    var compositionEnd: Int = -1
        private set

    val length: Int get() = sb.length
    val text: String get() = sb.toString()

    fun hasComposition(): Boolean = compositionStart >= 0 && compositionEnd >= 0

    var cursor: Int
        get() = selectionStart
        set(value) {
            val c = value.coerceIn(0, sb.length)
            selectionStart = c
            selectionEnd = c
        }

    fun setSelection(start: Int, end: Int) {
        selectionStart = start.coerceIn(0, sb.length)
        selectionEnd = end.coerceIn(0, sb.length)
    }

    fun setComposition(start: Int, end: Int) {
        compositionStart = start.coerceIn(0, sb.length)
        compositionEnd = end.coerceIn(0, sb.length)
    }

    fun commitComposition() {
        compositionStart = -1
        compositionEnd = -1
    }

    fun replace(start: Int, end: Int, text: String) {
        val s = start.coerceIn(0, sb.length)
        val e = end.coerceIn(0, sb.length)
        sb.replace(s, e, text)
        if (hasComposition()) {
            if (compositionEnd <= s) {
                // no change
            } else if (compositionStart >= e) {
                val delta = text.length - (e - s)
                compositionStart += delta
                compositionEnd += delta
            } else {
                commitComposition()
            }
        }
    }

    fun delete(start: Int, end: Int) = replace(start, end, "")

    fun reset(text: String, cursor: Int) {
        sb.clear()
        sb.append(text)
        this.cursor = cursor
        commitComposition()
    }
}

// =============================================================================
// IME Callback
// =============================================================================

interface DirectiveImeCallback {
    fun notifySelectionUpdate(selStart: Int, selEnd: Int, composingStart: Int, composingEnd: Int)
    fun notifyExtractedTextUpdate(token: Int, text: ExtractedText)
}

// =============================================================================
// IME Connection Modifier
// =============================================================================

private data class DirectiveImeConnectionElement(
    val state: DirectiveImeState,
    val hostView: View
) : ModifierNodeElement<DirectiveImeConnectionNode>() {
    override fun create() = DirectiveImeConnectionNode(state, hostView)
    override fun update(node: DirectiveImeConnectionNode) = node.update(state, hostView)
    override fun InspectorInfo.inspectableProperties() { name = "directiveImeConnection" }
}

private class DirectiveImeConnectionNode(
    private var state: DirectiveImeState,
    private var hostView: View
) : DelegatingNode(),
    PlatformTextInputModifierNode,
    androidx.compose.ui.focus.FocusEventModifierNode {

    private var inputSessionJob: Job? = null
    private var isFocused = false
    private var inputMethodManager: InputMethodManager? = null

    private val imeCallback = object : DirectiveImeCallback {
        override fun notifySelectionUpdate(selStart: Int, selEnd: Int, composingStart: Int, composingEnd: Int) {
            inputMethodManager?.updateSelection(hostView, selStart, selEnd, composingStart, composingEnd)
        }
        override fun notifyExtractedTextUpdate(token: Int, text: ExtractedText) {
            inputMethodManager?.updateExtractedText(hostView, token, text)
        }
    }

    fun update(newState: DirectiveImeState, newHostView: View) {
        val stateChanged = state !== newState
        val viewChanged = hostView !== newHostView

        if (stateChanged) state.setNotificationCallback(null)
        state = newState
        hostView = newHostView

        if (viewChanged) {
            inputMethodManager = hostView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        }

        if (stateChanged && isFocused) {
            stopInputSession()
            startInputSession()
        }
    }

    override fun onFocusEvent(focusState: FocusState) {
        val wasFocused = isFocused
        isFocused = focusState.isFocused

        if (isFocused && !wasFocused) {
            startInputSession()
        } else if (!isFocused && wasFocused) {
            stopInputSession()
        }
    }

    private fun startInputSession() {
        if (inputMethodManager == null) {
            inputMethodManager = hostView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        }
        state.setNotificationCallback(imeCallback)

        inputSessionJob?.cancel()
        inputSessionJob = coroutineScope.launch {
            establishTextInputSession {
                val request = PlatformTextInputMethodRequest { outAttrs ->
                    outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE
                    outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    outAttrs.initialSelStart = state.cursorPosition
                    outAttrs.initialSelEnd = state.cursorPosition
                    DirectiveInputConnection(state)
                }
                startInputMethod(request)
            }
        }
    }

    private fun stopInputSession() {
        state.setNotificationCallback(null)
        inputSessionJob?.cancel()
        inputSessionJob = null
    }
}

private fun Modifier.directiveImeConnection(state: DirectiveImeState, hostView: View): Modifier =
    this then DirectiveImeConnectionElement(state, hostView)

// =============================================================================
// Input Connection
// =============================================================================

private class DirectiveInputConnection(private val state: DirectiveImeState) : InputConnection {

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (text != null) state.commitText(text.toString(), newCursorPosition)
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

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        state.setComposingRegion(start, end)
        return true
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        state.setSelection(start, end)
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

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
        val isMonitor = flags and InputConnection.GET_EXTRACTED_TEXT_MONITOR != 0
        if (isMonitor && request != null) {
            state.startExtractedTextMonitoring(request.token)
        }
        return ExtractedText().apply {
            text = state.text
            startOffset = 0
            selectionStart = state.cursorPosition
            selectionEnd = state.cursorPosition
        }
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        return event != null && state.handleKeyEvent(event)
    }

    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true

    override fun replaceText(start: Int, end: Int, text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean {
        state.setComposingRegion(start, end)
        state.commitText(text.toString(), newCursorPosition)
        return true
    }

    override fun beginBatchEdit(): Boolean = state.beginBatchEdit()
    override fun endBatchEdit(): Boolean = state.endBatchEdit()
    override fun performEditorAction(editorAction: Int): Boolean = true
    override fun performContextMenuAction(id: Int): Boolean = false
    override fun clearMetaKeyStates(states: Int): Boolean = false
    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = false
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false
    override fun getHandler(): Handler? = null
    override fun closeConnection() {}
    override fun commitCompletion(text: CompletionInfo?): Boolean = true
    override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false
    override fun reportFullscreenMode(enabled: Boolean): Boolean = false
    override fun takeSnapshot(): TextSnapshot? = null
    override fun performSpellCheck(): Boolean = false
    override fun setImeConsumesInput(imeConsumesInput: Boolean): Boolean = false
}
