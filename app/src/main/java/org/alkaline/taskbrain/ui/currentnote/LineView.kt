package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Measures the rendered width of a prefix string.
 * Returns both the Dp value for layout and the pixel value for coordinate calculations.
 */
@Composable
internal fun measurePrefixWidth(prefix: String, textStyle: TextStyle): Pair<Dp, Float> {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    if (prefix.isEmpty()) {
        return 0.dp to 0f
    }

    val widthPx = remember(prefix, textStyle) {
        val result = textMeasurer.measure(prefix, textStyle)
        result.size.width.toFloat()
    }

    return with(density) { widthPx.toDp() } to widthPx
}

// =============================================================================
// Controller-based LineView (New Architecture)
// =============================================================================

/**
 * Renders a single line using EditorController for all state management.
 *
 * This is the NEW version that uses centralized state management:
 * - All text modifications go through EditorController
 * - No callback chains for text changes
 * - Single source of truth
 */
@Composable
internal fun ControlledLineView(
    lineIndex: Int,
    lineState: LineState,
    controller: EditorController,
    textStyle: TextStyle,
    focusRequester: FocusRequester,
    selectionRange: IntRange?,
    selectionIncludesNewline: Boolean = false,
    onFocusChanged: (Boolean) -> Unit,
    onTextLayoutResult: (TextLayoutResult) -> Unit,
    onPrefixWidthMeasured: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val prefix = lineState.prefix
    val content = lineState.content
    val (prefixWidth, prefixWidthPx) = measurePrefixWidth(prefix, textStyle)
    val contentCursorPosition = lineState.contentCursorPosition
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Report prefix width in pixels for handle positioning
    LaunchedEffect(prefixWidthPx) {
        onPrefixWidthMeasured(prefixWidthPx)
    }

    // Measure space width for newline visualization
    val spaceWidth = remember(textStyle) {
        textMeasurer.measure(" ", textStyle).size.width.toFloat()
    }

    // Convert line selection to content and prefix selection ranges
    val contentSelectionRange = selectionRange?.let {
        lineSelectionToContentSelection(it, prefix.length, content.length)
    }
    val prefixSelectionRange = selectionRange?.let {
        lineSelectionToPrefixSelection(it, prefix.length)
    }

    // Hide cursor whenever there's ANY selection in the editor (not just on this line)
    val hasExternalSelection = controller.hasSelection()

    // Track content TextLayoutResult for drawing selection
    var contentTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Track focus state
    var isFocused by remember { mutableStateOf(false) }

    // Check if prefix contains a checkbox (for tap handling)
    val hasCheckbox = remember(prefix) {
        LinePrefixes.hasCheckbox(prefix)
    }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Prefix area - tappable if contains checkbox
        if (prefix.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .width(prefixWidth)
                    .then(
                        if (hasCheckbox) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = null // No ripple effect
                            ) {
                                controller.toggleCheckboxOnLine(lineIndex)
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (prefixSelectionRange != null) {
                    PrefixSelectionOverlay(
                        prefix = prefix,
                        selectionRange = prefixSelectionRange,
                        textStyle = textStyle
                    )
                }
                BasicText(
                    text = prefix,
                    style = textStyle
                )
            }
        }

        // Content area - using LineTextInput with EditorController
        Box(modifier = Modifier.weight(1f)) {
            // Selection overlay behind text (including newline visualization)
            if ((contentSelectionRange != null || selectionIncludesNewline) && contentTextLayout != null) {
                ContentSelectionOverlay(
                    selectionRange = contentSelectionRange,
                    contentLength = content.length,
                    textLayout = contentTextLayout!!,
                    density = density,
                    includesNewline = selectionIncludesNewline,
                    spaceWidth = spaceWidth
                )
            }

            LineTextInput(
                lineIndex = lineIndex,
                content = content,
                contentCursor = contentCursorPosition,
                controller = controller,
                isFocused = isFocused,
                hasExternalSelection = hasExternalSelection,
                textStyle = textStyle,
                focusRequester = focusRequester,
                onFocusChanged = { focused ->
                    isFocused = focused
                    onFocusChanged(focused)
                },
                onTextLayoutResult = { layout ->
                    contentTextLayout = layout
                    onTextLayoutResult(layout)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// =============================================================================
// Legacy LineView (Callback-based - for backwards compatibility)
// =============================================================================

/**
 * Renders a single line with hanging indent and optional selection highlight.
 * The prefix is rendered with fixed width, content fills remaining space and wraps within it.
 * Uses ManualTextInput instead of BasicTextField to avoid built-in gesture handling.
 */
@Composable
internal fun LineView(
    lineState: LineState,
    textStyle: TextStyle,
    focusRequester: FocusRequester,
    selectionRange: IntRange?,
    selectionIncludesNewline: Boolean = false,
    onContentChange: (String, Int) -> Unit,
    onEnterPressed: (Int) -> Unit,
    onBackspaceAtStart: () -> Unit,
    onDeleteAtEnd: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSelectionCleared: () -> Unit,
    onTextLayoutResult: (TextLayoutResult) -> Unit,
    onPrefixWidthMeasured: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val prefix = lineState.prefix
    val content = lineState.content
    val (prefixWidth, prefixWidthPx) = measurePrefixWidth(prefix, textStyle)
    val contentCursorPosition = lineState.contentCursorPosition
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Report prefix width in pixels for handle positioning
    androidx.compose.runtime.LaunchedEffect(prefixWidthPx) {
        onPrefixWidthMeasured(prefixWidthPx)
    }

    // Measure space width for newline visualization
    val spaceWidth = remember(textStyle) {
        textMeasurer.measure(" ", textStyle).size.width.toFloat()
    }

    // Convert line selection to content and prefix selection ranges
    val contentSelectionRange = selectionRange?.let {
        lineSelectionToContentSelection(it, prefix.length, content.length)
    }
    val prefixSelectionRange = selectionRange?.let {
        lineSelectionToPrefixSelection(it, prefix.length)
    }

    val hasExternalSelection = selectionRange != null

    // Track content TextLayoutResult for drawing selection
    var contentTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Track focus state
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Prefix area
        if (prefix.isNotEmpty()) {
            Box(modifier = Modifier.width(prefixWidth)) {
                if (prefixSelectionRange != null) {
                    PrefixSelectionOverlay(
                        prefix = prefix,
                        selectionRange = prefixSelectionRange,
                        textStyle = textStyle
                    )
                }
                BasicText(
                    text = prefix,
                    style = textStyle
                )
            }
        }

        // Content area - using ManualTextInput for complete control
        Box(modifier = Modifier.weight(1f)) {
            // Selection overlay behind text (including newline visualization)
            if ((contentSelectionRange != null || selectionIncludesNewline) && contentTextLayout != null) {
                ContentSelectionOverlay(
                    selectionRange = contentSelectionRange,
                    contentLength = content.length,
                    textLayout = contentTextLayout!!,
                    density = density,
                    includesNewline = selectionIncludesNewline,
                    spaceWidth = spaceWidth
                )
            }

            ManualTextInput(
                text = content,
                cursorPosition = contentCursorPosition,
                isFocused = isFocused,
                hasExternalSelection = hasExternalSelection,
                textStyle = textStyle,
                focusRequester = focusRequester,
                onBackspaceAtStart = {
                    // Always delegate to the outer handler which has access to state
                    // It will handle both prefix removal and line merging
                    android.util.Log.d("LineView", "onBackspaceAtStart: delegating to outer handler")
                    onBackspaceAtStart()
                },
                onTextChange = { newText, newCursor ->
                    // Note: Don't call onSelectionCleared() here - let onContentChange handle
                    // selection replacement/deletion so typing replaces selection properly

                    // Check for newline (Enter key)
                    if (newText.contains('\n')) {
                        val newlineIndex = newText.indexOf('\n')
                        onEnterPressed(prefix.length + newlineIndex)
                    } else {
                        // Normal content change - backspace at start is handled via onBackspaceAtStart callback
                        onContentChange(newText, newCursor)
                    }
                },
                onFocusChanged = { focused ->
                    isFocused = focused
                    onFocusChanged(focused)
                },
                onTextLayoutResult = { layout ->
                    contentTextLayout = layout
                    onTextLayoutResult(layout)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
