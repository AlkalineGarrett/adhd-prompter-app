import type { DslValue, NoteVal } from './DslValue'
import { noteVal, toDisplayString } from './DslValue'
import type { Arguments } from './Arguments'
import type { Environment } from './Environment'
import { ExecutionException } from './ExecutionException'
import { MutationType } from './NoteMutation'
import { getUp } from './NotePropertyHandler'

/**
 * Handles method calls for NoteVal.
 */
export async function callNoteMethod(
  nv: NoteVal,
  methodName: string,
  args: Arguments,
  env: Environment,
  position: number,
): Promise<DslValue> {
  switch (methodName) {
    case 'up': return handleUp(nv, args, position, env)
    case 'append': return handleAppend(nv, args, env, position)
    default: throw new ExecutionException(`Unknown method '${methodName}' on note`, position)
  }
}

function handleUp(nv: NoteVal, args: Arguments, position: number, env: Environment): DslValue {
  const levels = args.positional.length === 0
    ? 1
    : (() => {
        const arg = args.require(0, 'levels')
        if (arg.kind !== 'NumberVal') throw new ExecutionException("'up' expects a number argument", position)
        return Math.floor(arg.value)
      })()
  return getUp(nv.note, levels, env)
}

async function handleAppend(nv: NoteVal, args: Arguments, env: Environment, position: number): Promise<DslValue> {
  const text = args.require(0, 'text')
  const textStr = text.kind === 'StringVal' ? text.value : toDisplayString(text)

  const ops = env.getNoteOperations()
  if (!ops) throw new ExecutionException('Cannot append to note: note operations not available', position)

  const updatedNote = await ops.appendToNote(nv.note.id, textStr)
  env.registerMutation({
    noteId: nv.note.id,
    updatedNote,
    mutationType: MutationType.CONTENT_APPENDED,
    appendedText: textStr,
  })
  return noteVal(updatedNote)
}
