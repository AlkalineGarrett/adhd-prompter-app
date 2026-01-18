package org.alkaline.taskbrain.ui.currentnote

import android.text.Html

/**
 * Utilities for escaping and unescaping HTML entities.
 */
object HtmlEntities {

    private val ENTITY_REPLACEMENTS = listOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\""
    )

    /**
     * Escapes special characters for HTML.
     * Falls back to manual replacement if Android's Html.escapeHtml returns null (in unit tests).
     */
    fun escape(text: String): String {
        return Html.escapeHtml(text) ?: text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    /**
     * Unescapes HTML entities to their character equivalents.
     */
    fun unescape(text: String): String {
        var result = text
        for ((entity, char) in ENTITY_REPLACEMENTS) {
            result = result.replace(entity, char)
        }
        return result
    }

    /**
     * Removes all HTML tags from a string.
     * Converts <br> tags to newlines.
     */
    fun stripTags(html: String): String {
        return html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
    }
}
