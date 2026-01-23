package org.alkaline.taskbrain.ui.currentnote.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHtmlConverterTest {

    // ==================== convertToHtml ====================

    @Test
    fun `convertToHtml converts single bullet to html`() {
        val text = "• Item"
        val html = convertToHtml(text)
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li>Item</li>"))
        assertTrue(html.contains("</ul>"))
    }

    @Test
    fun `convertToHtml converts multiple bullets to html list`() {
        val text = "• Item1\n• Item2\n• Item3"
        val html = convertToHtml(text)
        assertEquals("<ul><li>Item1</li><li>Item2</li><li>Item3</li></ul>", html)
    }

    @Test
    fun `convertToHtml converts checkbox to html list item`() {
        val text = "☐ Task"
        val html = convertToHtml(text)
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li>Task</li>"))
        assertTrue(html.contains("</ul>"))
    }

    @Test
    fun `convertToHtml converts checked checkbox to html list item`() {
        val text = "☑ Done"
        val html = convertToHtml(text)
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li>Done</li>"))
        assertTrue(html.contains("</ul>"))
    }

    @Test
    fun `convertToHtml handles indented bullets as nested lists`() {
        val text = "• Parent\n\t• Child"
        val html = convertToHtml(text)
        // Should have two levels of ul
        assertTrue(html.contains("<ul><li>Parent</li><ul><li>Child</li></ul></ul>"))
    }

    @Test
    fun `convertToHtml handles multiple indentation levels`() {
        val text = "• Level0\n\t• Level1\n\t\t• Level2"
        val html = convertToHtml(text)
        // Count ul tags - should have 3
        val ulCount = "<ul>".toRegex().findAll(html).count()
        assertEquals(3, ulCount)
    }

    @Test
    fun `convertToHtml handles plain text as paragraph`() {
        val text = "Just plain text"
        val html = convertToHtml(text)
        assertTrue(html.contains("<p>Just plain text</p>"))
    }

    @Test
    fun `convertToHtml handles empty line as break`() {
        val text = "• Item1\n\n• Item2"
        val html = convertToHtml(text)
        assertTrue(html.contains("<br>"))
    }

    @Test
    fun `convertToHtml handles mixed bullets and plain text`() {
        val text = "Header\n• Item1\n• Item2\nFooter"
        val html = convertToHtml(text)
        assertTrue(html.contains("<p>Header</p>"))
        assertTrue(html.contains("<li>Item1</li>"))
        assertTrue(html.contains("<li>Item2</li>"))
        assertTrue(html.contains("<p>Footer</p>"))
    }

    @Test
    fun `convertToHtml escapes html special characters`() {
        val text = "• Item with <brackets> & ampersand"
        val html = convertToHtml(text)
        assertTrue(html.contains("&lt;"))
        assertTrue(html.contains("&gt;"))
        assertTrue(html.contains("&amp;"))
    }

    @Test
    fun `convertToHtml handles unindent back to root level`() {
        val text = "• Root1\n\t• Nested\n• Root2"
        val html = convertToHtml(text)
        // Should properly close nested ul before opening Root2
        assertTrue(html.contains("</ul><li>Root2</li>"))
    }

    @Test
    fun `convertToHtml handles checkboxes and bullets mixed`() {
        val text = "• Bullet\n☐ Unchecked\n☑ Checked"
        val html = convertToHtml(text)
        assertTrue(html.contains("<li>Bullet</li>"))
        assertTrue(html.contains("<li>Unchecked</li>"))
        assertTrue(html.contains("<li>Checked</li>"))
    }

    @Test
    fun `convertToHtml handles empty bullet`() {
        val text = "• "
        val html = convertToHtml(text)
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li></li>"))
        assertTrue(html.contains("</ul>"))
    }

    @Test
    fun `convertToHtml handles indented checkbox`() {
        val text = "• Parent\n\t☐ Child task"
        val html = convertToHtml(text)
        assertTrue(html.contains("<li>Parent</li>"))
        assertTrue(html.contains("<li>Child task</li>"))
    }
}
