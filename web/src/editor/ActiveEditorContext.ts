import { createContext, useContext, type MutableRefObject } from 'react'
import type { EditorController } from './EditorController'
import type { EditorState } from './EditorState'
import type { InlineEditSession } from './InlineEditSession'

export interface ActiveEditorContextValue {
  /** The controller that command bar buttons should route to. */
  activeController: EditorController
  /** The state that command bar buttons should read from. */
  activeState: EditorState
  /** The currently active inline edit session, if any. */
  activeSession: InlineEditSession | null
  /** Called by a ViewNoteSection when it gains focus. */
  activateSession: (session: InlineEditSession) => void
  /** Called when focus leaves a view section. Returns the old session for saving.
   *  If expectedSession is provided, only deactivates if it matches the current active session
   *  (prevents a blurring section from deactivating a newly-activated sibling). */
  deactivateSession: (expectedSession?: InlineEditSession) => InlineEditSession | null
  /** Notify that the active session's state changed (triggers CommandBar re-render). */
  notifyActiveChange: () => void
  /** Ref populated by ViewDirectiveRenderer with its save function (manages saving UI state).
   *  Ctrl+S reads this to route through the same path as the Save button. */
  viewSaveRef: MutableRefObject<(() => Promise<void>) | null>
}

export const ActiveEditorContext = createContext<ActiveEditorContextValue | null>(null)

export function useActiveEditor(): ActiveEditorContextValue {
  const ctx = useContext(ActiveEditorContext)
  if (!ctx) throw new Error('useActiveEditor must be used within an ActiveEditorContext.Provider')
  return ctx
}
