import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { useNotes } from '@/hooks/useNotes'
import styles from './NoteListScreen.module.css'

export function NoteListScreen() {
  const { user, signOut } = useAuth()
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
      <header className={styles.header}>
        <h1 className={styles.title}>TaskBrain</h1>
        <div className={styles.userInfo}>
          <span>{user?.displayName}</span>
          <button className={styles.signOutButton} onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>

      <div className={styles.toolbar}>
        <button className={styles.createButton} onClick={handleCreateNote}>
          + New Note
        </button>
        <div className={styles.toolbarRight}>
          <label className={styles.toggleLabel}>
            <input
              type="checkbox"
              checked={showDeleted}
              onChange={(e) => setShowDeleted(e.target.checked)}
            />
            Show deleted
          </label>
          <button className={styles.refreshButton} onClick={refresh}>
            Refresh
          </button>
        </div>
      </div>

      <main className={styles.main}>
        {loading && <p className={styles.status}>Loading...</p>}
        {error && <p className={styles.error}>{error}</p>}
        {!loading && notes.length === 0 && (
          <p className={styles.status}>
            {showDeleted ? 'No deleted notes' : 'No notes yet. Create one!'}
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
                  {note.content || '(empty)'}
                </span>
                <span className={styles.noteDate}>
                  {formatDate(note.lastAccessedAt ?? note.updatedAt)}
                </span>
              </button>
              {showDeleted ? (
                <button
                  className={styles.actionButton}
                  onClick={() => undeleteNote(note.id)}
                  title="Restore"
                >
                  Restore
                </button>
              ) : (
                <button
                  className={styles.actionButton}
                  onClick={() => deleteNote(note.id)}
                  title="Delete"
                >
                  Delete
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
