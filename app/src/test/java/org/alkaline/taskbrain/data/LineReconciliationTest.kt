package org.alkaline.taskbrain.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the consolidated line-reconciliation logic that backs
 * EditorState.updateFromText, CurrentNoteViewModel.updateTrackedLines, and
 * NoteRepository.matchLinesToIds.
 */
class LineReconciliationTest {

    // ==================== matchLinesByContent ====================

    @Test
    fun `exact matches in order`() {
        val matches = matchLinesByContent(
            oldContents = listOf("A", "B", "C"),
            newContents = listOf("A", "B", "C"),
        )
        assertArrayEquals(intArrayOf(0, 1, 2), matches)
    }

    @Test
    fun `exact match with reordering`() {
        val matches = matchLinesByContent(
            oldContents = listOf("A", "B", "C"),
            newContents = listOf("C", "A", "B"),
        )
        assertArrayEquals(intArrayOf(2, 0, 1), matches)
    }

    @Test
    fun `duplicate content matched left-to-right`() {
        // Three "X" lines: each new "X" consumes the next available old "X" in order.
        val matches = matchLinesByContent(
            oldContents = listOf("X", "Y", "X", "X"),
            newContents = listOf("X", "X", "X"),
        )
        assertArrayEquals(intArrayOf(0, 2, 3), matches)
    }

    @Test
    fun `unmatched line is LINE_MATCH_NONE`() {
        val matches = matchLinesByContent(
            oldContents = listOf("A", "B"),
            newContents = listOf("A", "totally new content"),
        )
        assertEquals(0, matches[0])
        assertEquals(LINE_MATCH_NONE, matches[1])
    }

    @Test
    fun `similarity match catches small edit`() {
        val matches = matchLinesByContent(
            oldContents = listOf("Hello world"),
            newContents = listOf("Hello world!"),
        )
        // Only one possible match — similarity finds it.
        assertEquals(0, matches[0])
    }

    @Test
    fun `no positional fallback - completely-rewritten line is unmatched`() {
        val matches = matchLinesByContent(
            oldContents = listOf("Original content here"),
            newContents = listOf("xyz"),
        )
        // No characters in common → no similarity → unmatched (no positional fallback).
        assertEquals(LINE_MATCH_NONE, matches[0])
    }

    @Test
    fun `empty inputs return empty result`() {
        assertArrayEquals(intArrayOf(), matchLinesByContent(emptyList(), emptyList()))
    }

    @Test
    fun `empty old contents - all new lines unmatched`() {
        val matches = matchLinesByContent(emptyList(), listOf("A", "B"))
        assertArrayEquals(intArrayOf(LINE_MATCH_NONE, LINE_MATCH_NONE), matches)
    }

    @Test
    fun `empty new contents returns empty`() {
        assertArrayEquals(intArrayOf(), matchLinesByContent(listOf("A", "B"), emptyList()))
    }

    // ==================== reconcileLineNoteIds ====================

    @Test
    fun `reconcile preserves noteIds via exact match`() {
        val result = reconcileLineNoteIds(
            oldContents = listOf("A", "B", "C"),
            oldNoteIds = listOf(listOf("idA"), listOf("idB"), listOf("idC")),
            newContents = listOf("A", "B", "C"),
        )
        assertEquals(listOf("idA"), result[0])
        assertEquals(listOf("idB"), result[1])
        assertEquals(listOf("idC"), result[2])
    }

    @Test
    fun `reconcile preserves noteIds across reorder`() {
        val result = reconcileLineNoteIds(
            oldContents = listOf("A", "B", "C"),
            oldNoteIds = listOf(listOf("idA"), listOf("idB"), listOf("idC")),
            newContents = listOf("C", "A", "B"),
        )
        assertEquals(listOf("idC"), result[0])
        assertEquals(listOf("idA"), result[1])
        assertEquals(listOf("idB"), result[2])
    }

    @Test
    fun `reconcile gives empty list to unmatched line`() {
        val result = reconcileLineNoteIds(
            oldContents = listOf("A", "B"),
            oldNoteIds = listOf(listOf("idA"), listOf("idB")),
            newContents = listOf("A", "B", "completely new"),
        )
        assertEquals(listOf("idA"), result[0])
        assertEquals(listOf("idB"), result[1])
        assertTrue(result[2].isEmpty())
    }

    @Test
    fun `reconcile reports unmatched non-empty lines via callback`() {
        val unmatched = mutableListOf<Pair<Int, String>>()
        reconcileLineNoteIds(
            oldContents = listOf("A", "B"),
            oldNoteIds = listOf(listOf("idA"), listOf("idB")),
            newContents = listOf("A", "B", "xyz", "qwerty"),
            onUnmatchedNonEmpty = { idx, content -> unmatched.add(idx to content) },
        )
        assertEquals(listOf(2 to "xyz", 3 to "qwerty"), unmatched)
    }

    @Test
    fun `reconcile does NOT report unmatched empty lines`() {
        val unmatched = mutableListOf<Int>()
        reconcileLineNoteIds(
            oldContents = listOf("A"),
            oldNoteIds = listOf(listOf("idA")),
            newContents = listOf("A", "", ""),
            onUnmatchedNonEmpty = { idx, _ -> unmatched.add(idx) },
        )
        assertTrue(unmatched.isEmpty())
    }

    @Test
    fun `reconcile preserves multi-id (merge) lists`() {
        val result = reconcileLineNoteIds(
            oldContents = listOf("Merged content"),
            oldNoteIds = listOf(listOf("idA", "idB", "idC")),
            newContents = listOf("Merged content"),
        )
        assertEquals(listOf("idA", "idB", "idC"), result[0])
    }

    @Test
    fun `reconcile errors when old size mismatch`() {
        var threw = false
        try {
            reconcileLineNoteIds(
                oldContents = listOf("A", "B"),
                oldNoteIds = listOf(listOf("idA")),  // wrong size
                newContents = listOf("A"),
            )
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ==================== enforceParentNoteId ====================

    @Test
    fun `enforce sets first line primary id when missing`() {
        val result = enforceParentNoteId(
            noteIds = listOf(emptyList(), listOf("idB"), listOf("idC")),
            parentNoteId = "parent",
        )
        assertEquals(listOf("parent"), result[0])
        assertEquals(listOf("idB"), result[1])
        assertEquals(listOf("idC"), result[2])
    }

    @Test
    fun `enforce keeps existing parent as primary`() {
        val noteIds = listOf(listOf("parent"), listOf("idB"))
        val result = enforceParentNoteId(noteIds, "parent")
        assertEquals(noteIds, result)
    }

    @Test
    fun `enforce promotes parent to primary, preserving secondaries`() {
        val result = enforceParentNoteId(
            noteIds = listOf(listOf("idA", "idB"), listOf("idC")),
            parentNoteId = "parent",
        )
        // parent prepended, idA and idB preserved as secondaries
        assertEquals(listOf("parent", "idA", "idB"), result[0])
        assertEquals(listOf("idC"), result[1])
    }

    @Test
    fun `enforce dedupes parent if it appears as secondary`() {
        // Reordering brought parent to a secondary slot — promote it back, no duplication.
        val result = enforceParentNoteId(
            noteIds = listOf(listOf("idA", "parent")),
            parentNoteId = "parent",
        )
        assertEquals(listOf("parent", "idA"), result[0])
    }

    @Test
    fun `enforce no-op when parentNoteId empty`() {
        val noteIds = listOf(listOf("idA"), listOf("idB"))
        val result = enforceParentNoteId(noteIds, "")
        assertEquals(noteIds, result)
    }

    @Test
    fun `enforce no-op when noteIds empty`() {
        val result = enforceParentNoteId(emptyList(), "parent")
        assertTrue(result.isEmpty())
    }
}
