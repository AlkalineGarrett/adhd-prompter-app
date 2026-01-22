# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TaskBrain is a native Android app (Kotlin) for ADHD task management with Firebase backend and Gemini AI integration.

## Build Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew assembleRelease      # Build release APK
./gradlew test                 # Run unit tests
./gradlew connectedAndroidTest # Run instrumentation tests
./gradlew clean                # Clean build
```

Run a single test class:
```bash
./gradlew test --tests "org.alkaline.taskbrain.data.NoteLineTrackerTest"
```

## Architecture

**Pattern:** MVVM with Jetpack Compose UI

**Key directories:**
- `app/src/main/java/org/alkaline/taskbrain/data/` - Data layer (models, repository, utilities)
- `app/src/main/java/org/alkaline/taskbrain/ui/` - UI layer (screens, viewmodels, components)
- `app/src/test/` - Unit tests using JUnit 4 + MockK

**Data flow:** Compose Screens → ViewModels → NoteRepository → Firebase Firestore

## Core Components

**NoteRepository** (`data/NoteRepository.kt`): All Firestore operations. Uses transactions for atomic updates and Result<T> for error handling.

**NoteLineTracker** (`data/NoteLineTracker.kt`): Maintains stable note IDs across edits. Two-phase matching: exact content match first, then positional fallback. Critical for preserving parent-child relationships when lines are reordered.

**PrompterAgent** (`data/PrompterAgent.kt`): Wraps Gemini AI (gemini-2.5-flash) for note enhancement.

## Important Patterns

**Trailing empty line handling:**
- UI always shows an empty line at the end (for typing)
- DB never stores trailing empty lines (dropped on save)
- Loading a note appends an empty line for display

**Note structure in Firestore:**
- First line = parent note content
- Additional lines = contained child notes (via `containedNotes` array)
- Deletion is soft (state = "deleted")

**Whitespace semantics:**
- Empty string ("") = spacer line
- Whitespace-only ("   ") = actual content (creates child note)

## Testing

Tests use MockK for mocking and `runTest` for coroutines. Key test files:
- `NoteLineTrackerTest.kt` - Line ID preservation logic
- `NoteRepositoryTest.kt` - Repository operations with mocked Firestore

## Dependencies

Versions managed in `gradle/libs.versions.toml`. Key stack:
- Kotlin 2.1.0, Compose, Material 3
- Firebase (Auth, Firestore, AI)
- Google Sign-In with Credential Manager

## Release

See `TODO_RELEASE.md` for signing configuration. Requires environment variables: `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Development guidance

### Project requirements

Consult the following files to understand project requirements:

- .md files under docs/
- files name requirements.md in the source tree

### Undo/Redo Considerations

**Architecture**: All discrete editing operations must flow through `EditorController`, which handles undo boundary management via an operation-based system:

1. **EditorController** is the single channel for state modifications (mutation methods on `EditorState` are `internal`)
2. **OperationType enum** classifies operations: `COMMAND_BULLET`, `COMMAND_CHECKBOX`, `COMMAND_INDENT`, `PASTE`, `CUT`, `DELETE_SELECTION`, `CHECKBOX_TOGGLE`, `ALARM_SYMBOL`
3. **Operation executor** (`executeOperation`) wraps operations with proper pre/post undo handling

**When adding new operations that modify editor content:**
- Add a new `OperationType` if it has distinct undo semantics
- Add a method to `EditorController` that wraps the operation with `executeOperation()`
- Call the controller method from UI code (never call `EditorState` mutation methods directly)

**Key questions to ask the developer:**
- Should this operation create its own undo boundary, or be grouped with adjacent edits?
- For command bar buttons: should consecutive presses be grouped (like indent) or separate (like bullet)?
- If the operation creates side effects (like alarms), should those be undoable?
- Does the operation need special handling for redo (e.g., recreating external resources)?

See `ui/currentnote/requirements.md` for full undo/redo specification and implementation details.

### Error Handling and User Feedback

The app should generally inform users when problems occur rather than silently failing or recovering:
- **Show warning dialogs** when operations fail or produce unexpected results, even if the app can recover automatically
- **Explain what happened** in user-friendly terms, including what automatic recovery was attempted
- **Indicate potential inconsistency** if recovery may have left things in a partial state (e.g., "Consider saving and reloading")
- Use `AlertDialog` for warnings; see existing patterns in `CurrentNoteScreen.kt` (e.g., `redoRollbackWarning`, `schedulingWarning`)

This principle applies especially to:
- Failed async operations (Firebase, alarms, etc.)
- Automatic rollbacks or cleanup after failures
- Permission issues that affect functionality

### Refactoring

- Do the following when refactoring:
  - Look to make the code and design "elegant"
  - Consolidate repetition of code
  - Consolidate repetition of patterns based on the same concept
    - If the same groupings of parameters are passed around in multiple places, encapsulate them
  - Break apart long functions (anything longer than 50 lines is suspicious; the more indented, the more it needs to be broken apart)
  - Break apart long files
  - Avoid deeply nested code (anything 4 or more levels deep is suspicious, especially the more lines of code it is)
  - Make sure each unit (file, function, class) has a clear responsibility and not multiple, and at a single level of granularity (think: don't combine paragraph work with character work)
  - Define constants or config instead of hard-coded numbers
  - Move logic out of display classes
  - Look for ways that different use cases have slightly different logic, when there is no inherent reason for them to be different, and merge them
  - Decouple units by using callbacks, etc so that classes refer to each other directly less often
  - Look for places with too many edge cases and come up with more robust, general logic instead
