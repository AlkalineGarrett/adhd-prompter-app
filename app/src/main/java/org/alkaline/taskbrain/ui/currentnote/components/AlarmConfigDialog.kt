package org.alkaline.taskbrain.ui.currentnote.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.data.Alarm
import org.alkaline.taskbrain.data.AlarmStage
import org.alkaline.taskbrain.data.AlarmStageType
import org.alkaline.taskbrain.data.TimeOfDay
import org.alkaline.taskbrain.data.AlarmStatus
import org.alkaline.taskbrain.data.RecurringAlarm
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

enum class AlarmDialogMode { INSTANCE, RECURRENCE }

/**
 * Encapsulates the save-related state for [BottomButtons] to avoid parameter sprawl.
 */
private data class SaveState(
    val mode: AlarmDialogMode,
    val isRecurring: Boolean,
    val instanceDueTime: Timestamp?,
    val instanceStages: List<AlarmStage>,
    val recurrenceDueTime: Timestamp?,
    val recurrenceStages: List<AlarmStage>,
    val recurrenceConfig: RecurrenceConfig,
    val alsoUpdateRecurrence: Boolean,
    val alsoUpdateInstances: Boolean,
    val recurringAlarm: RecurringAlarm?
)

/**
 * Dialog for configuring an alarm's due time, stages, and optional recurrence.
 *
 * For recurring alarms, supports two modes:
 * - INSTANCE: edit this alarm instance's times (optionally propagate to recurrence template)
 * - RECURRENCE: edit the recurrence template's times/pattern (optionally propagate to matching instances)
 */
@Composable
fun AlarmConfigDialog(
    lineContent: String,
    existingAlarm: Alarm?,
    existingRecurrenceConfig: RecurrenceConfig? = null,
    recurringAlarm: RecurringAlarm? = null,
    recurringInstanceCount: Int = 0,
    initialMode: AlarmDialogMode = AlarmDialogMode.INSTANCE,
    onSave: (dueTime: Timestamp?, stages: List<AlarmStage>) -> Unit,
    onSaveInstance: ((alarm: Alarm, dueTime: Timestamp?, stages: List<AlarmStage>, alsoUpdateRecurrence: Boolean) -> Unit)? = null,
    onSaveRecurrenceTemplate: ((recurringAlarmId: String, dueTime: Timestamp?, stages: List<AlarmStage>, recurrenceConfig: RecurrenceConfig, alsoUpdateMatchingInstances: Boolean) -> Unit)? = null,
    onSaveRecurring: ((dueTime: Timestamp?, stages: List<AlarmStage>, recurrenceConfig: RecurrenceConfig) -> Unit)? = null,
    onEndRecurrence: (() -> Unit)? = null,
    onMarkDone: (() -> Unit)? = null,
    onMarkCancelled: (() -> Unit)? = null,
    onReactivate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onNavigatePrevious: (() -> Unit)? = null,
    onNavigateNext: (() -> Unit)? = null,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    onDismiss: () -> Unit
) {
    val alarmKey = existingAlarm?.id
    val isRecurring = recurringAlarm != null
    val timesMatchPreEdit = remember(alarmKey, recurringAlarm?.id) {
        recurringAlarm != null && existingAlarm != null &&
                recurringAlarm.timesMatchInstance(existingAlarm)
    }

    // Mode toggle state (only relevant for recurring alarms)
    var mode by remember(alarmKey) { mutableStateOf(initialMode) }

    // Instance state
    var instanceDueTime by remember(alarmKey) { mutableStateOf(existingAlarm?.dueTime) }
    var instanceStages by remember(alarmKey) {
        mutableStateOf(existingAlarm?.stages ?: Alarm.DEFAULT_STAGES)
    }

    // Recurrence state — derive from template's anchor or fall back to instance.
    // Keyed on recurringAlarm so these re-initialize when the template loads asynchronously.
    var recurrenceDueTime by remember(alarmKey, recurringAlarm?.id) {
        mutableStateOf(
            recurringAlarm?.anchorTimeOfDay?.let { anchor ->
                // Reconstruct a Timestamp from the anchor time on today's date
                existingAlarm?.dueTime?.let { instanceDue ->
                    anchor.onSameDateAs(instanceDue.toDate())
                }
            } ?: existingAlarm?.dueTime
        )
    }
    var recurrenceStages by remember(alarmKey, recurringAlarm?.id) {
        mutableStateOf(recurringAlarm?.stages ?: existingAlarm?.stages ?: Alarm.DEFAULT_STAGES)
    }
    var recurrenceConfig by remember(alarmKey, existingRecurrenceConfig) {
        mutableStateOf(existingRecurrenceConfig ?: RecurrenceConfig())
    }

    // Checkbox state — keyed on recurringAlarm so defaults re-evaluate when template loads
    var alsoUpdateRecurrence by remember(alarmKey, recurringAlarm?.id) { mutableStateOf(timesMatchPreEdit) }
    var alsoUpdateInstances by remember(alarmKey, recurringAlarm?.id) { mutableStateOf(true) }

    // Active due time / stages depend on mode
    val activeDueTime = if (mode == AlarmDialogMode.RECURRENCE) recurrenceDueTime else instanceDueTime
    val activeStages = if (mode == AlarmDialogMode.RECURRENCE) recurrenceStages else instanceStages

    val hasDueTime = activeDueTime != null
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

                // Status buttons for existing alarms
                if (existingAlarm != null) {
                    StatusButtons(
                        status = existingAlarm.status,
                        onMarkDone = onMarkDone,
                        onMarkCancelled = onMarkCancelled,
                        onReactivate = onReactivate,
                        onNavigatePrevious = onNavigatePrevious,
                        onNavigateNext = onNavigateNext,
                        hasPrevious = hasPrevious,
                        hasNext = hasNext,
                        onDismiss = onDismiss
                    )
                    HorizontalDivider()
                }

                // Mode toggle for recurring alarms
                if (isRecurring && onSaveInstance != null && onSaveRecurrenceTemplate != null) {
                    ModeToggle(
                        mode = mode,
                        onModeChange = { mode = it },
                        enabled = formEnabled
                    )
                }

                // Form section — grayed out when not pending
                Column(
                    modifier = Modifier.alpha(if (formEnabled) 1f else 0.4f)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Due time picker
                    DateTimePickerRow(
                        label = stringResource(R.string.alarm_due),
                        value = activeDueTime,
                        onValueChange = { newTime ->
                            if (!formEnabled) return@DateTimePickerRow
                            if (mode == AlarmDialogMode.RECURRENCE) {
                                recurrenceDueTime = newTime
                            } else {
                                instanceDueTime = newTime
                            }
                        },
                        showDelete = false
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(4.dp))

                    // Stage rows
                    activeStages.forEachIndexed { index, stage ->
                        StageRow(
                            stage = stage,
                            dueTime = activeDueTime,
                            enabled = formEnabled,
                            onStageChange = { updated ->
                                if (mode == AlarmDialogMode.RECURRENCE) {
                                    recurrenceStages = recurrenceStages.toMutableList().also { it[index] = updated }
                                } else {
                                    instanceStages = instanceStages.toMutableList().also { it[index] = updated }
                                }
                            }
                        )
                    }

                    // Cross-propagation checkbox
                    if (isRecurring && onSaveInstance != null && onSaveRecurrenceTemplate != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        if (mode == AlarmDialogMode.INSTANCE) {
                            CrossPropagationCheckbox(
                                checked = alsoUpdateRecurrence,
                                onCheckedChange = { alsoUpdateRecurrence = it },
                                label = stringResource(R.string.alarm_also_update_recurrence),
                                enabled = formEnabled
                            )
                        } else {
                            CrossPropagationCheckbox(
                                checked = alsoUpdateInstances,
                                onCheckedChange = { alsoUpdateInstances = it },
                                label = stringResource(R.string.alarm_also_update_next),
                                enabled = formEnabled
                            )
                        }
                    }

                    // Recurrence config
                    if (mode == AlarmDialogMode.RECURRENCE && isRecurring) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        RecurrenceConfigSection(
                            config = recurrenceConfig,
                            onConfigChange = { if (formEnabled) recurrenceConfig = it },
                            showToggle = false
                        )
                        if (onEndRecurrence != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            EndRecurrenceButton(onEndRecurrence, onDismiss)
                        }
                    } else if (!isRecurring && onSaveRecurring != null) {
                        // Non-recurring alarm: show recurrence toggle to enable recurrence
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        RecurrenceConfigSection(
                            config = recurrenceConfig,
                            onConfigChange = { if (formEnabled) recurrenceConfig = it }
                        )
                    } else if (isRecurring && mode == AlarmDialogMode.INSTANCE) {
                        // Instance mode for recurring — show recurrence config read-only style
                        val showRecurrenceToggle = recurringInstanceCount <= 1
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        if (showRecurrenceToggle) {
                            RecurrenceConfigSection(
                                config = recurrenceConfig,
                                onConfigChange = { if (formEnabled) recurrenceConfig = it }
                            )
                        } else {
                            RecurrenceConfigSection(
                                config = recurrenceConfig,
                                onConfigChange = { if (formEnabled) recurrenceConfig = it },
                                showToggle = false
                            )
                            if (onEndRecurrence != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                EndRecurrenceButton(onEndRecurrence, onDismiss)
                            }
                        }
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
                    saveState = SaveState(
                        mode = mode,
                        isRecurring = isRecurring,
                        instanceDueTime = instanceDueTime,
                        instanceStages = instanceStages,
                        recurrenceDueTime = recurrenceDueTime,
                        recurrenceStages = recurrenceStages,
                        recurrenceConfig = recurrenceConfig,
                        alsoUpdateRecurrence = alsoUpdateRecurrence,
                        alsoUpdateInstances = alsoUpdateInstances,
                        recurringAlarm = recurringAlarm
                    ),
                    onSave = onSave,
                    onSaveInstance = onSaveInstance,
                    onSaveRecurrenceTemplate = onSaveRecurrenceTemplate,
                    onSaveRecurring = onSaveRecurring,
                    onEndRecurrence = onEndRecurrence,
                    onDelete = onDelete,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun ModeToggle(
    mode: AlarmDialogMode,
    onModeChange: (AlarmDialogMode) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeButton(AlarmDialogMode.INSTANCE, mode, R.string.alarm_mode_instance, onModeChange, enabled)
        ModeButton(AlarmDialogMode.RECURRENCE, mode, R.string.alarm_mode_recurrence, onModeChange, enabled)
    }
}

@Composable
private fun RowScope.ModeButton(
    modeValue: AlarmDialogMode,
    currentMode: AlarmDialogMode,
    @StringRes labelRes: Int,
    onModeChange: (AlarmDialogMode) -> Unit,
    enabled: Boolean
) {
    val isSelected = currentMode == modeValue
    OutlinedButton(
        onClick = { if (enabled) onModeChange(modeValue) },
        modifier = Modifier.weight(1f),
        colors = if (isSelected) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(stringResource(labelRes), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun CrossPropagationCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

private val StatusButtonFontSize = 12.sp
private val StatusButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
private val NavButtonFontSize = 12.sp
private val NavButtonPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
private val NavButtonWidth = 32.dp

@Composable
private fun StatusButtons(
    status: AlarmStatus,
    onMarkDone: (() -> Unit)?,
    onMarkCancelled: (() -> Unit)?,
    onReactivate: (() -> Unit)?,
    onNavigatePrevious: (() -> Unit)?,
    onNavigateNext: (() -> Unit)?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onDismiss: () -> Unit
) {
    val showNavigation = onNavigatePrevious != null || onNavigateNext != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showNavigation) {
            OutlinedButton(
                onClick = { onNavigatePrevious?.invoke() },
                enabled = hasPrevious,
                modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp).width(NavButtonWidth),
                contentPadding = NavButtonPadding
            ) {
                Text("<", fontSize = NavButtonFontSize)
            }
        }
        OutlinedButton(
            onClick = {
                onReactivate?.invoke()
                onDismiss()
            },
            enabled = status != AlarmStatus.PENDING,
            modifier = Modifier.weight(1f),
            contentPadding = StatusButtonPadding
        ) {
            Text(stringResource(R.string.alarm_reopen), fontSize = StatusButtonFontSize, maxLines = 1)
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
            Text(stringResource(R.string.alarm_skipped), fontSize = StatusButtonFontSize, maxLines = 1)
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
            Text(stringResource(R.string.alarm_done), fontSize = StatusButtonFontSize, maxLines = 1)
        }
        if (showNavigation) {
            OutlinedButton(
                onClick = { onNavigateNext?.invoke() },
                enabled = hasNext,
                modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp).width(NavButtonWidth),
                contentPadding = NavButtonPadding
            ) {
                Text(">", fontSize = NavButtonFontSize)
            }
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
                            onStageChange(stage.copy(offsetMs = offsetMs, absoluteTimeOfDay = null))
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
            onConfirm = { timeOfDay ->
                onStageChange(stage.copy(absoluteTimeOfDay = timeOfDay))
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
    onConfirm: (TimeOfDay) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHour: Int
    val initialMinute: Int
    if (stage.absoluteTimeOfDay != null) {
        initialHour = stage.absoluteTimeOfDay.hour
        initialMinute = stage.absoluteTimeOfDay.minute
    } else {
        val calendar = Calendar.getInstance()
        if (dueTime != null) {
            calendar.time = java.util.Date(dueTime.toDate().time - stage.offsetMs)
        }
        initialHour = calendar.get(Calendar.HOUR_OF_DAY)
        initialMinute = calendar.get(Calendar.MINUTE)
    }

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
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
                            onConfirm(TimeOfDay(timePickerState.hour, timePickerState.minute))
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
    saveState: SaveState,
    onSave: (dueTime: Timestamp?, stages: List<AlarmStage>) -> Unit,
    onSaveInstance: ((alarm: Alarm, dueTime: Timestamp?, stages: List<AlarmStage>, alsoUpdateRecurrence: Boolean) -> Unit)?,
    onSaveRecurrenceTemplate: ((recurringAlarmId: String, dueTime: Timestamp?, stages: List<AlarmStage>, recurrenceConfig: RecurrenceConfig, alsoUpdateMatchingInstances: Boolean) -> Unit)?,
    onSaveRecurring: ((dueTime: Timestamp?, stages: List<AlarmStage>, recurrenceConfig: RecurrenceConfig) -> Unit)?,
    onEndRecurrence: (() -> Unit)?,
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
                with(saveState) {
                    when {
                        // Recurring alarm in INSTANCE mode
                        isRecurring && mode == AlarmDialogMode.INSTANCE &&
                                existingAlarm != null && onSaveInstance != null -> {
                            onSaveInstance(existingAlarm, instanceDueTime, instanceStages, alsoUpdateRecurrence)
                        }
                        // Recurring alarm in RECURRENCE mode
                        isRecurring && mode == AlarmDialogMode.RECURRENCE &&
                                recurringAlarm != null && onSaveRecurrenceTemplate != null -> {
                            onSaveRecurrenceTemplate(
                                recurringAlarm.id, recurrenceDueTime, recurrenceStages,
                                recurrenceConfig, alsoUpdateInstances
                            )
                        }
                        // Legacy: recurring save (non-modal path)
                        recurrenceConfig.enabled && onSaveRecurring != null -> {
                            onSaveRecurring(instanceDueTime, instanceStages, recurrenceConfig)
                        }
                        // Non-recurring alarm
                        else -> {
                            onSave(instanceDueTime, instanceStages)
                            if (!recurrenceConfig.enabled) onEndRecurrence?.invoke()
                        }
                    }
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
private fun EndRecurrenceButton(
    onEndRecurrence: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        OutlinedButton(
            onClick = {
                onEndRecurrence()
                onDismiss()
            },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colorResource(R.color.destructive_text)
            ),
            border = BorderStroke(1.dp, colorResource(R.color.destructive_text).copy(alpha = 0.5f))
        ) {
            Text(stringResource(R.string.recurrence_end_now))
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
    if (stage.absoluteTimeOfDay != null) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, stage.absoluteTimeOfDay.hour)
            set(Calendar.MINUTE, stage.absoluteTimeOfDay.minute)
        }
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(cal.time)
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
