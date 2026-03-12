import { useNavigate } from 'react-router-dom'
import { useNotes } from '@/hooks/useNotes'
import {
  ADD_NOTE, DELETE_NOTE, RESTORE_NOTE, SHOW_DELETED,
  NO_NOTES_FOUND, NO_DELETED_NOTES, EMPTY_NOTE, REFRESH, LOADING,
} from '@/strings'
import styles from './NoteListScreen.module.css'

export function NoteListScreen() {
  const {
    notes,
    loading,
    error,
    showDeleted,
    setShowDeleted,
    createNote,
    deleteNote,
    undeleteNote,
    refresh,
  } = useNotes()
  const navigate = useNavigate()

  const handleCreateNote = async () => {
    const id = await createNote()
    navigate(`/note/${id}`)
  }

  return (
    <div className={styles.container}>
      <div className={styles.toolbar}>
        <button className={styles.createButton} onClick={handleCreateNote}>
          + {ADD_NOTE}
        </button>
        <div className={styles.toolbarRight}>
          <label className={styles.toggleLabel}>
            <input
              type="checkbox"
              checked={showDeleted}
              onChange={(e) => setShowDeleted(e.target.checked)}
            />
            {SHOW_DELETED}
          </label>
          <button className={styles.refreshButton} onClick={refresh}>
            {REFRESH}
          </button>
        </div>
      </div>

      <main className={styles.main}>
        {loading && <p className={styles.status}>{LOADING}</p>}
        {error && <p className={styles.error}>{error}</p>}
        {!loading && notes.length === 0 && (
          <p className={styles.status}>
            {showDeleted ? NO_DELETED_NOTES : NO_NOTES_FOUND}
          </p>
        )}

        <ul className={styles.noteList}>
          {notes.map((note) => (
            <li key={note.id} className={styles.noteItem}>
              <button
                className={styles.noteButton}
                onClick={() => navigate(`/note/${note.id}`)}
              >
                <span className={styles.noteContent}>
                  {note.content || EMPTY_NOTE}
                </span>
                <span className={styles.noteDate}>
                  {formatDate(note.lastAccessedAt ?? note.updatedAt)}
                </span>
              </button>
              {showDeleted ? (
                <button
                  className={styles.actionButton}
                  onClick={() => undeleteNote(note.id)}
                  title={RESTORE_NOTE}
                >
                  {RESTORE_NOTE}
                </button>
              ) : (
                <button
                  className={styles.actionButton}
                  onClick={() => deleteNote(note.id)}
                  title={DELETE_NOTE}
                >
                  {DELETE_NOTE}
                </button>
              )}
            </li>
          ))}
        </ul>
      </main>
    </div>
  )
}

function formatDate(ts: { toDate(): Date } | null): string {
  if (!ts) return ''
  const date = ts.toDate()
  return date.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}
