# Mindl Implementation Decisions

Clarifications made during implementation planning.

---

## Architecture

### Parser Approach
**Decision:** Hand-rolled recursive descent parser

- More code to maintain but better error messages
- No external dependency (vs. ANTLR)
- More portable and easier to customize

### Package Location
**Decision:** New top-level package `dsl/`

```
app/src/main/java/org/alkaline/taskbrain/dsl/
```

Clear separation from existing data/ui layers.

---

## Storage

### Directive Result Cache
**Decision:** Firestore subcollection under each note

```
notes/{noteId}/directiveResults/{directiveHash}
```

Fields:
- `result` - Cached execution result
- `executedAt` - Timestamp of last execution
- `error` - Error message if execution failed
- `collapsed` - UI state (collapsed/expanded)

Rationale:
- Results naturally scoped to parent note
- No document size concerns (each result is its own doc)
- Collection group queries support global views (schedule view, error navigation)
- Write efficiency: updating one result doesn't touch the note document

Note: Requires explicit cleanup when note is deleted (Cloud Function or batch delete in app).

---

## Scheduling

### Backend Architecture
**Decision:** Firebase Cloud Functions (primary) + WorkManager (fallback)

- Primary: Cloud Firestore triggers + Cloud Scheduler for reliability
- Fallback: WorkManager on device for offline scenarios

---

## UI

### Directive Display
**Decision:** Inline chips

- Directive shows as compact chip in note text
- Tap to expand and see/edit the directive source
- Result displays below the chip

### View Editing
**Decision:** Editable inline

- Content from `[view ...]` directives is editable in place
- Changes propagate back to source notes

### View Sync Timing
**Decision:** On save

- Edits to viewed content sync to source notes when the parent note is saved
- Not real-time (avoids complexity of keystroke-level sync)
- Not explicit action (seamless user experience)

---

## Implementation Priority

**Decision:** Follow spec order (Milestones 1-13 sequentially)

This hits both sprint targets along the way:
- Target 2 (Refresh View) at Milestone 10
- Target 1 (Schedule) at Milestone 13
