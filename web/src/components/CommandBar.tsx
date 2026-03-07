import type { EditorController } from '@/editor/EditorController'
import styles from './CommandBar.module.css'

interface CommandBarProps {
  controller: EditorController
  onSave: () => void
  dirty: boolean
  saving: boolean
}

export function CommandBar({ controller, onSave, dirty, saving }: CommandBarProps) {
  return (
    <div className={styles.bar}>
      <div className={styles.group}>
        <button
          className={styles.button}
          onClick={() => controller.toggleBullet()}
          title="Toggle bullet (•)"
        >
          •
        </button>
        <button
          className={styles.button}
          onClick={() => controller.toggleCheckbox()}
          title="Toggle checkbox (☐/☑)"
        >
          ☐
        </button>
      </div>

      <div className={styles.group}>
        <button
          className={styles.button}
          onClick={() => controller.indent()}
          title="Indent (Tab)"
        >
          →
        </button>
        <button
          className={styles.button}
          onClick={() => controller.unindent()}
          title="Unindent (Shift+Tab)"
        >
          ←
        </button>
      </div>

      <div className={styles.group}>
        <button
          className={styles.button}
          onClick={() => controller.moveUp()}
          title="Move up"
          disabled={!controller.getMoveUpState().isEnabled}
        >
          ↑
        </button>
        <button
          className={styles.button}
          onClick={() => controller.moveDown()}
          title="Move down"
          disabled={!controller.getMoveDownState().isEnabled}
        >
          ↓
        </button>
      </div>

      <div className={styles.group}>
        <button
          className={styles.button}
          onClick={() => controller.undo()}
          title="Undo (Ctrl+Z)"
          disabled={!controller.canUndo}
        >
          ↩
        </button>
        <button
          className={styles.button}
          onClick={() => controller.redo()}
          title="Redo (Ctrl+Y)"
          disabled={!controller.canRedo}
        >
          ↪
        </button>
      </div>

      <div className={styles.spacer} />

      <button
        className={`${styles.button} ${styles.saveButton}`}
        onClick={onSave}
        disabled={!dirty || saving}
        title="Save (Ctrl+S)"
      >
        {saving ? 'Saving...' : dirty ? 'Save' : 'Saved'}
      </button>
    </div>
  )
}
