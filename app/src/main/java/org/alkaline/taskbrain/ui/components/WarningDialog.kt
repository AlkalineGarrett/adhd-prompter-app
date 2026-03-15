package org.alkaline.taskbrain.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.alkaline.taskbrain.R

/**
 * Simple warning dialog with a title, message, and single OK button.
 * Optionally makes the message text selectable (useful for error details).
 */
@Composable
fun WarningDialog(
    title: String,
    message: String,
    selectable: Boolean = false,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (selectable) {
                SelectionContainer { Text(message) }
            } else {
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        }
    )
}
