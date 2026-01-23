package org.alkaline.taskbrain.ui.currentnote.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionRendererTest {

    // ==================== lineSelectionToContentSelection ====================

    @Test
    fun `lineSelectionToContentSelection with selection fully in content`() {
        // Line: "• hello" (prefix="• ", content="hello")
        // prefixLength=2, contentLength=5
        // lineSelection=3..5 (selecting "ell" in content)
        val result = lineSelectionToContentSelection(3..5, prefixLength = 2, contentLength = 5)
        assertEquals(1..3, result) // "ell" in content coordinates
    }

    @Test
    fun `lineSelectionToContentSelection with selection spanning prefix and content`() {
        // Line: "• hello" (prefix="• ", content="hello")
        // lineSelection=0..4 (selecting "• hel")
        val result = lineSelectionToContentSelection(0..4, prefixLength = 2, contentLength = 5)
        assertEquals(0..2, result) // "hel" in content
    }

    @Test
    fun `lineSelectionToContentSelection with selection only in prefix`() {
        // Line: "• hello" (prefix="• ", content="hello")
        // lineSelection=0..1 (selecting "• ")
        val result = lineSelectionToContentSelection(0..1, prefixLength = 2, contentLength = 5)
        assertNull(result) // No content selected
    }

    @Test
    fun `lineSelectionToContentSelection with selection covering entire line`() {
        // Line: "• hello" (prefix="• ", content="hello")
        // lineSelection=0..6 (selecting entire line)
        val result = lineSelectionToContentSelection(0..6, prefixLength = 2, contentLength = 5)
        assertEquals(0..4, result) // entire content
    }

    @Test
    fun `lineSelectionToContentSelection with no prefix`() {
        // Line: "hello" (no prefix)
        // lineSelection=1..3 (selecting "ell")
        val result = lineSelectionToContentSelection(1..3, prefixLength = 0, contentLength = 5)
        assertEquals(1..3, result)
    }

    @Test
    fun `lineSelectionToContentSelection with empty content`() {
        // Line: "• " (prefix="• ", content="")
        // lineSelection=0..1
        val result = lineSelectionToContentSelection(0..1, prefixLength = 2, contentLength = 0)
        assertNull(result)
    }

    @Test
    fun `lineSelectionToContentSelection with selection at content start`() {
        // Line: "• hello"
        // lineSelection=2..4 (starting exactly at content)
        val result = lineSelectionToContentSelection(2..4, prefixLength = 2, contentLength = 5)
        assertEquals(0..2, result)
    }

    @Test
    fun `lineSelectionToContentSelection with selection at content end`() {
        // Line: "• hello"
        // lineSelection=4..6 (ending exactly at content end)
        val result = lineSelectionToContentSelection(4..6, prefixLength = 2, contentLength = 5)
        assertEquals(2..4, result)
    }

    // ==================== lineSelectionToPrefixSelection ====================

    @Test
    fun `lineSelectionToPrefixSelection with selection fully in prefix`() {
        // Line: "• hello" (prefix="• ")
        // lineSelection=0..1 (selecting "• ")
        val result = lineSelectionToPrefixSelection(0..1, prefixLength = 2)
        assertEquals(0..1, result)
    }

    @Test
    fun `lineSelectionToPrefixSelection with selection spanning prefix and content`() {
        // Line: "• hello" (prefix="• ")
        // lineSelection=0..4 (selecting "• hel")
        val result = lineSelectionToPrefixSelection(0..4, prefixLength = 2)
        assertEquals(0..1, result) // Only prefix part
    }

    @Test
    fun `lineSelectionToPrefixSelection with selection only in content`() {
        // Line: "• hello" (prefix="• ")
        // lineSelection=3..5 (selecting "ell")
        val result = lineSelectionToPrefixSelection(3..5, prefixLength = 2)
        assertNull(result) // No prefix selected
    }

    @Test
    fun `lineSelectionToPrefixSelection with selection starting in prefix ending in content`() {
        // Line: "• hello"
        // lineSelection=1..4 (selecting " hel")
        val result = lineSelectionToPrefixSelection(1..4, prefixLength = 2)
        assertEquals(1..1, result) // Just the space in prefix
    }

    @Test
    fun `lineSelectionToPrefixSelection with no prefix`() {
        // Line: "hello" (no prefix)
        val result = lineSelectionToPrefixSelection(0..3, prefixLength = 0)
        assertNull(result)
    }

    @Test
    fun `lineSelectionToPrefixSelection with selection starting at prefix end`() {
        // Line: "• hello"
        // lineSelection=2..5 (starting exactly after prefix)
        val result = lineSelectionToPrefixSelection(2..5, prefixLength = 2)
        assertNull(result)
    }

    @Test
    fun `lineSelectionToPrefixSelection with long prefix`() {
        // Line: "\t\t• hello" (prefix="\t\t• ")
        // lineSelection=0..2 (selecting tabs)
        val result = lineSelectionToPrefixSelection(0..2, prefixLength = 4)
        assertEquals(0..2, result)
    }

    @Test
    fun `lineSelectionToPrefixSelection with entire prefix selected`() {
        // Line: "• hello"
        // lineSelection=0..1 covers entire prefix "• "
        val result = lineSelectionToPrefixSelection(0..1, prefixLength = 2)
        assertEquals(0..1, result)
    }
}
