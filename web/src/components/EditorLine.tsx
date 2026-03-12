import { useRef, useEffect, useCallback, type KeyboardEvent, type ChangeEvent, type MouseEvent } from 'react'
import type { EditorController } from '@/editor/EditorController'
import type { EditorState } from '@/editor/EditorState'
import type { DirectiveResult } from '@/dsl/directives/DirectiveResult'
import { hasCheckbox } from '@/editor/LinePrefixes'
import { hasDirectives } from '@/dsl/directives/DirectiveSegmenter'
import { DirectiveLineContent } from './DirectiveLineContent'
import { getCharIndexAtX, getComputedFont, getWordBoundsAt } from '@/editor/TextMeasure'
import styles from './EditorLine.module.css'

interface EditorLineProps {
  lineIndex: number
  controller: EditorController
  editorState: EditorState
  directiveResults?: Map<string, DirectiveResult>
  onDirectiveEdit?: (key: string, newSourceText: string) => void
  onDirectiveRefresh?: (key: string, sourceText: string) => void
  onButtonClick?: (key: string) => void
  onViewNoteClick?: (noteId: string) => void
  onDragStart?: (anchorGlobalOffset: number) => void
}

export function EditorLine({
  lineIndex,
  controller,
  editorState,
  directiveResults,
  onDirectiveEdit,
  onDirectiveRefresh,
  onButtonClick,
  onViewNoteClick,
  onDragStart,
}: EditorLineProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const line = editorState.lines[lineIndex]
  if (!line) return null

  const isFocused = lineIndex === editorState.focusedLineIndex
  const prefix = line.prefix
  const content = line.content
  const indentLevel = prefix.match(/^\t*/)?.[0].length ?? 0
  const displayPrefix = prefix.replace(/^\t+/, '')

  // Compute per-line selection range (offsets into line.text)
  const lineSelection = editorState.getLineSelection(lineIndex)
  // Convert to content-relative offsets (excluding prefix)
  const contentSelection = lineSelection
    ? [
        Math.max(0, lineSelection[0] - prefix.length),
        Math.min(content.length, lineSelection[1] - prefix.length),
      ] as [number, number]
    : null
  const hasContentSelection = contentSelection && contentSelection[0] < contentSelection[1]

  // Focus management — sets native input selection for focused line
  useEffect(() => {
    if (!isFocused || !inputRef.current) return
    if (document.activeElement !== inputRef.current) {
      inputRef.current.focus({ preventScroll: true })
    }
    if (hasContentSelection) {
      inputRef.current.setSelectionRange(contentSelection[0], contentSelection[1])
    } else {
      const cursor = line.contentCursorPosition
      inputRef.current.setSelectionRange(cursor, cursor)
    }
  }, [isFocused, editorState.stateVersion])

  const handleChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      // If there's a cross-line selection, ignore native onChange — typing is handled in handleKeyDown
      if (editorState.hasSelection) {
        // Revert the input to the expected content
        e.target.value = content
        return
      }
      const newContent = e.target.value
      const newCursor = e.target.selectionStart ?? newContent.length
      controller.updateLineContent(lineIndex, newContent, newCursor)
    },
    [controller, editorState, lineIndex, content],
  )

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLInputElement>) => {
      const input = e.currentTarget
      const cursor = input.selectionStart ?? 0
      const hasSel = editorState.hasSelection

      // Ctrl/Cmd shortcuts
      if (e.metaKey || e.ctrlKey) {
        if (e.key === 'z') {
          e.preventDefault()
          if (e.shiftKey) controller.redo()
          else controller.undo()
          return
        }
        if (e.key === 'y') {
          e.preventDefault()
          controller.redo()
          return
        }
        if (e.key === 'a') {
          e.preventDefault()
          editorState.selectAll()
          return
        }
        if (e.key === 'c' && hasSel) {
          e.preventDefault()
          controller.copySelection()
          return
        }
        if (e.key === 'x' && hasSel) {
          e.preventDefault()
          controller.cutSelection()
          return
        }
        if (e.key === 'v' && hasSel) {
          e.preventDefault()
          void navigator.clipboard.readText().then((text) => {
            controller.paste(text)
          })
          return
        }
        return
      }

      // Typing with cross-line selection: replace selection with typed character
      if (hasSel && e.key.length === 1) {
        e.preventDefault()
        controller.paste(e.key)
        return
      }

      // Backspace/Delete with cross-line selection
      if (hasSel && (e.key === 'Backspace' || e.key === 'Delete')) {
        e.preventDefault()
        controller.deleteSelectionWithUndo()
        return
      }

      // Enter with selection: delete selection then split
      if (hasSel && e.key === 'Enter') {
        e.preventDefault()
        controller.deleteSelectionWithUndo()
        controller.splitLine(editorState.focusedLineIndex)
        return
      }

      // Shift+Arrow: extend selection
      if (e.shiftKey) {
        const curGlobal = editorState.getCursorGlobalOffset()
        switch (e.key) {
          case 'ArrowLeft': {
            e.preventDefault()
            const target = Math.max(0, curGlobal - 1)
            editorState.extendSelectionTo(target)
            return
          }
          case 'ArrowRight': {
            e.preventDefault()
            const maxOffset = editorState.text.length
            const target = Math.min(maxOffset, curGlobal + 1)
            editorState.extendSelectionTo(target)
            return
          }
          case 'ArrowUp': {
            if (lineIndex > 0) {
              e.preventDefault()
              const prevLine = editorState.lines[lineIndex - 1]!
              const prevLineStart = editorState.getLineStartOffset(lineIndex - 1)
              const localPos = Math.min(line.cursorPosition, prevLine.text.length)
              editorState.extendSelectionTo(prevLineStart + localPos)
            }
            return
          }
          case 'ArrowDown': {
            if (lineIndex < editorState.lines.length - 1) {
              e.preventDefault()
              const nextLine = editorState.lines[lineIndex + 1]!
              const nextLineStart = editorState.getLineStartOffset(lineIndex + 1)
              const localPos = Math.min(line.cursorPosition, nextLine.text.length)
              editorState.extendSelectionTo(nextLineStart + localPos)
            }
            return
          }
        }
      }

      // Arrow keys collapse selection when no shift
      if (hasSel && (e.key === 'ArrowLeft' || e.key === 'ArrowRight' ||
                     e.key === 'ArrowUp' || e.key === 'ArrowDown')) {
        e.preventDefault()
        controller.clearSelection()
        return
      }

      switch (e.key) {
        case 'Enter':
          e.preventDefault()
          controller.splitLine(lineIndex)
          break

        case 'Backspace':
          if (cursor === 0 && input.selectionEnd === 0) {
            e.preventDefault()
            controller.deleteBackward(lineIndex)
          }
          break

        case 'Delete':
          if (cursor === content.length) {
            e.preventDefault()
            controller.deleteForward(lineIndex)
          }
          break

        case 'Tab':
          e.preventDefault()
          if (e.shiftKey) {
            controller.unindent()
          } else {
            controller.indent()
          }
          break

        case 'ArrowUp':
          if (lineIndex > 0) {
            e.preventDefault()
            controller.setCursor(lineIndex - 1, editorState.lines[lineIndex - 1]!.text.length)
          }
          break

        case 'ArrowDown':
          if (lineIndex < editorState.lines.length - 1) {
            e.preventDefault()
            controller.setCursor(lineIndex + 1, 0)
          }
          break
      }
    },
    [controller, editorState, lineIndex, content.length],
  )

  const getCharIndexFromEvent = useCallback(
    (e: MouseEvent<HTMLInputElement | HTMLDivElement>): number => {
      const target = e.currentTarget
      const rect = target.getBoundingClientRect()
      const paddingLeft = parseFloat(getComputedStyle(target).paddingLeft)
      const clickX = e.clientX - rect.left - paddingLeft
      const font = getComputedFont(target as HTMLElement)
      return getCharIndexAtX(content, clickX, font)
    },
    [content],
  )

  const handleMouseDown = useCallback(
    (e: MouseEvent<HTMLInputElement | HTMLDivElement>) => {
      // Prevent native input selection so we can handle cross-line drag
      e.preventDefault()

      const charIdx = getCharIndexFromEvent(e)
      const globalOffset = editorState.getLineStartOffset(lineIndex) + prefix.length + charIdx

      if (e.shiftKey) {
        editorState.extendSelectionTo(globalOffset)
      } else if (e.detail === 2) {
        // Double-click: select word
        const wordBounds = getWordBoundsAt(content, charIdx)
        const lineStart = editorState.getLineStartOffset(lineIndex) + prefix.length
        controller.setSelection(lineStart + wordBounds[0], lineStart + wordBounds[1])
      } else if (e.detail >= 3) {
        // Triple-click: select whole line
        const lineStart = editorState.getLineStartOffset(lineIndex)
        const lineEnd = lineStart + line.text.length
        controller.setSelection(lineStart, lineEnd)
      } else {
        // Single click: place cursor and start drag
        controller.setCursorFromGlobalOffset(globalOffset)
        onDragStart?.(globalOffset)
      }
    },
    [getCharIndexFromEvent, controller, editorState, lineIndex, prefix.length, content, line, onDragStart],
  )

  const handleFocus = useCallback(() => {
    if (lineIndex !== editorState.focusedLineIndex) {
      // Don't clear selection on focus — shift+click and drag need it
      if (!editorState.hasSelection) {
        controller.focusLine(lineIndex)
      }
    }
  }, [controller, editorState, lineIndex])

  const handleGutterClick = useCallback(() => {
    if (hasCheckbox(line.text)) {
      controller.toggleCheckboxOnLine(lineIndex)
    } else {
      controller.focusLine(lineIndex)
    }
  }, [controller, line.text, lineIndex])

  // Show directive chips for unfocused lines that contain directives
  const showDirectiveChips = !isFocused && directiveResults && hasDirectives(content)

  // All lines with cross-line selection use the overlay for consistent rendering
  const showSelectionOverlay = !!hasContentSelection

  return (
    <div
      className={`${styles.line} ${isFocused ? styles.focused : ''}`}
      style={{ paddingLeft: `${0.25 + indentLevel * 0.6}rem` }}
    >
      {displayPrefix ? (
        <div className={styles.gutter} onClick={handleGutterClick}>
          <span className={styles.prefix}>{displayPrefix}</span>
        </div>
      ) : null}
      {showDirectiveChips ? (
        <div className={styles.directiveContent} onClick={handleFocus}>
          <DirectiveLineContent
            content={content}
            lineIndex={lineIndex}
            results={directiveResults}
            onDirectiveEdit={onDirectiveEdit}
            onDirectiveRefresh={onDirectiveRefresh}
            onButtonClick={onButtonClick}
            onViewNoteClick={onViewNoteClick}
          />
        </div>
      ) : (
        <div className={styles.inputWrapper}>
          <input
            ref={inputRef}
            className={`${styles.input}${showSelectionOverlay ? ` ${styles.nativeSelectionHidden}` : ''}`}
            value={content}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            onMouseDown={handleMouseDown}
            onFocus={handleFocus}
            spellCheck={false}
            autoComplete="off"
          />
          {showSelectionOverlay && (
            <div className={styles.selectionOverlay} aria-hidden>
              <span className={styles.selectionInvisible}>{content.substring(0, contentSelection[0])}</span>
              <span className={styles.selectionHighlight}>
                {content.substring(contentSelection[0], contentSelection[1])}
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
