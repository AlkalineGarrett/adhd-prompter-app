package org.alkaline.taskbrain.ui.currentnote.components

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
import android.content.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.ui.currentnote.MoveButtonState

// Color constants for button states
private val EnabledTint = Color(0xFF616161)   // Dark gray
private val DisabledTint = Color(0xFFBDBDBD)  // Light gray
private val WarningTint = Color(0xFFFF9800)   // Orange

/**
 * A bottom toolbar with buttons for bullet/checkbox toggle, indent/unindent, move lines, paste, and alarm.
 */
@Composable
fun CommandBar(
    onToggleBullet: () -> Unit,
    onToggleCheckbox: () -> Unit,
    onIndent: () -> Unit,
    onUnindent: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    moveUpState: MoveButtonState,
    moveDownState: MoveButtonState,
    onPaste: (plainText: String, html: String?) -> Unit,
    isPasteEnabled: Boolean,
    onAddAlarm: () -> Unit,
    isAlarmEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

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
                contentDescription = stringResource(R.string.command_toggle_bullet),
                tint = EnabledTint,
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
                contentDescription = stringResource(R.string.command_toggle_checkbox),
                tint = EnabledTint,
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
                contentDescription = stringResource(R.string.command_unindent),
                tint = EnabledTint,
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
                contentDescription = stringResource(R.string.command_indent),
                tint = EnabledTint,
                modifier = Modifier.size(24.dp)
            )
        }

        // Move up button
        IconButton(
            onClick = onMoveUp,
            enabled = moveUpState.isEnabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_up),
                contentDescription = stringResource(R.string.command_move_up),
                tint = when {
                    !moveUpState.isEnabled -> DisabledTint
                    moveUpState.isWarning -> WarningTint
                    else -> EnabledTint
                },
                modifier = Modifier.size(24.dp)
            )
        }

        // Move down button
        IconButton(
            onClick = onMoveDown,
            enabled = moveDownState.isEnabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_down),
                contentDescription = stringResource(R.string.command_move_down),
                tint = when {
                    !moveDownState.isEnabled -> DisabledTint
                    moveDownState.isWarning -> WarningTint
                    else -> EnabledTint
                },
                modifier = Modifier.size(24.dp)
            )
        }

        // Paste button
        IconButton(
            onClick = {
                val clipText = clipboardManager.getText()?.text ?: ""
                if (clipText.isNotEmpty()) {
                    val nativeClip = (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                        ?.primaryClip?.getItemAt(0)
                    val htmlText = nativeClip?.htmlText
                    onPaste(clipText, htmlText)
                }
            },
            enabled = isPasteEnabled,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_paste),
                contentDescription = stringResource(R.string.action_paste),
                tint = if (isPasteEnabled) EnabledTint else DisabledTint,
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
                contentDescription = stringResource(R.string.command_add_alarm),
                tint = if (isAlarmEnabled) EnabledTint else DisabledTint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
