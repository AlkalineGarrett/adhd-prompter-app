import { useParams, useNavigate } from 'react-router-dom'
import { useEffect, useCallback } from 'react'
import { useEditor } from '@/hooks/useEditor'
import { CommandBar } from '@/components/CommandBar'
import { EditorLine } from '@/components/EditorLine'
import { RecentTabsBar, addOrUpdateTab } from '@/components/RecentTabsBar'
import styles from './NoteEditorScreen.module.css'

export function NoteEditorScreen() {
  const { noteId } = useParams<{ noteId: string }>()
  const navigate = useNavigate()
  const { controller, editorState, loading, saving, error, dirty, save } = useEditor(noteId)

  // Update recent tab when note loads
  useEffect(() => {
    if (!noteId || loading) return
    const firstLine = editorState.lines[0]?.text ?? ''
    void addOrUpdateTab(noteId, firstLine || '(empty)')
  }, [noteId, loading, editorState.lines])

  // Ctrl+S to save
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 's') {
        e.preventDefault()
        void save()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [save])

  // Warn on unsaved changes
  useEffect(() => {
    if (!dirty) return
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault()
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [dirty])

  const handleBack = useCallback(() => {
    if (dirty) {
      if (confirm('You have unsaved changes. Discard them?')) {
        navigate('/')
      }
    } else {
      navigate('/')
    }
  }, [dirty, navigate])

  if (loading) {
    return <div className="loading">Loading note...</div>
  }

  if (error) {
    return (
      <div style={{ maxWidth: 800, margin: '0 auto', padding: '1rem' }}>
        <button onClick={handleBack}>Back</button>
        <p style={{ color: '#d32f2f' }}>{error}</p>
      </div>
    )
  }

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <button className={styles.backButton} onClick={handleBack}>
          ← Back
        </button>
      </header>

      <RecentTabsBar />

      <CommandBar
        controller={controller}
        onSave={save}
        dirty={dirty}
        saving={saving}
      />

      <div className={styles.editor}>
        {editorState.lines.map((_, index) => (
          <EditorLine
            key={index}
            lineIndex={index}
            controller={controller}
            editorState={editorState}
          />
        ))}
      </div>
    </div>
  )
}
