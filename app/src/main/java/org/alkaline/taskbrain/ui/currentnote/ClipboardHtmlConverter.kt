package org.alkaline.taskbrain.ui.currentnote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Html
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo

private const val BULLET_PREFIX = "• "
private const val CHECKBOX_UNCHECKED_PREFIX = "☐ "
private const val CHECKBOX_CHECKED_PREFIX = "☑ "

/**
 * Composable that monitors the clipboard and converts plain text with bullets/checkboxes
 * to HTML format for better compatibility when pasting into apps like Google Docs.
 */
@Composable
fun ClipboardHtmlConverter() {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val isWindowFocused = windowInfo.isWindowFocused

    var previouslyFocused by remember { mutableStateOf(false) }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Check clipboard when window gains focus (app comes to foreground)
    LaunchedEffect(isWindowFocused) {
        if (isWindowFocused && !previouslyFocused) {
            handleClipboardChange(clipboardManager)
        }
        previouslyFocused = isWindowFocused
    }

    DisposableEffect(Unit) {
        val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            handleClipboardChange(clipboardManager)
        }

        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        onDispose {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        }
    }
}

private fun handleClipboardChange(clipboardManager: ClipboardManager) {
    val clip = clipboardManager.primaryClip ?: return
    if (clip.itemCount == 0) return

    val item = clip.getItemAt(0)
    val text = item.text?.toString() ?: return
    val htmlText = item.htmlText

    // Plain text with bullets/checkboxes but no HTML -> add HTML for pasting into other apps
    if (htmlText == null && containsFormattedContent(text)) {
        val html = convertToHtml(text)
        val newClip = ClipData.newHtmlText(clip.description.label, text, html)
        clipboardManager.setPrimaryClip(newClip)
    }
}

/**
 * Checks if the clipboard contains HTML list items and returns the bulleted text version.
 * Returns null if clipboard doesn't have HTML lists or is empty.
 */
fun getClipboardAsBulletedText(context: Context): String? {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboardManager.primaryClip ?: return null
    if (clip.itemCount == 0) return null

    val item = clip.getItemAt(0)
    val text = item.text?.toString() ?: return null
    val htmlText = item.htmlText ?: return null

    // Only convert if HTML has list items and plain text doesn't already have bullets
    if (!containsFormattedContent(text) && containsHtmlList(htmlText)) {
        val bulletedText = convertHtmlToBulletedText(htmlText)
        if (containsFormattedContent(bulletedText)) {
            return bulletedText
        }
    }
    return null
}

private fun containsHtmlList(html: String): Boolean {
    return html.contains("<li", ignoreCase = true)
}

/**
 * Converts HTML with list items to plain text with bullet prefixes.
 * Handles nested lists by tracking indentation level.
 */
private fun convertHtmlToBulletedText(html: String): String {
    val result = StringBuilder()
    var foundListItems = false

    // Process the HTML character by character to track nesting
    var pos = 0
    var indentLevel = 0
    val tagPattern = Regex("<(/?)([a-zA-Z]+)[^>]*>", RegexOption.IGNORE_CASE)

    while (pos < html.length) {
        val tagMatch = tagPattern.find(html, pos)

        if (tagMatch == null || tagMatch.range.first > pos) {
            // Skip text outside of tags
            pos = tagMatch?.range?.first ?: html.length
            continue
        }

        val isClosing = tagMatch.groupValues[1] == "/"
        val tagName = tagMatch.groupValues[2].lowercase()

        when (tagName) {
            "ul", "ol" -> {
                if (isClosing) {
                    indentLevel = maxOf(0, indentLevel - 1)
                } else {
                    indentLevel++
                }
            }
            "li" -> {
                if (!isClosing) {
                    foundListItems = true
                    // Find the content of this li element
                    val liStart = tagMatch.range.last + 1
                    val liEndPattern = Regex("</li>", RegexOption.IGNORE_CASE)
                    val liEndMatch = liEndPattern.find(html, liStart)

                    if (liEndMatch != null) {
                        var content = html.substring(liStart, liEndMatch.range.first)
                        // Remove nested ul/ol tags and their contents for this level
                        content = content.replace(Regex("<ul[^>]*>.*?</ul>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                        content = content.replace(Regex("<ol[^>]*>.*?</ol>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                        // Remove remaining HTML tags
                        content = content.replace(Regex("<[^>]+>"), "")
                        // Decode HTML entities
                        content = content
                            .replace("&nbsp;", " ")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&quot;", "\"")
                            .trim()

                        if (content.isNotEmpty()) {
                            // Add tabs for indentation (indentLevel - 1 because we're inside the ul)
                            val tabs = "\t".repeat(maxOf(0, indentLevel - 1))
                            result.append(tabs)
                            result.append(BULLET_PREFIX)
                            result.append(content)
                            result.append("\n")
                        }
                    }
                }
            }
        }

        pos = tagMatch.range.last + 1
    }

    if (!foundListItems) {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
    }

    return result.toString().trimEnd()
}

private fun containsFormattedContent(text: String): Boolean {
    return text.lines().any { line ->
        val trimmedLine = line.trimStart('\t')
        trimmedLine.startsWith(BULLET_PREFIX) ||
        trimmedLine.startsWith(CHECKBOX_UNCHECKED_PREFIX) ||
        trimmedLine.startsWith(CHECKBOX_CHECKED_PREFIX)
    }
}

/**
 * Converts text with bullets and checkboxes to HTML.
 * Bullets (•) and checkboxes (☐/☑) become list items.
 * Indented items (with leading tabs) become nested sublists.
 */
fun convertToHtml(text: String): String {
    val lines = text.lines()
    val body = StringBuilder()
    var currentIndentLevel = -1 // -1 means not in a list

    for (line in lines) {
        // Count leading tabs for indentation level
        val leadingTabs = line.takeWhile { it == '\t' }
        val indentLevel = leadingTabs.length
        val lineContent = line.removePrefix(leadingTabs)

        val isBullet = lineContent.startsWith(BULLET_PREFIX)
        val isCheckbox = lineContent.startsWith(CHECKBOX_UNCHECKED_PREFIX) || lineContent.startsWith(CHECKBOX_CHECKED_PREFIX)

        if (isBullet || isCheckbox) {
            // Adjust nesting level
            while (currentIndentLevel < indentLevel) {
                body.append("<ul>")
                currentIndentLevel++
            }
            while (currentIndentLevel > indentLevel) {
                body.append("</ul>")
                currentIndentLevel--
            }

            val prefix = when {
                lineContent.startsWith(BULLET_PREFIX) -> BULLET_PREFIX
                lineContent.startsWith(CHECKBOX_CHECKED_PREFIX) -> CHECKBOX_CHECKED_PREFIX
                else -> CHECKBOX_UNCHECKED_PREFIX
            }
            val content = escapeHtml(lineContent.removePrefix(prefix))
            body.append("<li>$content</li>")
        } else {
            // Close all open lists
            while (currentIndentLevel >= 0) {
                body.append("</ul>")
                currentIndentLevel--
            }

            if (line.isEmpty()) {
                body.append("<br>")
            } else {
                body.append("<p>${escapeHtml(line)}</p>")
            }
        }
    }

    // Close any remaining open lists
    while (currentIndentLevel >= 0) {
        body.append("</ul>")
        currentIndentLevel--
    }

    return body.toString()
}

private fun escapeHtml(text: String): String {
    return Html.escapeHtml(text)
}
