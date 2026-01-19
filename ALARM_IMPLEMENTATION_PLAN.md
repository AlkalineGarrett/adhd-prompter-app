# Alarm/Reminder Feature Implementation Plan

## Overview

Add an alarm/reminder system that allows users to set multiple time-based triggers for any line in their notes. Alarms appear in a dedicated screen and can trigger notifications, lock screen alerts, and audible alarms.

---

## Key Design Decisions

1. **Upcoming vs Later:** Based on `upcomingTime` field - if set and in the future, shows in "Upcoming"; if not set, shows in "Later"
2. **Alarm-Line Association:** Alarms are associated with **note IDs** (each line has a note ID via `NoteLineTracker`). Multiple alarms per line/note are supported.
3. **Status Bar Icon:** Changes color based on the highest alarm level of any active alarm (e.g., green for upcoming, yellow for notify, orange for urgent, red for alarm)
4. **Urgent Display:** Red-themed full-screen activity shown over the lock screen
5. **Completed Alarms:** Keep separate "Completed" and "Cancelled" sections in the alarms screen
6. **Notification Content:** Shows line text only
7. **Snooze Options:** 2 minutes, 10 minutes, 1 hour

---

## 1. Data Model

### 1.1 Alarm Data Class

```kotlin
// data/Alarm.kt
data class Alarm(
    val id: String,                    // Unique alarm ID
    val userId: String,                // Owner
    val noteId: String,                // Associated note/line ID (from NoteLineTracker)
    val lineContent: String,           // Snapshot of line text (for display, updated on save)
    val createdAt: Timestamp,
    val updatedAt: Timestamp,

    // Four time thresholds (nullable = not set)
    val upcomingTime: Timestamp?,      // When to show in "Upcoming" list
    val notifyTime: Timestamp?,        // Lock screen notification + status bar icon
    val urgentTime: Timestamp?,        // Lock screen red tint (full-screen red activity)
    val alarmTime: Timestamp?,         // Audible alarm with snooze

    val status: AlarmStatus,           // pending, done, cancelled
    val snoozedUntil: Timestamp?       // If snoozed, when to fire again
)

enum class AlarmStatus {
    PENDING,    // Active alarm
    DONE,       // User marked as done
    CANCELLED   // User cancelled (not done)
}
```

### 1.2 Firestore Structure

```
/users/{userId}/alarms/{alarmId}
```

Fields map directly to `Alarm` data class. Index on `userId + status + upcomingTime` for efficient querying.

### 1.3 Repository

```kotlin
// data/AlarmRepository.kt
class AlarmRepository {
    suspend fun createAlarm(alarm: Alarm): Result<String>
    suspend fun updateAlarm(alarm: Alarm): Result<Unit>
    suspend fun deleteAlarm(alarmId: String): Result<Unit>
    suspend fun getAlarm(alarmId: String): Result<Alarm?>
    suspend fun getAlarmsForNote(noteId: String): Result<List<Alarm>>

    // List queries for AlarmsScreen sections
    suspend fun getUpcomingAlarms(userId: String): Result<List<Alarm>>   // status=PENDING, upcomingTime != null, ordered by upcomingTime
    suspend fun getLaterAlarms(userId: String): Result<List<Alarm>>      // status=PENDING, upcomingTime == null
    suspend fun getCompletedAlarms(userId: String): Result<List<Alarm>>  // status=DONE, ordered by updatedAt desc
    suspend fun getCancelledAlarms(userId: String): Result<List<Alarm>>  // status=CANCELLED, ordered by updatedAt desc

    // Status changes
    suspend fun markDone(alarmId: String): Result<Unit>
    suspend fun markCancelled(alarmId: String): Result<Unit>
    suspend fun snoozeAlarm(alarmId: String, snoozeDuration: SnoozeDuration): Result<Unit>

    // For status bar icon - get highest priority active alarm
    suspend fun getHighestPriorityAlarm(userId: String): Result<AlarmPriority?>

    // Sync line content when note is saved
    suspend fun updateLineContentForNote(noteId: String, newContent: String): Result<Unit>
}

enum class SnoozeDuration(val minutes: Int) {
    TWO_MINUTES(2),
    TEN_MINUTES(10),
    ONE_HOUR(60)
}

enum class AlarmPriority {
    UPCOMING,  // Green - upcomingTime is set
    NOTIFY,    // Yellow - notifyTime has passed or is imminent
    URGENT,    // Orange - urgentTime has passed or is imminent
    ALARM      // Red - alarmTime has passed or is imminent
}
```

---

## 2. Android Infrastructure

### 2.1 Permissions (AndroidManifest.xml)

```xml
<!-- Exact alarms (required for alarm clock functionality) -->
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />

<!-- Notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Full-screen intent for lock screen display -->
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />

<!-- Vibrate for alarm -->
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Boot receiver to reschedule alarms -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### 2.2 Notification Channels

```kotlin
// Create in Application.onCreate() or MainActivity
object NotificationChannels {
    const val REMINDER_CHANNEL_ID = "reminders"
    const val URGENT_CHANNEL_ID = "urgent_reminders"
    const val ALARM_CHANNEL_ID = "alarms"

    fun createChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        // Regular notification (notifyTime)
        notificationManager.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )

        // Urgent notification (urgentTime) - higher priority
        notificationManager.createNotificationChannel(
            NotificationChannel(URGENT_CHANNEL_ID, "Urgent Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                enableLights(true)
                lightColor = Color.RED
            }
        )

        // Alarm (alarmTime) - full screen intent
        notificationManager.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
                enableVibration(true)
            }
        )
    }
}
```

### 2.3 Alarm Scheduling Service

```kotlin
// service/AlarmScheduler.kt
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleAlarm(alarm: Alarm) {
        // Schedule each non-null time threshold
        alarm.notifyTime?.let { scheduleNotification(alarm.id, it, AlarmType.NOTIFY) }
        alarm.urgentTime?.let { scheduleNotification(alarm.id, it, AlarmType.URGENT) }
        alarm.alarmTime?.let { scheduleNotification(alarm.id, it, AlarmType.ALARM) }
    }

    fun cancelAlarm(alarmId: String) {
        // Cancel all pending intents for this alarm
        AlarmType.values().forEach { type ->
            val intent = createIntent(alarmId, type)
            val pendingIntent = PendingIntent.getBroadcast(...)
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun scheduleNotification(alarmId: String, time: Timestamp, type: AlarmType) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarmId)
            putExtra("type", type.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            generateRequestCode(alarmId, type),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.toDate().time,
            pendingIntent
        )
    }
}

enum class AlarmType { NOTIFY, URGENT, ALARM }
```

### 2.4 Broadcast Receiver

```kotlin
// receiver/AlarmReceiver.kt
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarmId") ?: return
        val type = AlarmType.valueOf(intent.getStringExtra("type") ?: return)

        // Fetch alarm from repository (use coroutine scope)
        CoroutineScope(Dispatchers.IO).launch {
            val alarm = alarmRepository.getAlarm(alarmId).getOrNull() ?: return@launch
            if (alarm.status != AlarmStatus.PENDING) return@launch

            when (type) {
                AlarmType.NOTIFY -> showNotification(context, alarm)
                AlarmType.URGENT -> showUrgentNotification(context, alarm)
                AlarmType.ALARM -> showAlarmWithFullScreen(context, alarm)
            }
        }
    }
}
```

### 2.5 Boot Receiver (Reschedule after reboot)

```kotlin
// receiver/BootReceiver.kt
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule all pending alarms from Firestore
            CoroutineScope(Dispatchers.IO).launch {
                val alarms = alarmRepository.getUpcomingAlarms(userId).getOrNull() ?: return@launch
                alarms.forEach { alarmScheduler.scheduleAlarm(it) }
            }
        }
    }
}
```

### 2.6 Full-Screen Alarm Activity

```kotlin
// ui/alarm/AlarmActivity.kt
class AlarmActivity : ComponentActivity() {
    // Shows when alarm fires and device is locked
    // Uses: setShowWhenLocked(true), setTurnScreenOn(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val alarmId = intent.getStringExtra("alarmId")
        val alarmType = AlarmType.valueOf(intent.getStringExtra("type") ?: "ALARM")

        setContent {
            AlarmScreen(
                alarmId = alarmId,
                alarmType = alarmType,
                onDismiss = { finish() },
                onSnooze = { duration -> snoozeAndFinish(duration) },
                onMarkDone = { markDoneAndFinish() }
            )
        }
    }
}

@Composable
fun AlarmScreen(
    alarmId: String,
    alarmType: AlarmType,
    onDismiss: () -> Unit,
    onSnooze: (SnoozeDuration) -> Unit,
    onMarkDone: () -> Unit
) {
    // Background color based on alarm type
    val backgroundColor = when (alarmType) {
        AlarmType.URGENT -> Color(0xFFB71C1C)  // Dark red for urgent
        AlarmType.ALARM -> Color(0xFF1A1A1A)   // Dark for alarm
        else -> MaterialTheme.colorScheme.background
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Alarm icon (animated pulse for ALARM type)
            Icon(
                painter = painterResource(R.drawable.ic_alarm),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(96.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Line content
            Text(
                text = lineContent,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            // Snooze buttons (only for ALARM type)
            if (alarmType == AlarmType.ALARM) {
                Text("Snooze for:", color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SnoozeButton("2 min") { onSnooze(SnoozeDuration.TWO_MINUTES) }
                    SnoozeButton("10 min") { onSnooze(SnoozeDuration.TEN_MINUTES) }
                    SnoozeButton("1 hour") { onSnooze(SnoozeDuration.ONE_HOUR) }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                Button(onClick = onMarkDone) {
                    Text("Mark Done")
                }
            }
        }
    }
}
```

---

## 3. UI Components

### 3.1 Command Bar Button

**File:** `CommandBar.kt`

Add new button:
- **Icon:** `ic_alarm` (alarm clock)
- **Enabled:** When `textFieldValue.selection.collapsed` (cursor positioned, no selection)
- **Callback:** `onAddAlarm: () -> Unit`

```kotlin
// In CommandBar composable, add:
IconButton(
    onClick = onAddAlarm,
    enabled = isAlarmEnabled,  // selection.collapsed
    modifier = Modifier.size(40.dp)
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_alarm),
        contentDescription = "Add alarm",
        tint = if (isAlarmEnabled) IconTint else DisabledIconTint,
        modifier = Modifier.size(24.dp)
    )
}
```

### 3.2 Alarm Configuration Dialog

**File:** `ui/currentnote/AlarmConfigDialog.kt`

```kotlin
@Composable
fun AlarmConfigDialog(
    lineContent: String,           // The line text to display at top
    existingAlarm: Alarm?,         // null for new, populated for edit
    onSave: (Alarm) -> Unit,
    onMarkDone: () -> Unit,        // Only shown for existing alarms
    onCancel: () -> Unit,          // Mark as cancelled (not done)
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column {
            // Line preview at top
            Text(lineContent, style = MaterialTheme.typography.bodyLarge)

            Divider()

            // Four date/time pickers
            DateTimePicker(
                label = "Show in Upcoming list",
                value = upcomingTime,
                onValueChange = { upcomingTime = it }
            )

            DateTimePicker(
                label = "Lock screen notification",
                value = notifyTime,
                onValueChange = { notifyTime = it }
            )

            DateTimePicker(
                label = "Urgent (red tint)",
                value = urgentTime,
                onValueChange = { urgentTime = it }
            )

            DateTimePicker(
                label = "Sound alarm",
                value = alarmTime,
                onValueChange = { alarmTime = it }
            )

            // Action buttons
            Row {
                if (existingAlarm != null) {
                    TextButton(onClick = onMarkDone) { Text("Done") }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close") }
                Button(onClick = { onSave(buildAlarm()) }) { Text("Save") }
            }
        }
    }
}
```

### 3.3 Date/Time Picker Component

**File:** `ui/components/DateTimePicker.kt`

Use Material 3 `DatePicker` + `TimePicker` dialogs, or a combined picker. Each row shows:
- Label
- Selected date/time (or "Not set")
- Clear button (to unset)
- Tap to open picker

### 3.4 Alarm Symbol in Text

**Symbol:** `⏰` (U+23F0) or similar

**Insertion:** When alarm is created, insert symbol at end of line:
```kotlin
fun insertAlarmSymbol(textFieldValue: TextFieldValue, alarmId: String): TextFieldValue {
    val cursorPos = textFieldValue.selection.start
    val text = textFieldValue.text
    val lineEnd = text.indexOf('\n', cursorPos).takeIf { it >= 0 } ?: text.length

    val newText = text.substring(0, lineEnd) + " ⏰" + text.substring(lineEnd)
    return TextFieldValue(newText, TextRange(cursorPos))
}
```

**Tap Detection:** Two approaches:

**Option A: AnnotatedString with LinkAnnotation** (Preferred for read-only display)
- Requires converting to `AnnotatedString` with alarm ID annotations
- Use `pushStringAnnotation("alarm", alarmId)` around alarm symbols

**Option B: Offset-based detection in BasicTextField**
- In `onTextLayout`, store `TextLayoutResult`
- Add `pointerInput` modifier to detect taps
- Use `layoutResult.getOffsetForPosition(pos)` to find tapped character
- Check if character is `⏰` and look up associated alarm

**Recommended:** Option B, since the text is editable and AnnotatedString complicates editing.

---

## 4. Navigation & Screens

### 4.1 Add Alarms Screen Route

**File:** `MainScreen.kt`

```kotlin
sealed class Screen(val route: String) {
    // ... existing routes
    object Alarms : Screen("alarms")
}

// In NavHost:
composable(Screen.Alarms.route) {
    AlarmsScreen(
        onAlarmClick = { alarm -> /* show config dialog */ },
        onNavigateToNote = { noteId -> navController.navigate("currentNote?noteId=$noteId") }
    )
}

// In bottom nav:
NavigationBarItem(
    icon = { Icon(painterResource(R.drawable.ic_alarm), "Alarms") },
    label = { Text("Alarms") },
    selected = currentRoute == Screen.Alarms.route,
    onClick = { navController.navigate(Screen.Alarms.route) }
)
```

### 4.2 Alarms Screen

**File:** `ui/alarms/AlarmsScreen.kt`

```kotlin
@Composable
fun AlarmsScreen(
    viewModel: AlarmsViewModel = viewModel(),
    onAlarmClick: (Alarm) -> Unit,
    onNavigateToNote: (String) -> Unit
) {
    val upcomingAlarms by viewModel.upcomingAlarms.observeAsState(emptyList())
    val laterAlarms by viewModel.laterAlarms.observeAsState(emptyList())
    val completedAlarms by viewModel.completedAlarms.observeAsState(emptyList())
    val cancelledAlarms by viewModel.cancelledAlarms.observeAsState(emptyList())

    LazyColumn {
        // Upcoming section (upcomingTime is set)
        item { SectionHeader("Upcoming", count = upcomingAlarms.size) }
        items(upcomingAlarms) { alarm ->
            AlarmListItem(
                alarm = alarm,
                onClick = { onAlarmClick(alarm) },
                onMarkDone = { viewModel.markDone(alarm.id) },
                onMarkCancelled = { viewModel.markCancelled(alarm.id) }
            )
        }

        // Later section (upcomingTime not set)
        item { SectionHeader("Later", count = laterAlarms.size) }
        items(laterAlarms) { alarm ->
            AlarmListItem(
                alarm = alarm,
                onClick = { onAlarmClick(alarm) },
                onMarkDone = { viewModel.markDone(alarm.id) },
                onMarkCancelled = { viewModel.markCancelled(alarm.id) }
            )
        }

        // Completed section (collapsible)
        item {
            CollapsibleSectionHeader(
                title = "Completed",
                count = completedAlarms.size,
                defaultExpanded = false
            )
        }
        items(completedAlarms) { alarm ->
            AlarmListItem(
                alarm = alarm,
                onClick = { onAlarmClick(alarm) },
                showTimestamp = true  // Show when it was completed
            )
        }

        // Cancelled section (collapsible)
        item {
            CollapsibleSectionHeader(
                title = "Cancelled",
                count = cancelledAlarms.size,
                defaultExpanded = false
            )
        }
        items(cancelledAlarms) { alarm ->
            AlarmListItem(
                alarm = alarm,
                onClick = { onAlarmClick(alarm) },
                showTimestamp = true  // Show when it was cancelled
            )
        }
    }
}

@Composable
fun AlarmListItem(
    alarm: Alarm,
    onClick: () -> Unit,
    onMarkDone: (() -> Unit)? = null,
    onMarkCancelled: (() -> Unit)? = null,
    showTimestamp: Boolean = false
) {
    // Shows:
    // - Colored indicator based on highest active threshold
    // - Line content text
    // - Next trigger time (if pending)
    // - Swipe actions for Done/Cancel (if pending)
    // - Completion timestamp (if showTimestamp)
}
```

### 4.3 Alarms ViewModel

**File:** `ui/alarms/AlarmsViewModel.kt`

```kotlin
class AlarmsViewModel : ViewModel() {
    private val repository = AlarmRepository()

    val upcomingAlarms: LiveData<List<Alarm>>   // status=PENDING, upcomingTime != null
    val laterAlarms: LiveData<List<Alarm>>      // status=PENDING, upcomingTime == null
    val completedAlarms: LiveData<List<Alarm>>  // status=DONE
    val cancelledAlarms: LiveData<List<Alarm>>  // status=CANCELLED

    fun markDone(alarmId: String) { ... }
    fun markCancelled(alarmId: String) { ... }
    fun reactivateAlarm(alarmId: String) { ... }  // Move from completed/cancelled back to pending
}
```

---

## 5. Integration Points

### 5.1 CurrentNoteScreen Changes

```kotlin
// State
var showAlarmDialog by remember { mutableStateOf(false) }
var alarmDialogLine by remember { mutableStateOf("") }
var editingAlarm by remember { mutableStateOf<Alarm?>(null) }

// CommandBar callback
CommandBar(
    // ... existing callbacks
    onAddAlarm = {
        val lineContent = getCurrentLineContent(textFieldValue)
        alarmDialogLine = lineContent
        editingAlarm = null
        showAlarmDialog = true
    },
    isAlarmEnabled = textFieldValue.selection.collapsed
)

// Dialog
if (showAlarmDialog) {
    AlarmConfigDialog(
        lineContent = alarmDialogLine,
        existingAlarm = editingAlarm,
        onSave = { alarm ->
            // Save alarm, insert symbol if new, schedule
        },
        onDismiss = { showAlarmDialog = false }
    )
}
```

### 5.2 Alarm Symbol Tap Handling in NoteTextField

```kotlin
// In NoteTextField, add tap detection:
Modifier.pointerInput(Unit) {
    detectTapGestures { offset ->
        textLayoutResult?.let { layout ->
            val charOffset = layout.getOffsetForPosition(offset)
            if (textFieldValue.text.getOrNull(charOffset) == '⏰') {
                // Find the line containing this position
                val lineStart = textFieldValue.text.lastIndexOf('\n', charOffset - 1) + 1
                val lineEnd = textFieldValue.text.indexOf('\n', charOffset).takeIf { it >= 0 } ?: textFieldValue.text.length

                // Count which alarm symbol this is on the line (for multiple alarms)
                val lineText = textFieldValue.text.substring(lineStart, lineEnd)
                val symbolIndex = lineText.substring(0, charOffset - lineStart).count { it == '⏰' }

                // Get the note ID for this line (from NoteLineTracker)
                val lineIndex = textFieldValue.text.substring(0, lineStart).count { it == '\n' }
                val noteId = noteLineTracker.getNoteIdForLine(lineIndex)

                if (noteId != null) {
                    // Fetch alarms for this note and show the nth one
                    onAlarmSymbolTap(noteId, symbolIndex)
                }
            }
        }
    }
}
```

### 5.3 NoteLineTracker Integration

The current `NoteLineTracker` tracks note IDs between save operations but doesn't maintain real-time line-to-ID mapping during editing. For alarm symbol tap detection, we need:

**Option A: Extend NoteLineTracker** (Recommended)
- Add `getCurrentLineNoteIds(): List<String?>` that returns note IDs for current editor lines
- Update this mapping on each text change
- Use existing two-phase matching logic

**Option B: Lazy lookup on tap**
- On symbol tap, find the line content
- Query Firestore for the note with matching content under the parent note
- Slower but doesn't require NoteLineTracker changes

### 5.4 Syncing Alarm Line Content on Save

When a note is saved, update any associated alarms with the current line content:

```kotlin
// In CurrentNoteViewModel.saveContent()
suspend fun saveContent(content: String) {
    val lines = content.lines()
    val lineToNoteId = noteLineTracker.matchLines(previousLines, lines)

    // Save note as usual...

    // Update alarm line content for any changed lines
    lineToNoteId.forEachIndexed { index, noteId ->
        if (noteId != null) {
            alarmRepository.updateLineContentForNote(noteId, lines[index])
        }
    }
}
```

---

## 6. Implementation Order

### Phase 1: Foundation
1. Create `Alarm` data model
2. Create `AlarmRepository` with Firestore operations
3. Set up notification channels
4. Add permissions to manifest

### Phase 2: UI - Dialog & Button
5. Create `DateTimePicker` component
6. Create `AlarmConfigDialog`
7. Add alarm button to `CommandBar`
8. Integrate dialog in `CurrentNoteScreen`

### Phase 3: Scheduling
9. Create `AlarmScheduler` service
10. Create `AlarmReceiver` broadcast receiver
11. Create `BootReceiver` for rescheduling
12. Create `AlarmActivity` for full-screen alarm

### Phase 4: Alarm Screen
13. Create `AlarmsViewModel`
14. Create `AlarmsScreen` with upcoming/later sections
15. Add navigation route and bottom nav item
16. Replace placeholder `NotificationsScreen`

### Phase 5: Symbol Interaction
17. Implement alarm symbol insertion
18. Implement tap detection in `NoteTextField`
19. Link alarm IDs to line positions

### Phase 6: Polish
20. Add alarm sound/vibration customization
21. Add snooze functionality
22. Handle edge cases (deleted notes, etc.)
23. Write tests

---

## 7. Files to Create/Modify

### New Files
```
data/
  Alarm.kt
  AlarmRepository.kt

service/
  AlarmScheduler.kt
  NotificationChannels.kt

receiver/
  AlarmReceiver.kt
  BootReceiver.kt

ui/alarms/
  AlarmsScreen.kt
  AlarmsViewModel.kt
  AlarmListItem.kt

ui/currentnote/
  AlarmConfigDialog.kt

ui/components/
  DateTimePicker.kt

ui/alarm/
  AlarmActivity.kt
```

### Modified Files
```
AndroidManifest.xml          - Permissions, receivers, activity
MainScreen.kt                - Add Alarms route & nav item
CommandBar.kt                - Add alarm button
CurrentNoteScreen.kt         - Dialog integration
NoteTextField.kt             - Symbol tap detection
res/drawable/                - ic_alarm.xml
```

---

## 8. Testing Considerations

- Unit tests for `AlarmRepository`
- Unit tests for time threshold logic (upcoming vs later)
- Integration tests for `AlarmScheduler`
- UI tests for `AlarmConfigDialog`
- Manual testing on different Android versions (permission handling varies)
- Test boot receiver rescheduling
- Test alarm firing when app is killed
