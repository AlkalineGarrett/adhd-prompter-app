package org.alkaline.taskbrain.ui.alarms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.ui.components.DialogTitleBar
import org.alkaline.taskbrain.ui.currentnote.components.RecurrenceConfig
import org.alkaline.taskbrain.ui.currentnote.components.RecurrenceConfigSection

/**
 * Dialog for editing only the recurrence settings of a recurring alarm template.
 * Opened from the recurrence edit button on alarm cards in the alarms list.
 */
@Composable
fun RecurrenceEditDialog(
    lineContent: String,
    initialConfig: RecurrenceConfig,
    onSave: (RecurrenceConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var config by remember(initialConfig) { mutableStateOf(initialConfig) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                DialogTitleBar(title = lineContent, onClose = onDismiss)

                Spacer(modifier = Modifier.height(8.dp))

                RecurrenceConfigSection(
                    config = config,
                    onConfigChange = { config = it },
                    showToggle = false
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                    Button(
                        onClick = {
                            onSave(config)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.action_update))
                    }
                }
            }
        }
    }
}
