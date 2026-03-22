import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useNotes } from '@/hooks/useNotes'
import {
  ADD_NOTE, DELETE_NOTE, RESTORE_NOTE, SECTION_DELETED_NOTES,
  NO_NOTES_FOUND, EMPTY_NOTE, REFRESH, LOADING, NOTE_MENU,
} from '@/strings'
import styles from './NoteListScreen.module.css'

export function NoteListScreen() {
  const {
    activeNotes,
    deletedNotes,
    loading,
    error,
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
          <button className={styles.refreshButton} onClick={refresh}>
            {REFRESH}
          </button>
        </div>
      </div>

      <main className={styles.main}>
        {loading && <p className={styles.status}>{LOADING}</p>}
        {error && <p className={styles.error}>{error}</p>}
        {!loading && activeNotes.length === 0 && deletedNotes.length === 0 && (
          <p className={styles.status}>{NO_NOTES_FOUND}</p>
        )}

        <ul className={styles.noteList}>
          {activeNotes.map((note) => (
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
              <NoteItemMenu
                onAction={() => deleteNote(note.id)}
                actionLabel={DELETE_NOTE}
                actionIcon={'\u{1F5D1}'}
                isDanger
              />
            </li>
          ))}
        </ul>

        {deletedNotes.length > 0 && (
          <>
            <h3 className={styles.deletedHeader}>{SECTION_DELETED_NOTES}</h3>
            <ul className={styles.noteList}>
              {deletedNotes.map((note) => (
                <li key={note.id} className={`${styles.noteItem} ${styles.deletedItem}`}>
                  <button
                    className={styles.noteButton}
                    onClick={() => navigate(`/note/${note.id}`)}
                  >
                    <span className={styles.noteContent}>
                      {note.content || EMPTY_NOTE}
                    </span>
                    <span className={styles.noteDate}>
                      {formatDate(note.updatedAt)}
                    </span>
                  </button>
                  <NoteItemMenu
                    onAction={() => undeleteNote(note.id)}
                    actionLabel={RESTORE_NOTE}
                    actionIcon={'\u21A9'}
                  />
                </li>
              ))}
            </ul>
          </>
        )}
      </main>
    </div>
  )
}

function NoteItemMenu({
  onAction,
  actionLabel,
  actionIcon,
  isDanger = false,
}: {
  onAction: () => void
  actionLabel: string
  actionIcon: string
  isDanger?: boolean
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  return (
    <div className={styles.menuContainer} ref={ref}>
      <button
        className={styles.menuTrigger}
        onClick={() => setOpen((prev) => !prev)}
        title={NOTE_MENU}
      >
        ⋮
      </button>
      {open && (
        <div className={styles.menu}>
          <button
            className={`${styles.menuItem} ${isDanger ? styles.menuItemDanger : ''}`}
            onClick={() => { setOpen(false); onAction() }}
          >
            <span className={styles.menuIcon}>{actionIcon}</span>
            {actionLabel}
          </button>
        </div>
      )}
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
