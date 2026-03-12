package org.alkaline.taskbrain.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.alkaline.taskbrain.R
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Error dialog that shows a simple message without stack trace.
 */
@Composable
fun ErrorDialog(
    title: String? = null,
    message: String,
    onDismiss: () -> Unit
) {
    val resolvedTitle = title ?: stringResource(R.string.error_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = resolvedTitle)
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_dismiss))
            }
        }
    )
}

/**
 * Error dialog that shows a Throwable with full stack trace.
 */
@Composable
fun ErrorDialog(
    title: String? = null,
    throwable: Throwable,
    onDismiss: () -> Unit
) {
    val resolvedTitle = title ?: stringResource(R.string.error_title)
    val stackTrace = StringWriter().also { sw ->
        throwable.printStackTrace(PrintWriter(sw))
    }.toString()
    val unknownError = stringResource(R.string.error_unknown)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = resolvedTitle)
        },
        text = {
            SelectionContainer {
                Column {
                    Text(
                        text = throwable.message ?: unknownError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stackTrace,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_dismiss))
            }
        }
    )
}
