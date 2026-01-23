package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.StateFlow

/**
 * Main text editing component for notes.
 * Uses HangingIndentEditor for proper wrapped line indentation.
 *
 * @param editorState State holder for the editor that exposes operations like indent/unindent.
 *        Create with rememberHangingIndentEditorState() and use for CommandBar operations.
 * @param controller The EditorController for managing state modifications.
 *        Create with rememberEditorController(editorState) and use for undo/redo operations.
 */
@Composable
fun NoteTextField(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    editorState: HangingIndentEditorState = rememberHangingIndentEditorState(),
    controller: EditorController,
    isFingerDownFlow: StateFlow<Boolean>? = null,
    onAlarmSymbolTap: ((AlarmSymbolInfo) -> Unit)? = null,
    textColor: Color = Color.Black,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        val textStyle = TextStyle(
            fontSize = EditorConfig.FontSize,
            color = textColor
        )

        HangingIndentEditor(
            text = textFieldValue.text,
            onTextChange = { newText ->
                // Convert string change back to TextFieldValue
                // Place cursor at end of text by default
                onTextFieldValueChange(TextFieldValue(
                    text = newText,
                    selection = TextRange(newText.length)
                ))
            },
            textStyle = textStyle,
            state = editorState,
            controller = controller,
            externalFocusRequester = focusRequester,
            onEditorFocusChanged = onFocusChanged,
            scrollState = scrollState,
            showGutter = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = EditorConfig.HorizontalPadding,
                    vertical = EditorConfig.VerticalPadding
                )
        )
    }
}
