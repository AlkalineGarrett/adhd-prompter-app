import { useState, useCallback, useEffect, useRef } from 'react'
import { NoteRepository } from '@/data/NoteRepository'
import { db, auth } from '@/firebase/config'
import {
  EDITING_NOTE, MODIFIED, SAVING, SAVE_AND_CLOSE, CANCEL, DISCARD_UNSAVED,
  ERROR_SAVE, ERROR_UNKNOWN,
} from '@/strings'
import styles from './InlineEditor.module.css'

const repo = new NoteRepository(db, auth)

interface InlineEditorProps {
  noteId: string
  initialContent: string
  onClose: () => void
  onSaved?: () => void
}

/**
 * Inline editor for editing a viewed note's content.
 * Saves on blur or explicit save, closes on Escape.
 */
export function InlineEditor({ noteId, initialContent, onClose, onSaved }: InlineEditorProps) {
  const [content, setContent] = useState(initialContent)
  const [saving, setSaving] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const isDirty = content !== initialContent

  useEffect(() => {
    textareaRef.current?.focus()
  }, [])

  const handleSave = useCallback(async () => {
    if (!isDirty) {
      onClose()
      return
    }
    setSaving(true)
    try {
      await repo.saveNoteWithFullContent(noteId, content)
      onSaved?.()
      onClose()
    } catch (e) {
      console.error('Failed to save inline edit:', e)
      alert(`${ERROR_SAVE}: ${e instanceof Error ? e.message : ERROR_UNKNOWN}`)
    } finally {
      setSaving(false)
    }
  }, [noteId, content, isDirty, onClose, onSaved])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        if (isDirty) {
          if (confirm(DISCARD_UNSAVED)) onClose()
        } else {
          onClose()
        }
      } else if ((e.metaKey || e.ctrlKey) && e.key === 's') {
        e.preventDefault()
        void handleSave()
      }
    },
    [isDirty, onClose, handleSave],
  )

  const handleBlur = useCallback(() => {
    void handleSave()
  }, [handleSave])

  return (
    <div className={styles.inlineEditor}>
      <div className={styles.header}>
        <span className={styles.label}>{EDITING_NOTE}</span>
        {isDirty && <span className={styles.dirtyIndicator}>{MODIFIED}</span>}
        <button className={styles.saveButton} onClick={() => void handleSave()} disabled={saving}>
          {saving ? SAVING : SAVE_AND_CLOSE}
        </button>
        <button className={styles.cancelButton} onClick={onClose}>
          {CANCEL}
        </button>
      </div>
      <textarea
        ref={textareaRef}
        className={styles.textarea}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={handleBlur}
        rows={Math.max(3, content.split('\n').length + 1)}
        spellCheck={false}
      />
    </div>
  )
}
