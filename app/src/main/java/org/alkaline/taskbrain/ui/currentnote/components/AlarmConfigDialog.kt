package org.alkaline.taskbrain.ui.currentnote.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmStage
import org.alkaline.taskbrain.data.AlarmStageType
import org.alkaline.taskbrain.data.AlarmStatus
import org.alkaline.taskbrain.ui.components.DateTimePickerRow
import org.alkaline.taskbrain.ui.components.DialogTitleBar
import org.alkaline.taskbrain.ui.components.ValueChip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val MINUTE_MS = 60 * 1000L
private const val HOUR_MS = 60 * MINUTE_MS

private val OFFSET_PRESETS = listOf(
    0L,
    5 * MINUTE_MS,
    10 * MINUTE_MS,
    15 * MINUTE_MS,
    30 * MINUTE_MS,
    1 * HOUR_MS,
    2 * HOUR_MS,
    3 * HOUR_MS
)

/**
 * Dialog for configuring an alarm's due time, stages, and optional recurrence.
 * For existing alarms, shows status buttons (Done/Cancel/Re-open) above the form,
 * with the form grayed out when the alarm is not pending.
 */
@Composable
fun AlarmConfigDialog(
    lineContent: String,
    existingAlarm: Alarm?,
    existingRecurrenceConfig: RecurrenceConfig? = null,
    onSave: (dueTime: Timestamp?, stages: List<AlarmStage>) -> Unit,
    onSaveRecurring: ((dueTime: Timestamp?, stages: List<AlarmStage>, recurrenceConfig: RecurrenceConfig) -> Unit)? = null,
    onMarkDone: (() -> Unit)? = null,
    onMarkCancelled: (() -> Unit)? = null,
    onReactivate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var dueTime by remember { mutableStateOf(existingAlarm?.dueTime) }
    var stages by remember { mutableStateOf(existingAlarm?.stages ?: Alarm.DEFAULT_STAGES) }
    var recurrenceConfig by remember(existingRecurrenceConfig) {
        mutableStateOf(existingRecurrenceConfig ?: RecurrenceConfig())
    }

    val hasDueTime = dueTime != null
    val isNewAlarm = existingAlarm == null
    val isPending = existingAlarm?.status == AlarmStatus.PENDING
    val formEnabled = isNewAlarm || isPending

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

                // Status buttons for existing alarms (above the form)
                if (existingAlarm != null) {
                    StatusButtons(
                        status = existingAlarm.status,
                        onMarkDone = onMarkDone,
                        onMarkCancelled = onMarkCancelled,
                        onReactivate = onReactivate,
                        onDismiss = onDismiss
                    )
                    HorizontalDivider()
                }

                // Form section — grayed out when not pending
                Column(
                    modifier = Modifier.alpha(if (formEnabled) 1f else 0.4f)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Due time picker
                    DateTimePickerRow(
                        label = stringResource(R.string.alarm_due),
                        value = dueTime,
                        onValueChange = { if (formEnabled) dueTime = it },
                        showDelete = false
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(4.dp))

                    // Stage rows
                    stages.forEachIndexed { index, stage ->
                        StageRow(
                            stage = stage,
                            dueTime = dueTime,
                            enabled = formEnabled,
                            onStageChange = { updated ->
                                stages = stages.toMutableList().also { it[index] = updated }
                            }
                        )
                    }

                    // Recurrence config
                    if (onSaveRecurring != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        RecurrenceConfigSection(
                            config = recurrenceConfig,
                            onConfigChange = { if (formEnabled) recurrenceConfig = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Save/delete buttons
                BottomButtons(
                    existingAlarm = existingAlarm,
                    hasDueTime = hasDueTime,
                    formEnabled = formEnabled,
                    dueTime = dueTime,
                    stages = stages,
                    recurrenceConfig = recurrenceConfig,
                    onSave = onSave,
                    onSaveRecurring = onSaveRecurring,
                    onDelete = onDelete,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

private val StatusButtonFontSize = 12.sp
private val StatusButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)

@Composable
private fun StatusButtons(
    status: AlarmStatus,
    onMarkDone: (() -> Unit)?,
    onMarkCancelled: (() -> Unit)?,
    onReactivate: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = {
                onReactivate?.invoke()
                onDismiss()
            },
            enabled = status != AlarmStatus.PENDING,
            modifier = Modifier.weight(1f),
            contentPadding = StatusButtonPadding
        ) {
            Text(stringResource(R.string.alarm_reopen), fontSize = StatusButtonFontSize)
        }
        OutlinedButton(
            onClick = {
                onMarkCancelled?.invoke()
                onDismiss()
            },
            enabled = status != AlarmStatus.CANCELLED,
            modifier = Modifier.weight(1f),
            contentPadding = StatusButtonPadding
        ) {
            Text(stringResource(R.string.alarm_skipped), fontSize = StatusButtonFontSize)
        }
        Button(
            onClick = {
                onMarkDone?.invoke()
                onDismiss()
            },
            enabled = status != AlarmStatus.DONE,
            modifier = Modifier.weight(1f),
            contentPadding = StatusButtonPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.action_button_background),
                contentColor = colorResource(R.color.action_button_text)
            )
        ) {
            Text(stringResource(R.string.alarm_done), fontSize = StatusButtonFontSize)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageRow(
    stage: AlarmStage,
    dueTime: Timestamp?,
    enabled: Boolean,
    onStageChange: (AlarmStage) -> Unit
) {
    var showOffsetMenu by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val is24Hour = remember { DateFormat.is24HourFormat(context) }

    val stageLabel = stageTypeLabel(stage.type)
    val chipText = formatStageTime(stage, is24Hour)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = stage.enabled,
            onCheckedChange = { if (enabled) onStageChange(stage.copy(enabled = it)) },
            enabled = enabled,
            modifier = Modifier.size(36.dp)
        )

        Text(
            text = stageLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (stage.enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Box {
            ValueChip(
                text = chipText,
                isSet = stage.enabled,
                onClick = { if (enabled) showOffsetMenu = true }
            )

            DropdownMenu(
                expanded = showOffsetMenu,
                onDismissRequest = { showOffsetMenu = false }
            ) {
                OFFSET_PRESETS.forEach { offsetMs ->
                    DropdownMenuItem(
                        text = { Text(formatOffset(offsetMs)) },
                        onClick = {
                            onStageChange(stage.copy(offsetMs = offsetMs, absoluteTime = null))
                            showOffsetMenu = false
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.datetime_select_time)) },
                    onClick = {
                        showOffsetMenu = false
                        showTimePicker = true
                    }
                )
            }
        }
    }

    // Time picker for absolute time
    if (showTimePicker) {
        StageTimePicker(
            stage = stage,
            dueTime = dueTime,
            is24Hour = is24Hour,
            onConfirm = { absoluteTime ->
                onStageChange(stage.copy(absoluteTime = absoluteTime))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageTimePicker(
    stage: AlarmStage,
    dueTime: Timestamp?,
    is24Hour: Boolean,
    onConfirm: (Timestamp) -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = Calendar.getInstance()
    if (stage.absoluteTime != null) {
        calendar.time = stage.absoluteTime.toDate()
    } else if (dueTime != null) {
        calendar.time = java.util.Date(dueTime.toDate().time - stage.offsetMs)
    }

    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = is24Hour
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.datetime_select_time),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )

                TimePicker(state = timePickerState)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            val dateCal = Calendar.getInstance()
                            if (dueTime != null) {
                                dateCal.time = dueTime.toDate()
                            }
                            dateCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            dateCal.set(Calendar.MINUTE, timePickerState.minute)
                            dateCal.set(Calendar.SECOND, 0)
                            dateCal.set(Calendar.MILLISECOND, 0)
                            onConfirm(Timestamp(dateCal.time))
                        }
                    ) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomButtons(
    existingAlarm: Alarm?,
    hasDueTime: Boolean,
    formEnabled: Boolean,
    dueTime: Timestamp?,
    stages: List<AlarmStage>,
    recurrenceConfig: RecurrenceConfig,
    onSave: (dueTime: Timestamp?, stages: List<AlarmStage>) -> Unit,
    onSaveRecurring: ((dueTime: Timestamp?, stages: List<AlarmStage>, recurrenceConfig: RecurrenceConfig) -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        if (existingAlarm != null && onDelete != null) {
            TextButton(
                onClick = {
                    onDelete()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorResource(R.color.destructive_text)
                )
            ) {
                Text(stringResource(R.string.action_delete))
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Button(
            onClick = {
                if (recurrenceConfig.enabled && onSaveRecurring != null) {
                    onSaveRecurring(dueTime, stages, recurrenceConfig)
                } else {
                    onSave(dueTime, stages)
                }
                onDismiss()
            },
            enabled = hasDueTime && formEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.action_button_background),
                contentColor = colorResource(R.color.action_button_text)
            )
        ) {
            Text(stringResource(if (existingAlarm != null) R.string.action_update else R.string.action_create))
        }
    }
}

@Composable
private fun stageTypeLabel(type: AlarmStageType): String = when (type) {
    AlarmStageType.SOUND_ALARM -> stringResource(R.string.alarm_sound)
    AlarmStageType.LOCK_SCREEN -> stringResource(R.string.alarm_urgent)
    AlarmStageType.NOTIFICATION -> stringResource(R.string.alarm_lock_screen)
}

@Composable
private fun formatStageTime(stage: AlarmStage, is24Hour: Boolean): String {
    if (stage.absoluteTime != null) {
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(stage.absoluteTime.toDate())
    }
    return formatOffset(stage.offsetMs)
}

@Composable
private fun formatOffset(offsetMs: Long): String {
    if (offsetMs == 0L) return stringResource(R.string.alarm_at_due_time)
    val totalMinutes = offsetMs / MINUTE_MS
    return if (totalMinutes >= 60 && totalMinutes % 60 == 0L) {
        stringResource(R.string.alarm_hours_before, (totalMinutes / 60).toInt())
    } else {
        stringResource(R.string.alarm_minutes_before, totalMinutes.toInt())
    }
}
