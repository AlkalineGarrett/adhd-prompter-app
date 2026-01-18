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
