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
 */
private fun convertHtmlToBulletedText(html: String): String {
    val result = StringBuilder()
    val liPattern = Regex("<li[^>]*>(.*?)</li>", RegexOption.IGNORE_CASE)
    var foundListItems = false

    for (match in liPattern.findAll(html)) {
        foundListItems = true
        val content = match.groupValues[1]
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()

        if (content.isNotEmpty()) {
            result.append(BULLET_PREFIX)
            result.append(content)
            result.append("\n")
        }
    }

    if (!foundListItems) {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
    }

    return result.toString().trimEnd()
}

private fun containsFormattedContent(text: String): Boolean {
    return text.lines().any { line ->
        line.startsWith(BULLET_PREFIX) ||
        line.startsWith(CHECKBOX_UNCHECKED_PREFIX) ||
        line.startsWith(CHECKBOX_CHECKED_PREFIX)
    }
}

/**
 * Converts text with bullets and checkboxes to HTML.
 * Bullets (•) and checkboxes (☐/☑) become list items.
 */
fun convertToHtml(text: String): String {
    val lines = text.lines()
    val body = StringBuilder()
    var inList = false

    for (line in lines) {
        when {
            line.startsWith(BULLET_PREFIX) -> {
                if (!inList) {
                    body.append("<ul>")
                    inList = true
                }
                val content = escapeHtml(line.removePrefix(BULLET_PREFIX))
                body.append("<li>$content</li>")
            }
            line.startsWith(CHECKBOX_UNCHECKED_PREFIX) || line.startsWith(CHECKBOX_CHECKED_PREFIX) -> {
                if (!inList) {
                    body.append("<ul>")
                    inList = true
                }
                val prefix = if (line.startsWith(CHECKBOX_CHECKED_PREFIX)) CHECKBOX_CHECKED_PREFIX else CHECKBOX_UNCHECKED_PREFIX
                val content = escapeHtml(line.removePrefix(prefix))
                body.append("<li>$content</li>")
            }
            else -> {
                if (inList) {
                    body.append("</ul>")
                    inList = false
                }

                if (line.isEmpty()) {
                    body.append("<br>")
                } else {
                    body.append("<p>${escapeHtml(line)}</p>")
                }
            }
        }
    }

    if (inList) {
        body.append("</ul>")
    }

    return body.toString()
}

private fun escapeHtml(text: String): String {
    return Html.escapeHtml(text)
}
