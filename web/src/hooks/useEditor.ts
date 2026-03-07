import { useCallback, useEffect, useRef, useState } from 'react'
import { EditorState } from '@/editor/EditorState'
import { EditorController } from '@/editor/EditorController'
import { UndoManager } from '@/editor/UndoManager'
import { LineState } from '@/editor/LineState'
import type { NoteLine } from '@/data/Note'
import { NoteRepository, matchLinesToIds } from '@/data/NoteRepository'
import { db, auth } from '@/firebase/config'

const repo = new NoteRepository(db, auth)

export function useEditor(noteId: string | undefined) {
  const [editorState] = useState(() => new EditorState())
  const [undoManager] = useState(() => new UndoManager())
  const [controller] = useState(() => new EditorController(editorState, undoManager))

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dirty, setDirty] = useState(false)

  // Track line IDs for Firestore mapping
  const trackedLinesRef = useRef<NoteLine[]>([])

  // Force re-render when editor state changes
  const [, setRenderVersion] = useState(0)

  useEffect(() => {
    editorState.onTextChange = () => {
      setDirty(true)
      setRenderVersion((v) => v + 1)
    }
    return () => {
      editorState.onTextChange = null
    }
  }, [editorState])

  // Load note
  useEffect(() => {
    if (!noteId) return

    let cancelled = false
    const loadNote = async () => {
      try {
        setLoading(true)
        setError(null)
        const lines = await repo.loadNoteWithChildren(noteId)

        if (cancelled) return

        // Populate editor state
        editorState.lines = lines.map((l) => new LineState(l.content))
        editorState.focusedLineIndex = 0
        editorState.clearSelection()
        editorState.requestFocusUpdate()

        // Track line IDs
        trackedLinesRef.current = lines

        // Set undo baseline
        controller.resetUndoHistory()
        undoManager.setBaseline(editorState.lines, editorState.focusedLineIndex)

        setDirty(false)

        // Update last accessed
        void repo.updateLastAccessed(noteId)
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Failed to load note')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void loadNote()
    return () => { cancelled = true }
  }, [noteId, editorState, controller, undoManager])

  // Save
  const save = useCallback(async () => {
    if (!noteId || !dirty) return

    try {
      setSaving(true)
      controller.commitUndoState(true)

      // Build tracked lines from current editor state + tracked IDs
      const currentLines = editorState.lines.map((l) => l.text)
      const existingTracked = trackedLinesRef.current

      // Re-match lines to IDs using the same algorithm as Android
      const newTracked = matchLinesToIds(
        noteId,
        existingTracked,
        currentLines,
      )

      const createdIds = await repo.saveNoteWithChildren(noteId, newTracked)

      // Update tracked lines with newly created IDs
      const updatedTracked = newTracked.map((line, index) => {
        const newId = createdIds.get(index)
        return newId ? { ...line, noteId: newId } : line
      })
      trackedLinesRef.current = updatedTracked

      setDirty(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save')
    } finally {
      setSaving(false)
    }
  }, [noteId, dirty, editorState, controller])

  return {
    controller,
    editorState,
    loading,
    saving,
    error,
    dirty,
    save,
  }
}
