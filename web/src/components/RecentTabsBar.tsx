import { useEffect, useState, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { RecentTabsRepository, type RecentTab } from '@/data/RecentTabsRepository'
import { db, auth } from '@/firebase/config'
import styles from './RecentTabsBar.module.css'

const repo = new RecentTabsRepository(db, auth)

export function RecentTabsBar() {
  const [tabs, setTabs] = useState<RecentTab[]>([])
  const navigate = useNavigate()
  const { noteId: currentNoteId } = useParams<{ noteId: string }>()

  const loadTabs = useCallback(async () => {
    try {
      const openTabs = await repo.getOpenTabs()
      setTabs(openTabs)
    } catch {
      // silently fail
    }
  }, [])

  useEffect(() => {
    void loadTabs()
  }, [loadTabs, currentNoteId])

  const handleClose = useCallback(
    async (e: React.MouseEvent, noteId: string) => {
      e.stopPropagation()
      await repo.removeTab(noteId)
      const remainingTabs = tabs.filter((t) => t.noteId !== noteId)
      setTabs(remainingTabs)
      if (noteId === currentNoteId) {
        // Navigate to adjacent tab, or home if none remain
        const closedIndex = tabs.findIndex((t) => t.noteId === noteId)
        const nextTab = remainingTabs[closedIndex] ?? remainingTabs[closedIndex - 1]
        if (nextTab) {
          navigate(`/note/${nextTab.noteId}`)
        } else {
          navigate('/')
        }
      }
    },
    [currentNoteId, navigate, tabs],
  )

  if (tabs.length === 0) return null

  return (
    <div className={styles.bar}>
      {tabs.map((tab) => (
        <button
          key={tab.noteId}
          className={`${styles.tab} ${tab.noteId === currentNoteId ? styles.active : ''}`}
          onClick={() => navigate(`/note/${tab.noteId}`)}
        >
          <span className={styles.tabText}>
            {tab.displayText || '(empty)'}
          </span>
          <span
            className={styles.closeButton}
            onClick={(e) => handleClose(e, tab.noteId)}
          >
            ×
          </span>
        </button>
      ))}
    </div>
  )
}

/** Call this when opening/editing a note to update the tab. */
export async function addOrUpdateTab(noteId: string, displayText: string): Promise<void> {
  try {
    await repo.addOrUpdateTab(noteId, displayText)
  } catch {
    // silently fail
  }
}
