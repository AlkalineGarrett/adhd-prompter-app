package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.alkaline.taskbrain.R

/**
 * A bottom toolbar with buttons for bullet/checkbox toggle, indent/unindent, paste, and alarm.
 */
@Composable
fun CommandBar(
    onToggleBullet: () -> Unit,
    onToggleCheckbox: () -> Unit,
    onIndent: () -> Unit,
    onUnindent: () -> Unit,
    onPaste: (String) -> Unit,
    isPasteEnabled: Boolean,
    onAddAlarm: () -> Unit,
    isAlarmEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Bullet toggle button
        IconButton(
            onClick = onToggleBullet,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_format_list_bulleted),
                contentDescription = "Toggle bullet",
                tint = Color(0xFF616161),
                modifier = Modifier.size(24.dp)
            )
        }

        // Checkbox toggle button
        IconButton(
            onClick = onToggleCheckbox,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check_box_outline),
                contentDescription = "Toggle checkbox",
                tint = Color(0xFF616161),
                modifier = Modifier.size(24.dp)
            )
        }

        // Unindent button
        IconButton(
            onClick = onUnindent,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_format_indent_decrease),
                contentDescription = "Unindent",
                tint = Color(0xFF616161),
                modifier = Modifier.size(24.dp)
            )
        }

        // Indent button
        IconButton(
            onClick = onIndent,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_format_indent_increase),
                contentDescription = "Indent",
                tint = Color(0xFF616161),
                modifier = Modifier.size(24.dp)
            )
        }

        // Paste button
        IconButton(
            onClick = {
                val clipText = clipboardManager.getText()?.text ?: ""
                if (clipText.isNotEmpty()) {
                    onPaste(clipText)
                }
            },
            enabled = isPasteEnabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_paste),
                contentDescription = "Paste",
                tint = if (isPasteEnabled) Color(0xFF616161) else Color(0xFFBDBDBD),
                modifier = Modifier.size(24.dp)
            )
        }

        // Alarm button
        IconButton(
            onClick = onAddAlarm,
            enabled = isAlarmEnabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_alarm),
                contentDescription = "Add alarm",
                tint = if (isAlarmEnabled) Color(0xFF616161) else Color(0xFFBDBDBD),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
