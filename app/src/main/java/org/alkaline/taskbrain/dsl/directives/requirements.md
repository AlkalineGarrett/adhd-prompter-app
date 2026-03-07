# Schedule Directives Requirements

This document captures the requirements for the scheduling feature, including missed schedule detection and the Schedules UI.

## Missed Schedules Detection

### Key Behavior

**15-Minute Threshold Rule:**
- Schedules that are **≤15 minutes late** auto-execute normally
- Schedules that are **>15 minutes late** are marked as "missed" and require user review
- Missed schedules do NOT auto-execute; they wait for manual user action

### Rationale

When the app or device is unavailable (phone off, no network, app killed), schedules can't run on time. Rather than silently executing stale actions that may no longer be relevant:
- Recent schedules (within 15 min) are assumed still relevant and auto-execute
- Older schedules require user confirmation before running

---

## Data Model

### ScheduleExecution

Tracks individual execution attempts for schedules.

**Firestore path:** `users/{userId}/scheduleExecutions/{executionId}`

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Document ID |
| `scheduleId` | String | Reference to parent Schedule |
| `userId` | String | User who owns this execution |
| `scheduledFor` | Timestamp | Original scheduled time |
| `executedAt` | Timestamp? | When it actually ran (null = missed, pending review) |
| `success` | Boolean | Whether execution succeeded |
| `error` | String? | Error message if failed |
| `manualRun` | Boolean | True if triggered from Schedules screen |
| `createdAt` | Timestamp | When this record was created |

**Derived states:**
- `isMissed`: executedAt == null
- `isCompleted`: executedAt != null && success
- `isFailed`: executedAt != null && !success

---

## Schedules Screen UI

### Structure

The Schedules screen has 3 tabs:

1. **Last 24 Hours** - History of executed schedules
   - Shows completed executions (successful and failed)
   - Each item shows: status icon, schedule ID, execution time, manual run indicator, error if any

2. **Next 24 Hours** - Upcoming schedules
   - Shows active schedules with nextExecution within 24 hours
   - Each item shows: schedule icon, note path, frequency description, countdown

3. **Missed** - Schedules awaiting user review
   - Shows executions where executedAt is null
   - Each item has a checkbox (all selected by default)
   - "Run" button at bottom executes selected schedules
   - Badge count shown on tab

### Empty States

Each tab shows appropriate empty state messages:
- "No executions in the last 24 hours"
- "No schedules in the next 24 hours"
- "No missed schedules"

---

## Missed Schedules Banner

### Behavior

- Displayed at the top of all screens (below app bar, above content)
- Shows count: "{N} schedule(s) missed - tap to review"
- Tapping navigates to Schedules screen (Missed tab)
- Uses error container color scheme for visibility

### Visibility Rules

Banner is shown when:
- User is signed in
- There are missed schedules (count > 0)
- Not already on the Schedules screen
- Not on the Login screen

---

## ScheduleWorker Changes

### Execution Flow

```
For each due schedule:
  1. Calculate delay = now - scheduledFor
  2. If delay ≤ 15 minutes:
     - Execute the schedule action
     - Record execution in ScheduleExecutionRepository
     - Update Schedule.lastExecution via ScheduleRepository
  3. If delay > 15 minutes:
     - Record as missed (executedAt = null) in ScheduleExecutionRepository
     - Advance Schedule.nextExecution so it's not picked up again
     - Do NOT execute the action
```

### Constants

- `LATE_THRESHOLD_MS = 15 * 60 * 1000L` (15 minutes)

---

## Manual Execution

### ScheduleManager.executeScheduleNow()

Allows manual execution of missed schedules from the UI:

1. Get the ScheduleExecution record
2. Get the associated Schedule
3. Execute the schedule action (same logic as ScheduleWorker)
4. Update ScheduleExecution with executedAt, success, error, manualRun=true
5. Update Schedule via markExecuted()

---

## Tab Data Queries

| Tab | Query | Source |
|-----|-------|--------|
| Last 24 Hours | `executedAt >= now - 24h` | ScheduleExecutionRepository |
| Next 24 Hours | `nextExecution > now AND nextExecution <= now + 24h AND status = ACTIVE` | ScheduleRepository |
| Missed | `executedAt == null` | ScheduleExecutionRepository |

---

## Real-time Updates

- `ScheduleExecutionRepository.observeMissedCount()` provides a Flow<Int>
- MainScreen observes this to show/hide the banner
- SchedulesScreen refreshes on ON_RESUME lifecycle event

---

## String Resources

```xml
<string name="title_schedules">Schedules</string>
<string name="missed_schedules_banner">%d schedule(s) missed - tap to review</string>
<string name="tab_last_24_hours">Last 24 Hours</string>
<string name="tab_next_24_hours">Next 24 Hours</string>
<string name="tab_missed">Missed</string>
<string name="run_selected">Run</string>
<string name="no_history">No executions in the last 24 hours</string>
<string name="no_upcoming">No schedules in the next 24 hours</string>
<string name="no_missed">No missed schedules</string>
```

---

## Testing Verification

### Unit Tests

- ScheduleExecutionRepository CRUD operations
- 15-minute threshold logic in ScheduleWorker
- SchedulesViewModel state transitions

### Manual Testing

1. Create a schedule with `daily_at()` set to a time >15 min in the past
2. Verify it appears in Missed tab and banner shows
3. Select and run - verify it executes and moves to Last 24 Hours
4. Create schedule for near future - verify it appears in Next 24 Hours
5. Wait for execution - verify it moves to Last 24 Hours

### Edge Cases

- Empty states for all three tabs
- Multiple missed schedules selection
- Network errors during manual execution
- User not signed in (graceful handling)

---

## Additional Requirements (Clarified)

### Missed Execution Cleanup

**Auto-delete after 30 days:**
- Missed executions older than 30 days are automatically deleted
- Cleanup runs during ScheduleWorker execution
- Prevents unbounded accumulation of stale records

### Dismiss Without Running

**Users can dismiss missed schedules:**
- Add "Dismiss" button alongside "Run" in the Missed tab
- Dismissing deletes the ScheduleExecution record without executing
- Useful for schedules that are no longer relevant

### Display Information

**Schedule items show rich context:**
- Note name (first line of note content)
- Note path (if set)
- Directive source code (truncated if long)
- Requires joining ScheduleExecution to Schedule data

### Error Handling

**Errors persist until dismissed:**
- When manual execution fails, error message stays visible
- User must explicitly dismiss the error
- Prevents errors from being missed due to auto-refresh

### Selection Persistence

**Remember selections across navigation:**
- When returning to Missed tab, previous selections are preserved
- Selection state stored in ViewModel (not reset on reload)
- Only reset when missed list changes (items added/removed)

### Multiple Missed Occurrences

**One record per missed occurrence:**
- If a daily schedule is missed 3 days in a row, 3 ScheduleExecution records are created
- Each can be run or dismissed independently
- This is the current behavior (confirmed as correct)

### Notifications

**In-app banner only:**
- No system notifications for missed schedules
- Banner in MainScreen is sufficient
- This is the current behavior (confirmed as correct)

### Precise vs Approximate Scheduling

**Schedule precision parameter:**
- Schedules have a `precise` boolean field (default: `false`)
- DSL functions accept `precise` parameter: `daily_at("09:05", precise: true)`

**Approximate scheduling (precise: false):**
- Uses WorkManager periodic job (current behavior)
- May be delayed by Android battery optimization, Doze mode
- Suitable for background tasks where exact timing isn't critical

**Precise scheduling (precise: true):**
- Uses AlarmManager with exact alarms
- Requires `SCHEDULE_EXACT_ALARM` permission (Android 12+)
- Wakes device at exact scheduled time
- Suitable for time-sensitive reminders

**Implementation notes:**
- ScheduleWorker continues to handle approximate schedules
- New AlarmReceiver handles precise schedules via AlarmManager
- Both paths record executions in ScheduleExecutionRepository
- Both paths apply the 15-minute missed threshold
