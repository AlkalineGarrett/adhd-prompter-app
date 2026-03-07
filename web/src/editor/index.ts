export { LineState, extractPrefix } from './LineState'
export * as LinePrefixes from './LinePrefixes'
export { EditorState } from './EditorState'
export { EditorController, OperationType, type MoveButtonState } from './EditorController'
export { UndoManager, CommandType, type UndoSnapshot } from './UndoManager'
export {
  type EditorSelection,
  SELECTION_NONE,
  hasSelection,
  selMin,
  selMax,
} from './EditorSelection'
