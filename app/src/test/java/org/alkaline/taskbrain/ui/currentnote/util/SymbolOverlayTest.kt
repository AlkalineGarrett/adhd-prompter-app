package org.alkaline.taskbrain.ui.currentnote.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolOverlayTest {

    @Test
    fun `hasVisibleBadges returns false for empty list`() {
        assertFalse(emptyList<SymbolOverlay>().hasVisibleBadges())
    }

    @Test
    fun `hasVisibleBadges returns false when all badges are None`() {
        val overlays = listOf(
            SymbolOverlay("⏰", SymbolBadge.None),
            SymbolOverlay("⏰", SymbolBadge.None)
        )
        assertFalse(overlays.hasVisibleBadges())
    }

    @Test
    fun `hasVisibleBadges returns true with Centered badge`() {
        val overlays = listOf(
            SymbolOverlay("⏰", SymbolBadge.None),
            SymbolOverlay("⏰", SymbolBadge.Centered(text = "!", color = Color.Red))
        )
        assertTrue(overlays.hasVisibleBadges())
    }

    @Test
    fun `hasVisibleBadges returns true with Corner badge`() {
        val overlays = listOf(
            SymbolOverlay("⏰", SymbolBadge.Corner(text = "✓", color = Color.Green))
        )
        assertTrue(overlays.hasVisibleBadges())
    }

    @Test
    fun `hasVisibleBadges works with mixed symbol types`() {
        val overlays = listOf(
            SymbolOverlay("⏰", SymbolBadge.None),
            SymbolOverlay("📌", SymbolBadge.Corner(text = "!", color = Color.Red))
        )
        assertTrue(overlays.hasVisibleBadges())
    }
}
