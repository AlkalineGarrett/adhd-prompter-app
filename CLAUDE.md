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

## BANNED: BasicTextField

**DO NOT USE `BasicTextField` in this codebase.**

### Why It's Banned

1. **Single-field selection model** - BasicTextField's selection is confined to one field. Our editor needs multi-line selection spanning multiple fields. This is a fundamental architectural mismatch.

2. **Undisableable UI behaviors** - Magnifier bubble, selection handles, and gesture handling cannot be fully disabled, even with `NoOpTextToolbar`, transparent `TextSelectionColors`, and consuming pointer events.

### What To Use Instead

`ImeConnection` modifier + `BasicText` (see `ui/currentnote/ImeConnection.kt`):

```kotlin
// CORRECT
Box(modifier = Modifier.focusable().imeConnection(state = imeState)) {
    BasicText(text = text) // Display only
}

// WRONG - DO NOT USE
BasicTextField(state = textFieldState)
```

### Mining BasicTextField for Parts

Our `ImeConnection` was built by studying BasicTextField's internals. For future enhancements, mine the source:
- [BasicTextField.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/BasicTextField.kt)
- [TextFieldDecoratorModifier.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/input/internal/TextFieldDecoratorModifier.kt)
- [StatelessInputConnection.android.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/text/input/internal/StatelessInputConnection.android.kt)

See `docs/compose-ime-learnings.md` for full explanation and more source links.

## Development guidance

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
