import { useEffect, useState, useCallback } from 'react'
import { collection, query, where, getDocs } from 'firebase/firestore'
import { db, auth } from '@/firebase/config'
import { type Note, noteFromFirestore } from '@/data/Note'
import { NoteRepository } from '@/data/NoteRepository'

const repo = new NoteRepository(db, auth)

/** Max gap between consecutive deletion timestamps to be considered the same batch. */
const BATCH_WINDOW_MS = 5000

interface DeletionBatch {
  notes: Note[]
  deletedAt: Date
}

function sortByCreation(notes: Note[]): Note[] {
  return [...notes].sort((a, b) => (a.createdAt?.toMillis() ?? 0) - (b.createdAt?.toMillis() ?? 0))
}

/** Cluster notes into batches where consecutive timestamps are within BATCH_WINDOW_MS. */
function clusterIntoBatches(notes: Note[], minSize: number): DeletionBatch[] {
  if (notes.length === 0) return []

  // Sort by updatedAt ascending
  const sorted = [...notes].sort((a, b) => {
    return (a.updatedAt?.toMillis() ?? 0) - (b.updatedAt?.toMillis() ?? 0)
  })

  const batches: DeletionBatch[] = []
  let current: Note[] = [sorted[0]!]

  for (let i = 1; i < sorted.length; i++) {
    const prev = sorted[i - 1]!.updatedAt?.toMillis() ?? 0
    const curr = sorted[i]!.updatedAt?.toMillis() ?? 0
    if (curr - prev <= BATCH_WINDOW_MS) {
      current.push(sorted[i]!)
    } else {
      if (current.length >= minSize) {
        batches.push({
          notes: sortByCreation(current),
          deletedAt: current[0]!.updatedAt?.toDate() ?? new Date(),
        })
      }
      current = [sorted[i]!]
    }
  }
  if (current.length >= minSize) {
    batches.push({
      notes: sortByCreation(current),
      deletedAt: current[0]!.updatedAt?.toDate() ?? new Date(),
    })
  }

  // Most recent batches first
  batches.reverse()
  return batches
}

export function RecoverScreen() {
  const [batches, setBatches] = useState<DeletionBatch[]>([])
  const [loading, setLoading] = useState(true)
  const [restoringBatch, setRestoringBatch] = useState<number | null>(null)

  useEffect(() => {
    void loadDeletedNotes()
  }, [])

  async function loadDeletedNotes() {
    setLoading(true)
    const userId = auth.currentUser?.uid
    if (!userId) return

    const notesRef = collection(db, 'notes')
    const q = query(notesRef, where('userId', '==', userId), where('state', '==', 'deleted'))
    const snapshot = await getDocs(q)
    const allDeleted = snapshot.docs.map(d => noteFromFirestore(d.id, d.data()))

    setBatches(clusterIntoBatches(allDeleted, 3))
    setLoading(false)
  }

  const handleRestoreBatch = useCallback(async (batchIndex: number) => {
    const batch = batches[batchIndex]
    if (!batch) return
    setRestoringBatch(batchIndex)
    try {
      await Promise.all(batch.notes.map(n => repo.undeleteNote(n.id)))
      setBatches(prev => prev.filter((_, i) => i !== batchIndex))
    } catch (e) {
      console.error('Restore failed:', e)
      alert('Restore failed: ' + (e instanceof Error ? e.message : String(e)))
    } finally {
      setRestoringBatch(null)
    }
  }, [batches])

  if (loading) {
    return <div style={{ padding: '2rem' }}>Loading deleted notes...</div>
  }

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '1rem' }}>
      <h2 style={{ marginBottom: '0.5rem' }}>Batch-Deleted Notes</h2>
      <p style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)', marginBottom: '1rem' }}>
        Showing groups of 3+ notes deleted within {BATCH_WINDOW_MS / 1000}s of each other (likely overwrites).
      </p>
      {batches.length === 0 && <p>No batch deletions found.</p>}
      {batches.map((batch, batchIndex) => (
        <div
          key={batchIndex}
          style={{
            border: '1px solid var(--color-border)',
            borderRadius: 6,
            padding: '0.75rem',
            marginBottom: '1rem',
            background: 'var(--color-surface-alt)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <div style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)' }}>
              {batch.notes.length} notes &middot; deleted {batch.deletedAt.toLocaleString()}
            </div>
            <button
              onClick={() => void handleRestoreBatch(batchIndex)}
              disabled={restoringBatch === batchIndex}
              style={{
                padding: '0.4rem 0.8rem',
                background: 'var(--color-success)',
                color: 'white',
                border: 'none',
                borderRadius: 4,
                cursor: 'pointer',
              }}
            >
              {restoringBatch === batchIndex ? 'Restoring...' : 'Restore All'}
            </button>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.15rem' }}>
            {batch.notes.map(n => (
              <div
                key={n.id}
                style={{
                  fontSize: '0.9rem',
                  paddingLeft: n.parentNoteId ? '1rem' : 0,
                  borderLeft: n.parentNoteId ? '2px solid var(--color-border)' : 'none',
                }}
              >
                {n.content || '(empty)'}
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
