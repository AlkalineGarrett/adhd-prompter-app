package org.alkaline.taskbrain.ui.alarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Transparent activity that shows an error dialog.
 * Used to display errors from BroadcastReceivers where dialogs can't be shown directly.
 */
class AlarmErrorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Error"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "An unknown error occurred"

        setContent {
            MaterialTheme {
                Surface {
                    ErrorDialog(
                        title = title,
                        message = message,
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"

        /**
         * Creates an intent to show an error dialog.
         */
        fun createIntent(context: Context, title: String, message: String): Intent {
            return Intent(context, AlarmErrorActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
        }

        /**
         * Shows an error dialog from any context.
         */
        fun show(context: Context, title: String, message: String) {
            context.startActivity(createIntent(context, title, message))
        }
    }
}

@Composable
private fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = message,
                modifier = Modifier.padding(top = 8.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
