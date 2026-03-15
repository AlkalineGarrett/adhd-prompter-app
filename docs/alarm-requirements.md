# Alarm State Management Requirements

Applies to all alarm state transitions in the Android app.

## Terminology

- **Alarm**: a scheduled reminder tied to a note line. Has a due time, stages, and status.
- **Recurring alarm template**: a `RecurringAlarm` that spawns alarm instances on a schedule. Has its own status lifecycle.
- **Instance**: an `Alarm` spawned by a recurring template. Linked via `recurringAlarmId`.
- **Stage**: a trigger point relative to due time. Each alarm has an ordered list of stages, each independently enabled/disabled.
  - `NOTIFICATION` — status bar notification (low priority).
  - `LOCK_SCREEN` — full-screen activity + red wallpaper tint (medium priority).
  - `SOUND_ALARM` — audible alarm with snooze (highest priority).
- **Status** (`AlarmStatus`): `PENDING`, `DONE`, `CANCELLED`.
- **Recurring status** (`RecurringAlarmStatus`): `ACTIVE`, `PAUSED`, `ENDED`.
- **Recurrence type** (`RecurrenceType`):
  - `FIXED` — calendar-anchored via RRULE. Next instance created when trigger fires.
  - `RELATIVE` — completion-anchored. Next instance created when current is completed/cancelled.
- **Snooze**: temporarily suppresses an alarm. Sets `snoozedUntil` timestamp; alarm re-triggers when it expires.
- **Deactivate**: cancel all PendingIntents, exit urgent state, dismiss notification. Always precedes a state change that removes the alarm from active display.
- **PendingIntent request code**: `alarmId.hashCode() * 10 + alarmType.ordinal`. One per (alarm, stage type) pair.

## Centralization Rule

All state transitions go through `AlarmStateManager`. No caller (UI, receiver, ViewModel) directly modifies alarm status in the database or manages side effects. `AlarmStateManager` is the single authority for:
- Database status updates
- PendingIntent lifecycle (via `AlarmScheduler`)
- Urgent state lifecycle (via `UrgentStateManager`)
- Notification lifecycle (via `NotificationHelper`)
- Recurrence advancement (via `RecurrenceScheduler`)

## Layer Responsibilities

### Service layer (`AlarmStateManager`, `AlarmScheduler`, `RecurrenceScheduler`, `UrgentStateManager`, `NotificationHelper`)
- Owns all state transitions and side effects.
- `AlarmStateManager`: orchestrates transitions. Every public method is a complete transition (DB + all side effects).
- `AlarmScheduler`: PendingIntent scheduling/cancellation via AlarmManager. Pure scheduling — no DB writes.
- `RecurrenceScheduler`: instance creation, orphan cleanup, bootstrap recovery. Writes new alarms and updates templates.
- `UrgentStateManager`: wallpaper set/restore, tracks which alarms are in urgent state.
- `NotificationHelper`: builds and shows/dismisses notifications. No state decisions.

### Receiver layer (`AlarmReceiver`, `AlarmActionReceiver`)
- Thin dispatch. Validates intent extras, delegates to service layer.
- `AlarmReceiver`: handles `ACTION_ALARM_TRIGGERED`. Checks `shouldShowAlarm()`, shows notification/urgent state, triggers FIXED recurrence.
- `AlarmActionReceiver`: handles notification button actions (`ACTION_MARK_DONE`, `ACTION_SNOOZE`, `ACTION_CANCEL`). Delegates to `AlarmStateManager`.

### UI layer (`CurrentNoteViewModel`, `AlarmActivity`, `AlarmsViewModel`)
- Calls `AlarmStateManager` methods. Never writes alarm status directly.
- Reads alarm state for display. Refreshes on `AlarmUpdateEvent`.
- Handles permission checks and user-facing warnings.

## Side Effect Ordering

### Deactivation (always this order)
1. Cancel all PendingIntents for the alarm (`AlarmScheduler.cancelAlarm`)
2. Exit urgent state (`UrgentStateManager.exitUrgentState`) — restores wallpaper if no other urgent alarms remain
3. Dismiss notification (`NotificationHelper.dismiss`)

### Activation (scheduling a new/updated alarm)
1. Write alarm to database
2. Schedule PendingIntents for each enabled stage (`AlarmScheduler.scheduleAlarm`)
3. For past triggers at schedule time:
   - `NOTIFICATION`: show immediately (silent)
   - `LOCK_SCREEN`: enter urgent state immediately (silent)
   - `SOUND_ALARM`: skip (cannot retroactively sound)

### Recurrence advancement (after markDone/markCancelled)
1. Complete the status transition (deactivate + DB update)
2. Then advance recurrence (create next instance, schedule it)
3. Clean up orphaned instances (old PENDING instances of same template)

## Multi-Stage Timing

An alarm can have up to 3 stages enabled at different offsets before due time (e.g., NOTIFICATION at -2h, LOCK_SCREEN at -30m, SOUND_ALARM at 0). At any moment, some stages may have already fired and others may be pending. This section specifies behavior across all combinations.

### At activation time (scheduling)

When `AlarmScheduler.scheduleAlarm` is called (transitions 1, 2, 8, 9, 15, 16), each enabled stage is evaluated independently:

| Stage time vs now | NOTIFICATION | LOCK_SCREEN | SOUND_ALARM |
|---|---|---|---|
| **Future** | Schedule PendingIntent | Schedule PendingIntent | Schedule PendingIntent |
| **Past** | Show immediately (silent) | Enter urgent state immediately (silent) | Skip — no retroactive sound |

"Silent" means low-priority notification / no sound. The user missed the original trigger time; we surface the information without startling them.

**All stages past**: NOTIFICATION shown silent + LOCK_SCREEN entered silent + SOUND_ALARM skipped. Result: alarm is PENDING with no future PendingIntents. The notification and urgent state are the only signals to the user. If dismissed without action (transition 10), the alarm becomes orphaned — PENDING in DB with no way to re-trigger. This is a known edge case; the alarms list remains the backstop for acting on it.

### Between stages (some fired, some pending)

At any point between stage triggers, the alarm's active presentation is the union of all stages that have fired so far. Deactivation (transitions 4, 5, 6, 7, 9) cleans up everything — both active presentations and future PendingIntents:

| What exists | Deactivation cleans up |
|---|---|
| Notification showing | `NotificationHelper.dismiss` |
| Urgent state active (wallpaper) | `UrgentStateManager.exitUrgentState` |
| Future PendingIntents | `AlarmScheduler.cancelAlarm` (cancels all 3 types regardless of which are scheduled) |

This is safe because `cancelAlarm` cancels all alarm type PendingIntents unconditionally — no need to track which are currently scheduled.

#### Scenario walkthrough: single-instance alarm, 3 stages (NOTIFICATION at -2h, LOCK_SCREEN at -30m, SOUND_ALARM at 0)

**Action between NOTIFICATION and LOCK_SCREEN** (notification showing, lock screen + sound pending):
- **Mark done/cancelled**: deactivate cancels lock screen + sound PIs, dismisses notification. Clean. No recurrence.
- **Snooze**: deactivate cancels lock screen + sound PIs, dismisses notification. Schedules single snooze trigger at highest priority (SOUND_ALARM). Clean.
- **Dismiss**: dismisses notification only. Lock screen + sound PIs remain — alarm will re-alert at lock screen time. Correct.
- **Update**: deactivate cleans up notification + cancels PIs. Reschedule computes new stage times. Clean.

**Action between LOCK_SCREEN and SOUND_ALARM** (notification + urgent active, sound pending):
- **Mark done/cancelled**: deactivate cancels sound PI, exits urgent state (restores wallpaper), dismisses notification. Clean.
- **Snooze**: same deactivation. Schedules single snooze trigger as SOUND_ALARM. Clean.
- **Dismiss**: dismisses notification, exits urgent state. Sound PI remains — alarm will sound at due time. Correct.

**Action after all stages fired** (notification + urgent + sound all active, no PIs):
- **Mark done/cancelled**: deactivate exits urgent, dismisses notification, cancels PIs (no-op). Clean.
- **Snooze**: same deactivation. Schedules snooze trigger. Clean.
- **Dismiss**: dismisses notification, exits urgent. No future PIs. Alarm is PENDING with no triggers — user must act from alarms list.

#### Scenario walkthrough: FIXED recurring instance, 3 stages (NOTIFICATION at -2h, LOCK_SCREEN at -30m, SOUND_ALARM at 0)

FIXED recurring creates the next instance when any stage trigger fires (transition 11), deduplicated by `currentAlarmId` guard. This interacts with between-stage actions.

**First stage (NOTIFICATION) fires**:
- `AlarmReceiver` calls `RecurrenceScheduler.onFixedInstanceTriggered`.
- Next instance created, template's `currentAlarmId` updated to new instance. Orphaned PENDING instances cleaned up.
- Notification shown for current instance.

**Action between NOTIFICATION and LOCK_SCREEN** (next instance already created):
- **Mark done**: deactivate cancels lock screen + sound PIs, dismisses notification. Recurrence: `onInstanceCompleted` called → records completion, but next instance already exists (created at first trigger). `createNextIfNeeded` is a no-op. Clean.
- **Mark cancelled**: same deactivation. `onInstanceCancelled` → next instance already exists, no-op. Clean.
- **Dismiss**: dismisses notification. Lock screen PI still scheduled — when it fires, `AlarmReceiver` calls `onFixedInstanceTriggered` again, but `currentAlarmId` guard deduplicates (next instance already created). Shows lock screen for current instance. Correct.

**Second stage (LOCK_SCREEN) fires** (if not already done/cancelled):
- `onFixedInstanceTriggered` called again → `currentAlarmId` ≠ this alarm → skip (dedup). No duplicate instance.
- Urgent state entered for current instance.

**Action between LOCK_SCREEN and SOUND_ALARM**:
- **Mark done/cancelled**: same as above — deactivate + recurrence advancement is no-op since next instance exists. Clean.
- **Dismiss**: exits urgent, dismisses notification. Sound PI remains. Correct.

**After all stages fired**:
- Same as single-instance "all stages fired" case, plus recurrence advancement is a no-op.

**Key invariant**: for FIXED, the next instance is created exactly once (on first stage trigger). All subsequent stage triggers and markDone/markCancelled are idempotent with respect to recurrence.

#### Scenario walkthrough: RELATIVE recurring instance, 3 stages (NOTIFICATION at -2h, LOCK_SCREEN at -30m, SOUND_ALARM at 0)

RELATIVE recurring creates the next instance only on completion/cancellation (transitions 12, 13) — NOT on trigger. Stage triggers do not advance recurrence.

**First stage (NOTIFICATION) fires**:
- `AlarmReceiver` does NOT call `onFixedInstanceTriggered` (only for FIXED). Just shows notification.
- No next instance created yet.

**Action between NOTIFICATION and LOCK_SCREEN** (no next instance yet):
- **Mark done**: deactivate cancels lock screen + sound PIs, dismisses notification. Recurrence: `onInstanceCompleted` → records completion, creates next instance, schedules it. Clean.
- **Mark cancelled**: same deactivation. `onInstanceCancelled` → creates next instance (no completion recorded). Clean.
- **Dismiss**: dismisses notification. Lock screen PI fires later, shows lock screen. No recurrence effect. Correct.
- **Snooze**: deactivate + schedule snooze trigger. No recurrence advancement (alarm stays PENDING). Clean.

**Action between LOCK_SCREEN and SOUND_ALARM**:
- Same pattern. Mark done/cancelled triggers recurrence. Dismiss/snooze does not.

**After all stages fired**:
- **Mark done/cancelled**: deactivate (exits urgent, dismisses notification) + creates next instance. Clean.
- **Dismiss**: orphan scenario — PENDING with no triggers, no next instance created until user acts from alarms list.

**Key invariant**: for RELATIVE, the next instance is created exactly once (on markDone or markCancelled). Stage triggers never advance recurrence. The next instance's timing is anchored to the completion/cancellation moment, not the trigger moment.

#### Snooze and recurrence interaction

Snooze does NOT advance recurrence for either type:
- The alarm stays PENDING (no status change → no completion/cancellation).
- FIXED: if snooze happens after first stage trigger, next instance is already created. If snooze happens before any trigger (e.g., user snoozed from a re-triggered notification), no next instance yet — it will be created when the snoozed alarm eventually triggers or completes.
- RELATIVE: next instance only created on eventual markDone/markCancelled.

### Snooze stage collapsing

Snooze replaces the multi-stage schedule with a **single trigger** at snooze end time, using the highest-priority enabled stage type. The original stage offsets are not preserved.

When the snooze trigger fires:
- `shouldShowAlarm()` returns true (`snoozedUntil` is now in the past).
- `snoozedUntil` is NOT cleared from the DB — it remains as a historical marker. `shouldShowAlarm` only checks `snoozedUntil < now`.
- The alarm presents as the single snooze-trigger type (e.g., SOUND_ALARM). No staged escalation.

Subsequent snoozes repeat this pattern — always a single trigger at the highest priority.

### Dismiss vs deactivate

**Dismiss** (transition 10) clears the current presentation but does NOT cancel future PendingIntents. The alarm remains active and will re-trigger at the next stage time.

**Deactivate** cancels everything — it is a full teardown used before state changes (done, cancelled, snooze, delete, update).

A dismiss followed by no further stage triggers leaves the alarm PENDING with no active presentation and no future triggers (the "all stages past + dismissed" case). This is the orphan scenario described above.

## State Transitions

### 1. Create (one-shot)

**Trigger**: user creates alarm from editor.
**Precondition**: note is saved (line tracker has stable noteId).
**State change**: ∅ → `PENDING`.
**Side effects**:
1. Save note content (ensure noteId exists).
2. Create `Alarm` in Firestore with status `PENDING`.
3. Schedule PendingIntents via `AlarmScheduler.scheduleAlarm`.
4. Emit `AlarmUpdateEvent`.

**Recurrence**: N/A.

### 2. Create (recurring template + first instance)

**Trigger**: user creates recurring alarm from editor.
**Precondition**: note is saved.
**State change**: ∅ → `ACTIVE` template + `PENDING` first instance.
**Side effects**:
1. Save note content.
2. Create `RecurringAlarm` in Firestore with status `ACTIVE`.
3. Create first `Alarm` instance with `recurringAlarmId` set.
4. Update template's `currentAlarmId` to first instance.
5. Schedule first instance via `AlarmScheduler.scheduleAlarm`.
6. Emit `AlarmUpdateEvent`.

**Recurrence**: template is now active; subsequent instances created per transitions 11–13.

### 3. Trigger (PendingIntent fires)

**Trigger**: AlarmManager fires PendingIntent → `AlarmReceiver.onReceive`.
**Precondition**: alarm exists, `shouldShowAlarm()` returns true (status `PENDING`, not snoozed).
**State change**: `PENDING` → `PENDING` (no DB change).
**Side effects**:
1. Fetch alarm from DB.
2. Check `shouldShowAlarm()` — abort if false.
3. For FIXED recurring: call `RecurrenceScheduler.onFixedInstanceTriggered` (creates next instance if not already created).
4. Show alarm based on stage type:
   - `NOTIFICATION` → `NotificationHelper.showReminderNotification`.
   - `LOCK_SCREEN` → `UrgentStateManager.enterUrgentState`.
   - `SOUND_ALARM` → `NotificationHelper.showAlarmNotification`.

**Recurrence** (FIXED only): next instance created on first stage trigger. Subsequent stage triggers for same alarm are deduplicated via `currentAlarmId` guard.

### 4. Mark done

**Trigger**: user taps "Done" (notification action, AlarmActivity, or editor UI).
**Precondition**: alarm is `PENDING`.
**State change**: `PENDING` → `DONE`.
**Side effects**:
1. Deactivate (cancel PendingIntents, exit urgent, dismiss notification).
2. Update status to `DONE` in Firestore.
3. Advance recurrence: call `RecurrenceScheduler.onInstanceCompleted` (records completion, creates next instance if not ended).
4. Emit `AlarmUpdateEvent`.

**Recurrence**:
- Records completion on template (increment `completionCount`, set `lastCompletionDate`).
- Checks end condition → if met, ends template (transition 14).
- RELATIVE: creates next instance immediately.
- FIXED: creates next instance if not already created.

### 5. Mark cancelled

**Trigger**: user taps "Cancel" (notification action or editor UI).
**Precondition**: alarm is `PENDING`.
**State change**: `PENDING` → `CANCELLED`.
**Side effects**:
1. Deactivate.
2. Update status to `CANCELLED` in Firestore.
3. Advance recurrence: call `RecurrenceScheduler.onInstanceCancelled` (does NOT record completion, creates next instance).
4. Emit `AlarmUpdateEvent`.

**Recurrence**:
- Does NOT increment `completionCount`.
- RELATIVE: creates next instance using `lastCompletionDate` (or `createdAt`) as anchor.
- FIXED: creates next instance if not already created.

### 6. Snooze

**Trigger**: user taps snooze button (notification action or AlarmActivity). Duration: 2 min, 10 min, or 1 hour.
**Precondition**: alarm is `PENDING`.
**State change**: `PENDING` → `PENDING` (`snoozedUntil` set).
**Side effects**:
1. Compute snooze end time: `now + duration`.
2. Update `snoozedUntil` in Firestore.
3. Deactivate (cancel old PendingIntents, exit urgent, dismiss notification).
4. Schedule single PendingIntent at snooze end time with highest-priority enabled stage type (`AlarmUtils.determineAlarmTypeForSnooze`).
5. Emit `AlarmUpdateEvent`.

**Recurrence**: N/A (alarm stays PENDING, no advancement).

### 7. Delete

**Trigger**: user deletes alarm, or undo system removes a recreated alarm.
**Precondition**: alarm exists (any status).
**State change**: any → deleted (removed from Firestore).
**Side effects**:
1. Deactivate.
2. Delete alarm document from Firestore.

**Recurrence**: no advancement. If this was a recurring instance, the template's `currentAlarmId` becomes stale — bootstrap recovery (transition 15) will create a new instance on next app launch.

### 8. Reactivate

**Trigger**: user reactivates a completed or cancelled alarm from editor UI.
**Precondition**: alarm is `DONE` or `CANCELLED`.
**State change**: `DONE`/`CANCELLED` → `PENDING`.
**Side effects**:
1. Update status to `PENDING` in Firestore.
2. Fetch updated alarm.
3. Schedule PendingIntents via `AlarmScheduler.scheduleAlarm`.
4. Emit `AlarmUpdateEvent`.

**Recurrence**: N/A.

### 9. Update (due time/stages)

**Trigger**: user edits alarm due time or stage configuration.
**Precondition**: alarm is `PENDING`.
**State change**: `PENDING` → `PENDING` (fields changed).
**Side effects**:
1. Create updated alarm copy with new `dueTime` and/or `stages`.
2. Update alarm in Firestore.
3. Deactivate old schedule.
4. Schedule with new times/stages via `AlarmScheduler.scheduleAlarm`.
5. If recurring: update template's recurrence config and stages.
6. Emit `AlarmUpdateEvent`.

**Recurrence**: template updated if recurrence config changed.

### 10. Dismiss (close notification, keep alarm active)

**Trigger**: user taps "Dismiss" in AlarmActivity or swipes away notification.
**Precondition**: alarm is `PENDING`.
**State change**: `PENDING` → `PENDING` (no DB change).
**Side effects**:
1. Dismiss notification (`NotificationHelper.dismiss`).
2. Exit urgent state if active (`UrgentStateManager.exitUrgentState`).
3. Finish AlarmActivity if open.

**Note**: PendingIntents for later stages remain scheduled. Alarm is still PENDING and will trigger again at next stage time (if any).

### 11. Fixed next instance

**Trigger**: FIXED recurring alarm's PendingIntent fires → `RecurrenceScheduler.onFixedInstanceTriggered`.
**Precondition**: recurring template is `ACTIVE`, current alarm is `PENDING`.
**State change**: template's `currentAlarmId` updated to new instance. New `PENDING` alarm created.
**Side effects**:
1. Guard: check `currentAlarmId` — if it already differs from triggered alarm, skip (dedup for multi-stage).
2. Compute next occurrence via `RRuleParser.nextOccurrence`.
3. Create new `Alarm` from template.
4. Update template's `currentAlarmId`.
5. Schedule new instance.
6. Clean up orphaned PENDING instances of same template.
7. Emit `AlarmUpdateEvent`.

**End condition**: if `hasReachedEnd` → end template (transition 14) instead.

### 12. Relative next instance (on completion)

**Trigger**: `RecurrenceScheduler.onInstanceCompleted` after markDone on a RELATIVE recurring instance.
**Precondition**: recurring template is `ACTIVE`, end condition not met.
**State change**: new `PENDING` alarm created. Template `completionCount` incremented, `lastCompletionDate` set.
**Side effects**:
1. Record completion on template.
2. Check end condition → if met, end template (transition 14), stop.
3. Compute next base time: `lastCompletionDate + relativeIntervalMs`.
4. Create new `Alarm` from template.
5. Update template's `currentAlarmId`.
6. Schedule new instance.
7. Clean up orphaned instances.
8. Emit `AlarmUpdateEvent`.

### 13. Relative next instance (on cancellation)

**Trigger**: `RecurrenceScheduler.onInstanceCancelled` after markCancelled on a RELATIVE recurring instance.
**Precondition**: recurring template is `ACTIVE`.
**State change**: new `PENDING` alarm created. Template `completionCount` NOT incremented.
**Side effects**:
1. Compute next base time: `lastCompletionDate` (or `createdAt` if never completed) `+ relativeIntervalMs`.
2. Create new `Alarm` from template.
3. Update template's `currentAlarmId`.
4. Schedule new instance.
5. Clean up orphaned instances.
6. Emit `AlarmUpdateEvent`.

**Note**: cancellation does not count toward `repeatCount` end condition.

### 14. Recurring end

**Trigger**: end condition met (`endDate` passed or `completionCount >= repeatCount`), or user manually ends.
**Precondition**: recurring template is `ACTIVE` or `PAUSED`.
**State change**: `ACTIVE`/`PAUSED` → `ENDED`.
**Side effects**:
1. Set template status to `ENDED` in Firestore.
2. No new instances created.

**Note**: existing PENDING instance (if any) remains active until user completes/cancels it.

### 15. Boot/reschedule

**Trigger**: device boot, app launch, or `RecurrenceScheduler.bootstrapRecurringAlarms`.
**Precondition**: alarm is `PENDING` (PendingIntents lost due to reboot/process death).
**State change**: `PENDING` → `PENDING` (PendingIntents restored).
**Side effects**:
1. For each `ACTIVE` recurring template:
   a. If `currentAlarmId` instance is `PENDING`: reschedule it via `AlarmScheduler.scheduleAlarm`.
   b. If `currentAlarmId` instance is `DONE`/`CANCELLED` (or missing): create next instance (transitions 11–13 logic).
2. For each non-recurring `PENDING` alarm: reschedule via `AlarmScheduler.scheduleAlarm`.

### 16. Recreate (redo)

**Trigger**: undo system recreates a previously deleted alarm.
**Precondition**: alarm was deleted, redo is invoked.
**State change**: ∅ → `PENDING`.
**Side effects**:
1. Create `Alarm` in Firestore with status `PENDING` (same fields as original).
2. Schedule PendingIntents via `AlarmScheduler.scheduleAlarm`.
3. Emit `AlarmUpdateEvent`.

**Recurrence**: N/A (recurring alarm redo is not supported — only single instances).
