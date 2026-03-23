package org.alkaline.taskbrain.ui.currentnote.gestures

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorGestureHandlerTest {

    // ==================== findWordBoundaries ====================

    @Test
    fun `findWordBoundaries finds word at start`() {
        val (start, end) = findWordBoundaries("hello world", 0)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries finds word at middle of word`() {
        val (start, end) = findWordBoundaries("hello world", 2)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries finds word at end of word`() {
        val (start, end) = findWordBoundaries("hello world", 5)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries finds second word`() {
        val (start, end) = findWordBoundaries("hello world", 8)
        assertEquals(6, start)
        assertEquals(11, end)
    }

    @Test
    fun `findWordBoundaries at space returns space boundaries`() {
        val (start, end) = findWordBoundaries("hello world", 5)
        // At position 5 (end of "hello"), should find "hello"
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries handles empty string`() {
        val (start, end) = findWordBoundaries("", 0)
        assertEquals(0, start)
        assertEquals(0, end)
    }

    @Test
    fun `findWordBoundaries clamps offset beyond length`() {
        val (start, end) = findWordBoundaries("hello", 10)
        // Offset clamped to 5 (end of text), finds "hello"
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries clamps negative offset`() {
        val (start, end) = findWordBoundaries("hello", -1)
        // Offset clamped to 0, finds "hello"
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries handles single word`() {
        val (start, end) = findWordBoundaries("hello", 2)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries handles multiple spaces`() {
        val (start, end) = findWordBoundaries("hello   world", 8)
        assertEquals(8, start)
        assertEquals(13, end)
    }

    @Test
    fun `findWordBoundaries handles tabs as whitespace`() {
        val (start, end) = findWordBoundaries("hello\tworld", 8)
        assertEquals(6, start)
        assertEquals(11, end)
    }

    @Test
    fun `findWordBoundaries handles newlines as whitespace`() {
        val (start, end) = findWordBoundaries("hello\nworld", 8)
        assertEquals(6, start)
        assertEquals(11, end)
    }

    @Test
    fun `findWordBoundaries excludes punctuation from word`() {
        // Standard behavior: punctuation is not part of word
        val (start, end) = findWordBoundaries("hello, world", 3)
        assertEquals(0, start)
        assertEquals(5, end) // "hello" only, comma excluded
    }

    @Test
    fun `findWordBoundaries at punctuation finds adjacent word`() {
        // At the comma position, should find the previous word
        val (start, end) = findWordBoundaries("hello, world", 5)
        assertEquals(0, start)
        assertEquals(5, end) // "hello" - cursor at end of word
    }

    @Test
    fun `findWordBoundaries with punctuation between words`() {
        val (start, end) = findWordBoundaries("hello,world", 5)
        // At comma, cursor is between words - finds "hello" (previous word char)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun `findWordBoundaries selects word after punctuation`() {
        val (start, end) = findWordBoundaries("hello,world", 6)
        // At 'w' in "world"
        assertEquals(6, start)
        assertEquals(11, end)
    }

    @Test
    fun `findWordBoundaries at beginning of second word`() {
        val (start, end) = findWordBoundaries("hello world", 6)
        assertEquals(6, start)
        assertEquals(11, end)
    }

    @Test
    fun `findWordBoundaries at end of text`() {
        val (start, end) = findWordBoundaries("hello world", 11)
        assertEquals(6, start)
        assertEquals(11, end)
    }

    // ==================== findLineIndexAtY ====================

    @Test
    fun `findLineIndexAtY finds first line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null),
            LineLayoutInfo(2, 100f, 50f, null)
        )
        assertEquals(0, findLineIndexAtY(25f, layouts, 2))
    }

    @Test
    fun `findLineIndexAtY finds middle line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null),
            LineLayoutInfo(2, 100f, 50f, null)
        )
        assertEquals(1, findLineIndexAtY(75f, layouts, 2))
    }

    @Test
    fun `findLineIndexAtY finds last line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null),
            LineLayoutInfo(2, 100f, 50f, null)
        )
        assertEquals(2, findLineIndexAtY(125f, layouts, 2))
    }

    @Test
    fun `findLineIndexAtY at line boundary returns upper line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null)
        )
        // At exactly 50f (start of second line)
        assertEquals(1, findLineIndexAtY(50f, layouts, 1))
    }

    @Test
    fun `findLineIndexAtY returns 0 for negative maxLineIndex`() {
        val layouts = listOf(LineLayoutInfo(0, 0f, 50f, null))
        assertEquals(0, findLineIndexAtY(25f, layouts, -1))
    }

    @Test
    fun `findLineIndexAtY clamps to maxLineIndex`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null),
            LineLayoutInfo(2, 100f, 50f, null)
        )
        // Position in line 2, but maxLineIndex is 1
        assertEquals(1, findLineIndexAtY(125f, layouts, 1))
    }

    @Test
    fun `findLineIndexAtY handles empty layouts with default height`() {
        val layouts = emptyList<LineLayoutInfo>()
        // With default height of 50, y=75 should estimate line 1
        assertEquals(1, findLineIndexAtY(75f, layouts, 5, 50f))
    }

    @Test
    fun `findLineIndexAtY handles layouts with zero height`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 0f, null), // Zero height
            LineLayoutInfo(1, 50f, 50f, null)
        )
        // Should skip zero-height line and find line 1
        assertEquals(1, findLineIndexAtY(75f, layouts, 1))
    }

    @Test
    fun `findLineIndexAtY beyond all lines returns last line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null)
        )
        // Position way beyond all lines
        assertEquals(1, findLineIndexAtY(500f, layouts, 1))
    }

    @Test
    fun `findLineIndexAtY at y zero returns first line`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 50f, null),
            LineLayoutInfo(1, 50f, 50f, null)
        )
        assertEquals(0, findLineIndexAtY(0f, layouts, 1))
    }

    @Test
    fun `findLineIndexAtY with varying line heights`() {
        val layouts = listOf(
            LineLayoutInfo(0, 0f, 30f, null),    // 0-30
            LineLayoutInfo(1, 30f, 100f, null),  // 30-130 (tall line)
            LineLayoutInfo(2, 130f, 30f, null)   // 130-160
        )
        assertEquals(0, findLineIndexAtY(15f, layouts, 2))
        assertEquals(1, findLineIndexAtY(80f, layouts, 2))
        assertEquals(2, findLineIndexAtY(145f, layouts, 2))
    }

    // ==================== GestureStateMachine ====================

    private fun createGSM(
        downPosition: Offset = Offset(100f, 100f),
        downNearCursor: Boolean = false,
        touchSlop: Float = 10f,
        resolveOffset: (Offset) -> Int = { it.x.toInt() },
        resolveBoundaries: (Int) -> Pair<Int, Int> = { it to it + 5 },
        setSelection: (Int, Int) -> Unit = { _, _ -> },
        hasSelection: () -> Boolean = { false },
        isOffsetInSelection: (Int) -> Boolean = { false },
        onCursorDragged: (Int) -> Unit = {}
    ) = GestureStateMachine(
        downPosition, downNearCursor, touchSlop,
        resolveOffset, resolveBoundaries, setSelection,
        hasSelection, isOffsetInSelection, onCursorDragged
    )

    @Test
    fun `drag far from cursor becomes scroll gesture`() {
        val gsm = createGSM(downNearCursor = false, touchSlop = 10f)
        // Move past touch slop
        gsm.onPointerMove(Offset(100f, 115f))
        assertTrue(gsm.isScrollGesture)
        assertFalse(gsm.isCursorDragGesture)
    }

    @Test
    fun `drag near cursor becomes cursor drag gesture`() {
        val draggedOffsets = mutableListOf<Int>()
        val gsm = createGSM(
            downNearCursor = true,
            touchSlop = 10f,
            onCursorDragged = { draggedOffsets.add(it) }
        )
        // Move past touch slop
        val consumed = gsm.onPointerMove(Offset(100f, 115f))
        assertTrue(consumed)
        assertTrue(gsm.isCursorDragGesture)
        assertFalse(gsm.isScrollGesture)
        assertEquals(1, draggedOffsets.size)
    }

    @Test
    fun `cursor drag continues moving cursor on subsequent moves`() {
        val draggedOffsets = mutableListOf<Int>()
        val gsm = createGSM(
            downNearCursor = true,
            touchSlop = 10f,
            onCursorDragged = { draggedOffsets.add(it) }
        )
        gsm.onPointerMove(Offset(100f, 115f)) // enter cursor drag
        gsm.onPointerMove(Offset(120f, 115f))
        gsm.onPointerMove(Offset(140f, 115f))
        assertEquals(3, draggedOffsets.size)
        assertEquals(120, draggedOffsets[1])
        assertEquals(140, draggedOffsets[2])
    }

    @Test
    fun `long press is ignored during cursor drag`() {
        var selectionSet = false
        val gsm = createGSM(
            downNearCursor = true,
            touchSlop = 10f,
            setSelection = { _, _ -> selectionSet = true },
            onCursorDragged = {}
        )
        gsm.onPointerMove(Offset(100f, 115f)) // enter cursor drag
        gsm.onLongPressTriggered()
        assertFalse(gsm.longPressTriggered)
        assertFalse(selectionSet)
        assertTrue(gsm.isCursorDragGesture)
    }

    @Test
    fun `long press triggers selection when not near cursor`() {
        var selStart = -1
        var selEnd = -1
        val gsm = createGSM(
            downNearCursor = false,
            resolveBoundaries = { 10 to 15 },
            setSelection = { s, e -> selStart = s; selEnd = e }
        )
        gsm.onLongPressTriggered()
        assertTrue(gsm.longPressTriggered)
        assertEquals(10, selStart)
        assertEquals(15, selEnd)
    }

    @Test
    fun `long press then drag extends selection`() {
        var lastSelStart = -1
        var lastSelEnd = -1
        val gsm = createGSM(
            downPosition = Offset(100f, 100f),
            downNearCursor = false,
            resolveBoundaries = { 10 to 15 },
            setSelection = { s, e -> lastSelStart = s; lastSelEnd = e }
        )
        gsm.onLongPressTriggered()
        // Drag to position that resolves to offset 20 (past the word end of 15)
        val consumed = gsm.onPointerMove(Offset(120f, 100f))
        assertTrue(consumed)
        assertEquals(10, lastSelStart) // anchor start preserved
        assertEquals(120, lastSelEnd) // extended to drag position
    }

    @Test
    fun `tap positions cursor`() {
        var cursorOffset = -1
        val gsm = createGSM(
            downPosition = Offset(50f, 100f),
            resolveOffset = { 42 }
        )
        gsm.onGestureComplete(
            onCursorPositioned = { cursorOffset = it },
            onTapOnSelection = null,
            onSelectionCompleted = null
        )
        assertEquals(42, cursorOffset)
    }

    @Test
    fun `tap on selection triggers selection callback`() {
        var tappedOnSelection = false
        val gsm = createGSM(
            hasSelection = { true },
            isOffsetInSelection = { true }
        )
        gsm.onGestureComplete(
            onCursorPositioned = {},
            onTapOnSelection = { tappedOnSelection = true },
            onSelectionCompleted = null
        )
        assertTrue(tappedOnSelection)
    }

    @Test
    fun `cursor drag complete does not reposition cursor`() {
        var cursorPositioned = false
        val gsm = createGSM(
            downNearCursor = true,
            touchSlop = 10f,
            onCursorDragged = {}
        )
        gsm.onPointerMove(Offset(100f, 115f)) // enter cursor drag
        gsm.onGestureComplete(
            onCursorPositioned = { cursorPositioned = true },
            onTapOnSelection = null,
            onSelectionCompleted = null
        )
        assertFalse(cursorPositioned)
    }

    @Test
    fun `scroll gesture suppresses all callbacks`() {
        var anyCalled = false
        val gsm = createGSM(downNearCursor = false, touchSlop = 10f)
        gsm.onPointerMove(Offset(100f, 115f)) // become scroll
        gsm.onGestureComplete(
            onCursorPositioned = { anyCalled = true },
            onTapOnSelection = { anyCalled = true },
            onSelectionCompleted = { anyCalled = true }
        )
        assertFalse(anyCalled)
    }

    @Test
    fun `consumed by child suppresses all callbacks`() {
        var anyCalled = false
        val gsm = createGSM()
        gsm.markConsumedByChild()
        gsm.onGestureComplete(
            onCursorPositioned = { anyCalled = true },
            onTapOnSelection = { anyCalled = true },
            onSelectionCompleted = { anyCalled = true }
        )
        assertFalse(anyCalled)
    }

    @Test
    fun `small movement within touch slop is not scroll or cursor drag`() {
        val gsm = createGSM(downNearCursor = true, touchSlop = 10f)
        val consumed = gsm.onPointerMove(Offset(105f, 100f)) // 5px, within slop
        assertFalse(consumed)
        assertFalse(gsm.isScrollGesture)
        assertFalse(gsm.isCursorDragGesture)
    }
}
