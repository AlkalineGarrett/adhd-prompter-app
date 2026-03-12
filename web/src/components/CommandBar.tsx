import type { EditorController } from '@/editor/EditorController'
import {
  SAVE, SAVING, SAVED,
  COMMAND_TOGGLE_BULLET, COMMAND_TOGGLE_CHECKBOX,
  COMMAND_INDENT, COMMAND_UNINDENT,
  COMMAND_MOVE_UP, COMMAND_MOVE_DOWN,
} from '@/strings'
import styles from './CommandBar.module.css'

interface CommandBarProps {
  controller: EditorController
  onSave: () => void
  onUndo?: () => void
  onRedo?: () => void
  dirty: boolean
  saving: boolean
}

export function CommandBar({ controller, onSave, onUndo, onRedo, dirty, saving }: CommandBarProps) {
  return (
    <div className={styles.bar}>
      <div className={styles.group}>
        <button
          className={styles.button}
          onClick={() => controller.toggleBullet()}
          title={COMMAND_TOGGLE_BULLET}
        >
          •
        </button>
        <button
          className={styles.button}
          onClick={() => controller.toggleCheckbox()}
          title={COMMAND_TOGGLE_CHECKBOX}
        >
          ☐
        </button>
      </div>

      <div className={styles.group}>
        <button
          className={styles.button}
          onClick={() => controller.indent()}
          title={COMMAND_INDENT}
        >
          →
        </button>
        <button
          className={styles.button}
          onClick={() => controller.unindent()}
          title={COMMAND_UNINDENT}
        >
          ←
        </button>
      </div>

      <div className={styles.group}>
        <button
          className={`${styles.button} ${controller.getMoveUpState().isWarning ? styles.warning : ''}`}
          onClick={() => controller.moveUp()}
          title={COMMAND_MOVE_UP}
          disabled={!controller.getMoveUpState().isEnabled}
        >
          ↑
        </button>
        <button
          className={`${styles.button} ${controller.getMoveDownState().isWarning ? styles.warning : ''}`}
          onClick={() => controller.moveDown()}
          title={COMMAND_MOVE_DOWN}
          disabled={!controller.getMoveDownState().isEnabled}
        >
          ↓
        </button>
      </div>

      <div className={styles.group}>
        <button
          className={styles.button}
          onClick={onUndo ?? (() => controller.undo())}
          title="Undo (Ctrl+Z)"
          disabled={!controller.canUndo}
        >
          ↩
        </button>
        <button
          className={styles.button}
          onClick={onRedo ?? (() => controller.redo())}
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
        title={`${SAVE} (Ctrl+S)`}
      >
        {saving ? SAVING : dirty ? SAVE : SAVED}
      </button>
    </div>
  )
}
