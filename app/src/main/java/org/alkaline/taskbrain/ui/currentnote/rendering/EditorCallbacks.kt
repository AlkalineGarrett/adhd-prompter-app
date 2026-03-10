package org.alkaline.taskbrain.ui.currentnote.rendering

import org.alkaline.taskbrain.dsl.runtime.values.ButtonVal
import org.alkaline.taskbrain.dsl.ui.ButtonExecutionState

/**
 * Callbacks for directive interactions (tap, edit, view, refresh).
 * Bundled to reduce parameter sprawl across composable layers.
 */
data class DirectiveCallbacks(
    val onDirectiveTap: ((directiveKey: String, sourceText: String) -> Unit)? = null,
    val onViewNoteTap: ((directiveKey: String, noteId: String, noteContent: String) -> Unit)? = null,
    val onViewEditDirective: ((directiveKey: String, sourceText: String) -> Unit)? = null,
    val onViewDirectiveRefresh: ((lineIndex: Int, directiveKey: String, sourceText: String, newText: String) -> Unit)? = null,
    val onViewDirectiveConfirm: ((lineIndex: Int, directiveKey: String, sourceText: String, newText: String) -> Unit)? = null,
    val onViewDirectiveCancel: ((lineIndex: Int, directiveKey: String, sourceText: String) -> Unit)? = null,
)

/**
 * State and callbacks for button directives.
 * Bundled to reduce parameter sprawl across composable layers.
 */
data class ButtonCallbacks(
    val onClick: ((directiveKey: String, buttonVal: ButtonVal, sourceText: String) -> Unit)? = null,
    val executionStates: Map<String, ButtonExecutionState> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
)
