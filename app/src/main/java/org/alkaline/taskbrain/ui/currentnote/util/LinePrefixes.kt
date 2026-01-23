package org.alkaline.taskbrain.ui.currentnote.util

/**
 * Single source of truth for line prefix constants used in bullet lists and checkboxes.
 */
object LinePrefixes {
    // Display prefixes (what the user sees)
    const val BULLET = "• "
    const val CHECKBOX_UNCHECKED = "☐ "
    const val CHECKBOX_CHECKED = "☑ "

    // Source prefixes (what the user types)
    const val ASTERISK_SPACE = "* "
    const val BRACKETS_EMPTY = "[]"
    const val BRACKETS_CHECKED = "[x]"

    // Indentation
    const val TAB = "\t"

    // All display prefixes that trigger continuation on Enter
    val LINE_PREFIXES = listOf(BULLET, CHECKBOX_UNCHECKED, CHECKBOX_CHECKED)

    /**
     * Returns the display prefix if the line (after indentation) starts with one, null otherwise.
     */
    fun getPrefix(lineContent: String): String? {
        val trimmed = lineContent.trimStart(TAB[0])
        return LINE_PREFIXES.find { trimmed.startsWith(it) }
    }

    /**
     * Returns true if the line starts with a bullet prefix (after indentation).
     */
    fun hasBullet(lineContent: String): Boolean {
        return lineContent.trimStart(TAB[0]).startsWith(BULLET)
    }

    /**
     * Returns true if the line starts with any checkbox prefix (after indentation).
     */
    fun hasCheckbox(lineContent: String): Boolean {
        val trimmed = lineContent.trimStart(TAB[0])
        return trimmed.startsWith(CHECKBOX_UNCHECKED) || trimmed.startsWith(CHECKBOX_CHECKED)
    }

    /**
     * Returns true if the line starts with any display prefix (bullet or checkbox, after indentation).
     */
    fun hasAnyPrefix(lineContent: String): Boolean {
        return getPrefix(lineContent) != null
    }

    /**
     * Removes the prefix from line content (preserving indentation).
     * Returns the original content if no prefix found.
     */
    fun removePrefix(lineContent: String): String {
        val indentation = lineContent.takeWhile { it == TAB[0] }
        val afterIndent = lineContent.removePrefix(indentation)
        val prefix = LINE_PREFIXES.find { afterIndent.startsWith(it) } ?: return lineContent
        return indentation + afterIndent.removePrefix(prefix)
    }

    /**
     * Adds a prefix to line content (after indentation).
     * If the line already has a prefix, replaces it.
     */
    fun addPrefix(lineContent: String, prefix: String): String {
        val indentation = lineContent.takeWhile { it == TAB[0] }
        val afterIndent = lineContent.removePrefix(indentation)
        val existingPrefix = LINE_PREFIXES.find { afterIndent.startsWith(it) }
        return if (existingPrefix != null) {
            indentation + prefix + afterIndent.removePrefix(existingPrefix)
        } else {
            indentation + prefix + afterIndent
        }
    }
}
