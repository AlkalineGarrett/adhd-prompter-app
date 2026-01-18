package org.alkaline.taskbrain.ui.currentnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinePrefixesTest {

    // ==================== getPrefix ====================

    @Test
    fun `getPrefix returns bullet for bullet line`() {
        assertEquals(LinePrefixes.BULLET, LinePrefixes.getPrefix("• Item"))
    }

    @Test
    fun `getPrefix returns unchecked checkbox for unchecked line`() {
        assertEquals(LinePrefixes.CHECKBOX_UNCHECKED, LinePrefixes.getPrefix("☐ Task"))
    }

    @Test
    fun `getPrefix returns checked checkbox for checked line`() {
        assertEquals(LinePrefixes.CHECKBOX_CHECKED, LinePrefixes.getPrefix("☑ Done"))
    }

    @Test
    fun `getPrefix returns null for plain line`() {
        assertNull(LinePrefixes.getPrefix("Plain text"))
    }

    @Test
    fun `getPrefix ignores leading tabs`() {
        assertEquals(LinePrefixes.BULLET, LinePrefixes.getPrefix("\t\t• Item"))
    }

    // ==================== hasBullet ====================

    @Test
    fun `hasBullet returns true for bullet line`() {
        assertTrue(LinePrefixes.hasBullet("• Item"))
    }

    @Test
    fun `hasBullet returns true for indented bullet line`() {
        assertTrue(LinePrefixes.hasBullet("\t• Item"))
    }

    @Test
    fun `hasBullet returns false for checkbox line`() {
        assertFalse(LinePrefixes.hasBullet("☐ Task"))
    }

    @Test
    fun `hasBullet returns false for plain line`() {
        assertFalse(LinePrefixes.hasBullet("Plain text"))
    }

    // ==================== hasCheckbox ====================

    @Test
    fun `hasCheckbox returns true for unchecked checkbox`() {
        assertTrue(LinePrefixes.hasCheckbox("☐ Task"))
    }

    @Test
    fun `hasCheckbox returns true for checked checkbox`() {
        assertTrue(LinePrefixes.hasCheckbox("☑ Done"))
    }

    @Test
    fun `hasCheckbox returns true for indented checkbox`() {
        assertTrue(LinePrefixes.hasCheckbox("\t☐ Task"))
    }

    @Test
    fun `hasCheckbox returns false for bullet line`() {
        assertFalse(LinePrefixes.hasCheckbox("• Item"))
    }

    @Test
    fun `hasCheckbox returns false for plain line`() {
        assertFalse(LinePrefixes.hasCheckbox("Plain text"))
    }

    // ==================== hasAnyPrefix ====================

    @Test
    fun `hasAnyPrefix returns true for bullet`() {
        assertTrue(LinePrefixes.hasAnyPrefix("• Item"))
    }

    @Test
    fun `hasAnyPrefix returns true for unchecked checkbox`() {
        assertTrue(LinePrefixes.hasAnyPrefix("☐ Task"))
    }

    @Test
    fun `hasAnyPrefix returns true for checked checkbox`() {
        assertTrue(LinePrefixes.hasAnyPrefix("☑ Done"))
    }

    @Test
    fun `hasAnyPrefix returns false for plain text`() {
        assertFalse(LinePrefixes.hasAnyPrefix("Plain text"))
    }

    // ==================== removePrefix ====================

    @Test
    fun `removePrefix removes bullet`() {
        assertEquals("Item", LinePrefixes.removePrefix("• Item"))
    }

    @Test
    fun `removePrefix removes checkbox`() {
        assertEquals("Task", LinePrefixes.removePrefix("☐ Task"))
    }

    @Test
    fun `removePrefix preserves indentation`() {
        assertEquals("\tItem", LinePrefixes.removePrefix("\t• Item"))
    }

    @Test
    fun `removePrefix returns original for plain text`() {
        assertEquals("Plain text", LinePrefixes.removePrefix("Plain text"))
    }

    // ==================== addPrefix ====================

    @Test
    fun `addPrefix adds bullet to plain text`() {
        assertEquals("• Item", LinePrefixes.addPrefix("Item", LinePrefixes.BULLET))
    }

    @Test
    fun `addPrefix adds checkbox to plain text`() {
        assertEquals("☐ Task", LinePrefixes.addPrefix("Task", LinePrefixes.CHECKBOX_UNCHECKED))
    }

    @Test
    fun `addPrefix preserves indentation`() {
        assertEquals("\t• Item", LinePrefixes.addPrefix("\tItem", LinePrefixes.BULLET))
    }

    @Test
    fun `addPrefix replaces existing prefix`() {
        assertEquals("☐ Item", LinePrefixes.addPrefix("• Item", LinePrefixes.CHECKBOX_UNCHECKED))
    }

    @Test
    fun `addPrefix replaces checkbox with bullet`() {
        assertEquals("• Task", LinePrefixes.addPrefix("☐ Task", LinePrefixes.BULLET))
    }

    @Test
    fun `addPrefix preserves indentation when replacing`() {
        assertEquals("\t\t☐ Item", LinePrefixes.addPrefix("\t\t• Item", LinePrefixes.CHECKBOX_UNCHECKED))
    }
}
