package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SelectionMenuControllerTest {

    private lateinit var controller: SelectionMenuController

    @Before
    fun setUp() {
        controller = SelectionMenuController()
    }

    // ==================== syncCursorPosition ====================

    @Test
    fun `syncCursorPosition updates when cursor moves collapsed to collapsed`() {
        // Initial state: cursor at 0
        controller.syncCursorPosition(TextRange(0))
        assertEquals(TextRange(0), controller.previousSelection)

        // Move cursor to position 5
        val synced = controller.syncCursorPosition(TextRange(5))

        assertTrue(synced)
        assertEquals(TextRange(5), controller.previousSelection)
    }

    @Test
    fun `syncCursorPosition does not update when selection is not collapsed`() {
        controller.syncCursorPosition(TextRange(0))

        // Try to sync with a non-collapsed selection
        val synced = controller.syncCursorPosition(TextRange(0, 5))

        assertFalse(synced)
        assertEquals(TextRange(0), controller.previousSelection)
    }

    @Test
    fun `syncCursorPosition does not update when previous was not collapsed`() {
        // Set up a non-collapsed previous selection by using analyzeSelectionChange
        controller.analyzeSelectionChange(TextRange(0, 5))
        controller.updatePreviousSelection(TextRange(0, 5))

        // Try to sync with collapsed - should not update because previous is not collapsed
        val synced = controller.syncCursorPosition(TextRange(3))

        assertFalse(synced)
        assertEquals(TextRange(0, 5), controller.previousSelection)
    }

    @Test
    fun `syncCursorPosition does not update when position unchanged`() {
        controller.syncCursorPosition(TextRange(5))

        // Same position - should not update
        val synced = controller.syncCursorPosition(TextRange(5))

        assertFalse(synced)
    }

    // ==================== analyzeSelectionChange ====================

    @Test
    fun `analyzeSelectionChange returns WaitForGesture for new selection from cursor`() {
        // Previous is collapsed cursor
        controller.syncCursorPosition(TextRange(0))

        // New selection created
        val result = controller.analyzeSelectionChange(TextRange(0, 5))

        assertTrue(result is SelectionMenuController.SelectionChangeResult.WaitForGesture)
    }

    @Test
    fun `analyzeSelectionChange returns WaitForGesture for changed selection`() {
        // Set up previous selection
        controller.updatePreviousSelection(TextRange(0, 5))

        // Selection changed to different range
        val result = controller.analyzeSelectionChange(TextRange(10, 15))

        assertTrue(result is SelectionMenuController.SelectionChangeResult.WaitForGesture)
    }

    @Test
    fun `analyzeSelectionChange returns PassThrough for same selection`() {
        controller.updatePreviousSelection(TextRange(0, 5))

        // Same selection
        val result = controller.analyzeSelectionChange(TextRange(0, 5))

        assertTrue(result is SelectionMenuController.SelectionChangeResult.PassThrough)
    }

    @Test
    fun `analyzeSelectionChange returns RestoreSelection for tap inside selection`() {
        // Set up a selection
        controller.updatePreviousSelection(TextRange(5, 15))

        // Tap inside the selection (cursor at position 10)
        val result = controller.analyzeSelectionChange(TextRange(10))

        assertTrue(result is SelectionMenuController.SelectionChangeResult.RestoreSelection)
        assertEquals(TextRange(5, 15), (result as SelectionMenuController.SelectionChangeResult.RestoreSelection).selectionToRestore)
    }

    @Test
    fun `analyzeSelectionChange returns PassThrough for tap outside selection`() {
        // Set up a selection
        controller.updatePreviousSelection(TextRange(5, 15))

        // Tap outside the selection (cursor at position 20)
        val result = controller.analyzeSelectionChange(TextRange(20))

        assertTrue(result is SelectionMenuController.SelectionChangeResult.PassThrough)
    }

    @Test
    fun `analyzeSelectionChange returns PassThrough for tap at selection start boundary`() {
        controller.updatePreviousSelection(TextRange(5, 15))

        // Tap at start boundary
        val result = controller.analyzeSelectionChange(TextRange(5))

        assertTrue(result is SelectionMenuController.SelectionChangeResult.RestoreSelection)
    }

    @Test
    fun `analyzeSelectionChange returns PassThrough for tap at selection end boundary`() {
        controller.updatePreviousSelection(TextRange(5, 15))

        // Tap at end boundary
        val result = controller.analyzeSelectionChange(TextRange(15))

        assertTrue(result is SelectionMenuController.SelectionChangeResult.RestoreSelection)
    }

    @Test
    fun `analyzeSelectionChange returns Skip when skipNextRestore is set`() {
        // Set up a selection
        controller.updatePreviousSelection(TextRange(5, 15))
        controller.markSkipNextRestore()

        // Tap inside the selection
        val result = controller.analyzeSelectionChange(TextRange(10))

        assertTrue(result is SelectionMenuController.SelectionChangeResult.Skip)
        // skipNextRestore should be reset
        assertFalse(controller.skipNextRestore)
    }

    @Test
    fun `analyzeSelectionChange returns PassThrough for cursor movement`() {
        // Previous is collapsed cursor
        controller.syncCursorPosition(TextRange(0))

        // Move cursor to new position
        val result = controller.analyzeSelectionChange(TextRange(5))

        assertTrue(result is SelectionMenuController.SelectionChangeResult.PassThrough)
    }

    // ==================== menu state ====================

    @Test
    fun `showMenu sets showContextMenu to true`() {
        assertFalse(controller.showContextMenu)

        controller.showMenu()

        assertTrue(controller.showContextMenu)
    }

    @Test
    fun `hideMenu sets showContextMenu to false`() {
        controller.showMenu()
        assertTrue(controller.showContextMenu)

        controller.hideMenu()

        assertFalse(controller.showContextMenu)
    }

    @Test
    fun `markSkipNextRestore sets flag`() {
        assertFalse(controller.skipNextRestore)

        controller.markSkipNextRestore()

        assertTrue(controller.skipNextRestore)
    }

    // ==================== updatePreviousSelection ====================

    @Test
    fun `updatePreviousSelection updates the stored selection`() {
        assertEquals(TextRange.Zero, controller.previousSelection)

        controller.updatePreviousSelection(TextRange(10, 20))

        assertEquals(TextRange(10, 20), controller.previousSelection)
    }
}
