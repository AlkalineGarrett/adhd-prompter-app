package org.alkaline.taskbrain.ui.currentnote.ime

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Custom IME integration - mined from BasicTextField internals.
 *
 * This implementation was built by studying the AndroidX source code:
 * - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/BasicTextField.kt
 * - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/input/internal/TextFieldDecoratorModifier.kt
 * - https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/input/internal/StatelessInputConnection.android.kt
 *
 * WHY WE DON'T USE BasicTextField:
 * 1. Single-field selection model - BasicTextField's selection is confined to one field.
 *    Our editor needs multi-line selection spanning multiple fields (one per line).
 *    This is a fundamental architectural mismatch.
 *
 * 2. Undisableable UI behaviors - Magnifier bubble, selection handles, and gesture
 *    handling cannot be fully disabled, even with NoOpTextToolbar, transparent
 *    TextSelectionColors, and consuming pointer events.
 *
 * This custom implementation uses PlatformTextInputModifierNode directly to connect
 * to the soft keyboard without any of the UI baggage.
 *
 * DO NOT REPLACE THIS WITH BasicTextField. If you're tempted to use BasicTextField,
 * read docs/compose-ime-learnings.md first.
 */

/**
 * State holder for IME connection that manages text and cursor position.
 */
class ImeState(
    initialText: String = "",
    initialCursor: Int = 0
) {
    var text by mutableStateOf(initialText)
        private set

    var cursorPosition by mutableStateOf(initialCursor.coerceIn(0, initialText.length))
        private set

    var composingStart by mutableStateOf(-1)
        private set

    var composingEnd by mutableStateOf(-1)
        private set

    private var onTextChangeCallback: ((String, Int) -> Unit)? = null
    private var onBackspaceAtStartCallback: (() -> Unit)? = null

    fun setOnTextChangeListener(callback: (String, Int) -> Unit) {
        onTextChangeCallback = callback
    }

    fun setOnBackspaceAtStartListener(callback: () -> Unit) {
        onBackspaceAtStartCallback = callback
    }

    /**
     * Update from external source (e.g., parent recomposition).
     * Does not trigger callback.
     */
    fun updateFromExternal(newText: String, newCursor: Int) {
        text = newText
        cursorPosition = newCursor.coerceIn(0, newText.length)
        // Clear composition when external update happens
        composingStart = -1
        composingEnd = -1
    }

    /**
     * Called when IME commits text (user finished typing a word).
     */
    fun commitText(commitText: String, newCursorOffset: Int) {
        // First, remove any composing text
        val baseText = if (composingStart >= 0 && composingEnd >= 0) {
            text.substring(0, composingStart.coerceIn(0, text.length)) +
                text.substring(composingEnd.coerceIn(0, text.length))
        } else {
            text
        }
        val baseCursor = if (composingStart >= 0) composingStart else cursorPosition

        val newText = buildString {
            append(baseText.substring(0, baseCursor.coerceIn(0, baseText.length)))
            append(commitText)
            append(baseText.substring(baseCursor.coerceIn(0, baseText.length)))
        }
        val newCursor = baseCursor + commitText.length + newCursorOffset - 1

        text = newText
        cursorPosition = newCursor.coerceIn(0, newText.length)
        composingStart = -1
        composingEnd = -1

        onTextChangeCallback?.invoke(text, cursorPosition)
    }

    /**
     * Called when IME sets composing text (autocomplete suggestion).
     */
    fun setComposingText(composingText: String, newCursorOffset: Int) {
        // If there's existing composition, replace it
        val start = if (composingStart >= 0) composingStart else cursorPosition
        val end = if (composingEnd >= 0) composingEnd else cursorPosition

        val newText = buildString {
            append(text.substring(0, start.coerceIn(0, text.length)))
            append(composingText)
            append(text.substring(end.coerceIn(0, text.length)))
        }

        text = newText
        composingStart = start
        composingEnd = start + composingText.length
        cursorPosition = (composingEnd + newCursorOffset - 1).coerceIn(0, newText.length)

        onTextChangeCallback?.invoke(text, cursorPosition)
    }

    /**
     * Called when IME finishes composition.
     */
    fun finishComposingText() {
        composingStart = -1
        composingEnd = -1
    }

    /**
     * Called when IME deletes text around cursor.
     */
    fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        if (beforeLength == 0 && afterLength == 0) return

        // Check if backspace at start with nothing to delete
        if (beforeLength > 0 && cursorPosition == 0 && text.isEmpty()) {
            onBackspaceAtStartCallback?.invoke()
            return
        }

        val deleteStart = (cursorPosition - beforeLength).coerceAtLeast(0)
        val deleteEnd = (cursorPosition + afterLength).coerceAtMost(text.length)

        // Check if backspace would go past the start (nothing before cursor to delete)
        if (beforeLength > 0 && cursorPosition == 0) {
            onBackspaceAtStartCallback?.invoke()
            return
        }

        val newText = text.removeRange(deleteStart, deleteEnd)
        text = newText
        cursorPosition = deleteStart.coerceIn(0, newText.length)
        composingStart = -1
        composingEnd = -1

        onTextChangeCallback?.invoke(text, cursorPosition)
    }

    /**
     * Called when IME sets selection/cursor.
     */
    fun setSelection(start: Int, end: Int) {
        // For our single-line case, we only care about cursor position (collapsed selection)
        cursorPosition = start.coerceIn(0, text.length)
    }

    /**
     * Handle hardware key events (physical keyboard).
     */
    fun handleKeyEvent(keyEvent: KeyEvent): Boolean {
        // Handle backspace from hardware keyboard
        if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_DEL) {
            // Always call deleteSurroundingText - it handles cursor at 0 by invoking callback
            deleteSurroundingText(1, 0)
            return true
        }
        // Handle delete (forward delete)
        if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            if (cursorPosition < text.length) {
                deleteSurroundingText(0, 1)
            }
            return true
        }
        return false
    }
}

/**
 * Modifier element that creates our IME connection node.
 */
private data class ImeConnectionElement(
    val state: ImeState,
    val imeOptions: ImeOptions,
    val onImeAction: ((ImeAction) -> Unit)?
) : ModifierNodeElement<ImeConnectionNode>() {

    override fun create(): ImeConnectionNode = ImeConnectionNode(state, imeOptions, onImeAction)

    override fun update(node: ImeConnectionNode) {
        node.update(state, imeOptions, onImeAction)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "imeConnection"
        properties["imeOptions"] = imeOptions
    }
}

/**
 * The actual modifier node that implements IME connection.
 *
 * This implements PlatformTextInputModifierNode to connect to the soft keyboard
 * without any of BasicTextField's gesture/selection/magnifier baggage.
 */
private class ImeConnectionNode(
    private var state: ImeState,
    private var imeOptions: ImeOptions,
    private var onImeAction: ((ImeAction) -> Unit)?
) : DelegatingNode(),
    PlatformTextInputModifierNode,
    FocusEventModifierNode {

    private var inputSessionJob: Job? = null
    private var isFocused = false

    fun update(newState: ImeState, newImeOptions: ImeOptions, newOnImeAction: ((ImeAction) -> Unit)?) {
        val needsRestart = state !== newState || imeOptions != newImeOptions
        state = newState
        imeOptions = newImeOptions
        onImeAction = newOnImeAction

        // Restart session if options changed while focused
        if (needsRestart && isFocused) {
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
        inputSessionJob?.cancel()
        inputSessionJob = coroutineScope.launch {
            establishTextInputSession {
                // Create the request that will provide InputConnection
                val request = PlatformTextInputMethodRequest { outAttrs ->
                    configureEditorInfo(outAttrs)
                    SimpleInputConnection(state, onImeAction ?: {})
                }

                // This suspends until the session is cancelled
                startInputMethod(request)
            }
        }
    }

    private fun configureEditorInfo(outAttrs: EditorInfo) {
        outAttrs.imeOptions = when (imeOptions.imeAction) {
            ImeAction.Default -> EditorInfo.IME_ACTION_UNSPECIFIED
            ImeAction.None -> EditorInfo.IME_ACTION_NONE
            ImeAction.Go -> EditorInfo.IME_ACTION_GO
            ImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
            ImeAction.Send -> EditorInfo.IME_ACTION_SEND
            ImeAction.Previous -> EditorInfo.IME_ACTION_PREVIOUS
            ImeAction.Next -> EditorInfo.IME_ACTION_NEXT
            ImeAction.Done -> EditorInfo.IME_ACTION_DONE
            else -> EditorInfo.IME_ACTION_UNSPECIFIED
        }

        outAttrs.inputType = when (imeOptions.keyboardType) {
            KeyboardType.Text -> android.text.InputType.TYPE_CLASS_TEXT
            KeyboardType.Number -> android.text.InputType.TYPE_CLASS_NUMBER
            KeyboardType.Phone -> android.text.InputType.TYPE_CLASS_PHONE
            KeyboardType.Email -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            KeyboardType.Password -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            KeyboardType.Uri -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            else -> android.text.InputType.TYPE_CLASS_TEXT
        }

        // Enable multiline
        outAttrs.inputType = outAttrs.inputType or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE

        // Set capitalization
        when (imeOptions.capitalization) {
            KeyboardCapitalization.Sentences ->
                outAttrs.inputType = outAttrs.inputType or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            KeyboardCapitalization.Words ->
                outAttrs.inputType = outAttrs.inputType or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            KeyboardCapitalization.Characters ->
                outAttrs.inputType = outAttrs.inputType or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            else -> {}
        }

        // Initial text state
        outAttrs.initialSelStart = state.cursorPosition
        outAttrs.initialSelEnd = state.cursorPosition
    }

    private fun stopInputSession() {
        inputSessionJob?.cancel()
        inputSessionJob = null
    }
}

/**
 * Simple InputConnection implementation that forwards all edits to ImeState.
 *
 * This is the key to receiving keyboard input without BasicTextField.
 * We implement the InputConnection interface directly.
 */
private class SimpleInputConnection(
    private val state: ImeState,
    private val onImeAction: (ImeAction) -> Unit
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
        // For simplicity, treat code points same as characters (correct for BMP)
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

    override fun getSelectedText(flags: Int): CharSequence? {
        // We don't support selection, just cursor
        return null
    }

    override fun getCursorCapsMode(reqModes: Int): Int {
        return android.text.TextUtils.getCapsMode(state.text, state.cursorPosition, reqModes)
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && state.handleKeyEvent(event)) {
            return true
        }
        return false
    }

    override fun performEditorAction(editorAction: Int): Boolean {
        val action = when (editorAction) {
            EditorInfo.IME_ACTION_GO -> ImeAction.Go
            EditorInfo.IME_ACTION_SEARCH -> ImeAction.Search
            EditorInfo.IME_ACTION_SEND -> ImeAction.Send
            EditorInfo.IME_ACTION_PREVIOUS -> ImeAction.Previous
            EditorInfo.IME_ACTION_NEXT -> ImeAction.Next
            EditorInfo.IME_ACTION_DONE -> ImeAction.Done
            else -> ImeAction.Default
        }
        onImeAction(action)
        return true
    }

    // Required methods with sensible defaults

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        // We handle composition through setComposingText
        return true
    }

    override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int): android.view.inputmethod.ExtractedText? {
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

    override fun performContextMenuAction(id: Int): Boolean = false

    override fun performPrivateCommand(action: String?, data: android.os.Bundle?): Boolean = false

    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false

    override fun getHandler(): android.os.Handler? = null

    override fun closeConnection() {
        state.finishComposingText()
    }

    override fun commitCorrection(correctionInfo: android.view.inputmethod.CorrectionInfo?): Boolean = true

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
        // Replace the specified range
        val newText = buildString {
            append(state.text.substring(0, start.coerceIn(0, state.text.length)))
            append(text)
            append(state.text.substring(end.coerceIn(0, state.text.length)))
        }
        val newCursor = start + text.length + newCursorPosition - 1
        state.updateFromExternal(newText, newCursor.coerceIn(0, newText.length))
        return true
    }

    override fun performSpellCheck(): Boolean = false

    override fun setImeConsumesInput(imeConsumesInput: Boolean): Boolean = false
}

/**
 * Modifier that connects a composable to the soft keyboard (IME) without using BasicTextField.
 *
 * This is the CORRECT way to get keyboard input in this codebase.
 * DO NOT use BasicTextField - see docs/compose-ime-learnings.md for why.
 *
 * Usage:
 * ```kotlin
 * val imeState = remember { ImeState() }
 *
 * Box(
 *     modifier = Modifier
 *         .focusRequester(focusRequester)
 *         .focusable()
 *         .imeConnection(
 *             state = imeState,
 *             imeOptions = ImeOptions.Default,
 *             onImeAction = { action -> }
 *         )
 * )
 * ```
 */
fun Modifier.imeConnection(
    state: ImeState,
    imeOptions: ImeOptions = ImeOptions.Default,
    onImeAction: ((ImeAction) -> Unit)? = null
): Modifier = this then ImeConnectionElement(state, imeOptions, onImeAction)
