package org.alkaline.taskbrain.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val ChipShape = RoundedCornerShape(8.dp)
private val ChipFontSize = 13.sp

/**
 * A row component for selecting a date and time independently.
 * Layout: [delete] [label] [date chip] [time chip]
 * Date and time are shown in separate tappable rounded boxes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerRow(
    label: String,
    value: Timestamp?,
    onValueChange: (Timestamp?) -> Unit,
    modifier: Modifier = Modifier,
    showDelete: Boolean
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val is24Hour = remember { DateFormat.is24HourFormat(context) }

    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    val dateText = if (value != null) {
        val cal = Calendar.getInstance().apply { time = value.toDate() }
        val pattern = if (cal.get(Calendar.YEAR) == currentYear) "MMM d" else "MMM d, yyyy"
        SimpleDateFormat(pattern, Locale.getDefault()).format(value.toDate())
    } else {
        stringResource(R.string.datetime_date)
    }

    val timeText = if (value != null) {
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        SimpleDateFormat(pattern, Locale.getDefault()).format(value.toDate())
    } else {
        stringResource(R.string.datetime_time)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDelete) {
            if (value != null) {
                IconButton(
                    onClick = { onValueChange(null) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        ValueChip(
            text = timeText,
            isSet = value != null,
            onClick = { showTimePicker = true }
        )

        Spacer(modifier = Modifier.width(8.dp))

        ValueChip(
            text = dateText,
            isSet = value != null,
            onClick = { showDatePicker = true }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val initialMillis = if (value != null) {
            DateTimeUtils.getDatePickerMillisFromTimestamp(value)
        } else {
            System.currentTimeMillis()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                            ?: return@TextButton
                        val cal = Calendar.getInstance()
                        if (value != null) {
                            cal.time = value.toDate()
                        } else {
                            // Default to 1 hour from now
                            cal.add(Calendar.HOUR_OF_DAY, 1)
                        }
                        val timestamp = DateTimeUtils.combineDatePickerWithTime(
                            datePickerMillis = selectedMillis,
                            hour = cal.get(Calendar.HOUR_OF_DAY),
                            minute = cal.get(Calendar.MINUTE)
                        )
                        onValueChange(timestamp)
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val calendar = Calendar.getInstance()
        if (value != null) {
            calendar.time = value.toDate()
        }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = is24Hour
        )

        Dialog(onDismissRequest = { showTimePicker = false }) {
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
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        TextButton(
                            onClick = {
                                val dateMillis = if (value != null) {
                                    DateTimeUtils.getDatePickerMillisFromTimestamp(value)
                                } else {
                                    // Default to today or tomorrow based on selected time
                                    val now = Calendar.getInstance()
                                    val selectedIsBeforeNow =
                                        timePickerState.hour < now.get(Calendar.HOUR_OF_DAY) ||
                                        (timePickerState.hour == now.get(Calendar.HOUR_OF_DAY) &&
                                            timePickerState.minute <= now.get(Calendar.MINUTE))
                                    if (selectedIsBeforeNow) {
                                        now.add(Calendar.DAY_OF_MONTH, 1)
                                    }
                                    DateTimeUtils.getDatePickerMillisFromTimestamp(
                                        Timestamp(now.time)
                                    )
                                }
                                val timestamp = DateTimeUtils.combineDatePickerWithTime(
                                    datePickerMillis = dateMillis,
                                    hour = timePickerState.hour,
                                    minute = timePickerState.minute
                                )
                                onValueChange(timestamp)
                                showTimePicker = false
                            }
                        ) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ValueChip(
    text: String,
    isSet: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = ChipShape,
        border = BorderStroke(
            1.dp,
            if (isSet) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (isSet) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = ChipFontSize,
            color = if (isSet) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
