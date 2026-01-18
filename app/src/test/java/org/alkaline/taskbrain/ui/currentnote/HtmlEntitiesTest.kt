package org.alkaline.taskbrain.ui.currentnote

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlEntitiesTest {

    // ==================== escape ====================

    @Test
    fun `escape escapes ampersand`() {
        assertEquals("foo &amp; bar", HtmlEntities.escape("foo & bar"))
    }

    @Test
    fun `escape escapes less than`() {
        assertEquals("foo &lt; bar", HtmlEntities.escape("foo < bar"))
    }

    @Test
    fun `escape escapes greater than`() {
        assertEquals("foo &gt; bar", HtmlEntities.escape("foo > bar"))
    }

    @Test
    fun `escape escapes quotes`() {
        assertEquals("foo &quot;bar&quot;", HtmlEntities.escape("foo \"bar\""))
    }

    @Test
    fun `escape handles plain text`() {
        assertEquals("Hello World", HtmlEntities.escape("Hello World"))
    }

    // ==================== unescape ====================

    @Test
    fun `unescape converts nbsp to space`() {
        assertEquals("foo bar", HtmlEntities.unescape("foo&nbsp;bar"))
    }

    @Test
    fun `unescape converts amp`() {
        assertEquals("foo & bar", HtmlEntities.unescape("foo &amp; bar"))
    }

    @Test
    fun `unescape converts lt`() {
        assertEquals("foo < bar", HtmlEntities.unescape("foo &lt; bar"))
    }

    @Test
    fun `unescape converts gt`() {
        assertEquals("foo > bar", HtmlEntities.unescape("foo &gt; bar"))
    }

    @Test
    fun `unescape converts quot`() {
        assertEquals("foo \"bar\"", HtmlEntities.unescape("foo &quot;bar&quot;"))
    }

    @Test
    fun `unescape handles plain text`() {
        assertEquals("Hello World", HtmlEntities.unescape("Hello World"))
    }

    @Test
    fun `unescape handles multiple entities`() {
        assertEquals("a < b & c > d", HtmlEntities.unescape("a &lt; b &amp; c &gt; d"))
    }

    // ==================== stripTags ====================

    @Test
    fun `stripTags removes simple tags`() {
        assertEquals("Hello", HtmlEntities.stripTags("<p>Hello</p>"))
    }

    @Test
    fun `stripTags removes tags with attributes`() {
        assertEquals("Hello", HtmlEntities.stripTags("<p class=\"foo\">Hello</p>"))
    }

    @Test
    fun `stripTags removes nested tags`() {
        assertEquals("Hello World", HtmlEntities.stripTags("<div><p>Hello</p> <span>World</span></div>"))
    }

    @Test
    fun `stripTags converts br to newline`() {
        assertEquals("Hello\nWorld", HtmlEntities.stripTags("Hello<br/>World"))
    }

    @Test
    fun `stripTags converts br with space to newline`() {
        assertEquals("Hello\nWorld", HtmlEntities.stripTags("Hello<br />World"))
    }

    @Test
    fun `stripTags converts br without slash to newline`() {
        assertEquals("Hello\nWorld", HtmlEntities.stripTags("Hello<br>World"))
    }

    @Test
    fun `stripTags handles plain text`() {
        assertEquals("Hello World", HtmlEntities.stripTags("Hello World"))
    }
}
